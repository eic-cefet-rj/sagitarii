package cmabreu.sagitarii.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cmabreu.sagitarii.core.delivery.DeliveryUnit;
import cmabreu.sagitarii.core.delivery.InstanceDeliveryControl;
import cmabreu.sagitarii.core.mail.MailService;
import cmabreu.sagitarii.core.processor.Activation;
import cmabreu.sagitarii.core.sockets.FileImporter;
import cmabreu.sagitarii.core.sockets.FileReceiverManager;
import cmabreu.sagitarii.core.sockets.ReceivedFile;
import cmabreu.sagitarii.core.types.ExperimentStatus;
import cmabreu.sagitarii.core.types.FragmentStatus;
import cmabreu.sagitarii.persistence.entity.Experiment;
import cmabreu.sagitarii.persistence.entity.Fragment;
import cmabreu.sagitarii.persistence.entity.Pipeline;
import cmabreu.sagitarii.persistence.exceptions.NotFoundException;
import cmabreu.sagitarii.persistence.services.ExperimentService;
import cmabreu.sagitarii.persistence.services.FragmentService;
import cmabreu.sagitarii.persistence.services.PipelineService;


public class Sagitarii {
	private Logger logger = LogManager.getLogger( this.getClass().getName() );
	private static Sagitarii sagitarii;
	private List<Experiment> runningExperiments;
	private Queue<Pipeline> pipelineInputBuffer;
	private Queue<Pipeline> pipelineJoinInputBuffer;
	private Queue<Pipeline> pipelineOutputBuffer;
	private int maxInputBufferCapacity;
	private int spinPointer = 0;
	private int spinPointerJoin = 0;
	private Experiment experimentOnTable;
	private Experiment experimentOnTableJoin;
	private boolean stopped = false;
	private boolean canLoadJoinBuffer = true;
	
	public void removeExperiment( Experiment exp ) {
		for ( Experiment experiment : runningExperiments ) {
			if ( experiment.getTagExec().equals( exp.getTagExec() ) ) {
				logger.debug("removing experiment " + exp.getTagExec() + " from execution queue.");
				runningExperiments.remove( experiment );
				electExperiment();
				electExperimentJoin();
				break;
			}
		}
	}
	
	/** 
	 * A cada ciclo, elege um experimento para ter seus pipelines processados.
	 * No momento estou usando uma roleta simples, onde todos os experimentos
	 * possuem igual preferência. A cada ciclo de verificação, o ponteiro (spinPointer) 
	 * é incrementado até atingir o fim da fila, quando retorna para o primeiro da fila.
	 * 
	 * Ver também "electExperimentJoin()"
	 * 
	 */
	private synchronized void electExperiment() {
		Experiment exp = null;
		if (runningExperiments.size() > 0 ) {
			exp = runningExperiments.get( spinPointer );
			logger.debug("experiment " + exp.getTagExec() + " elected for common buffer");
			spinPointer++;
			if ( spinPointer > (runningExperiments.size() -1 ) ) {
				spinPointer = 0;
			}
		} else {
			logger.debug("no running experiments for common buffer");
		}
		experimentOnTable = exp;
	}

	/**
	 * O mesmo que electExperiment(), mas
	 * trata de pipelines que serão processados pelo MainCluster
	 * ou seja, atividades que envolvam SQL (JOIN).
	 * Dessa forma, posso ter pipelines de dois experimentos
	 * sendo lidos para o buffer simultaneamente, um com
	 * pipelines de SQL e outro com pipelines SQL (MainCluster) 
	 * 
	 */
	private synchronized void electExperimentJoin() {
		Experiment exp = null;
		if (runningExperiments.size() > 0 ) {
			exp = runningExperiments.get( spinPointerJoin );
			logger.debug("experiment " + exp.getTagExec() + " elected for JOIN buffer");
			spinPointerJoin++;
			if ( spinPointerJoin > (runningExperiments.size() -1 ) ) {
				spinPointerJoin = 0;
			}
		} else {
			logger.debug("no running experiments for JOIN buffer");
		}
		experimentOnTableJoin = exp;
	}
	
	/**
	 * Dado um experimento, verifica se existe ainda algum pipeline pertencente a um de seus
	 * fragmentos nos buffers de entrada ou de saida.
	 * 
	 * @param exp Um experimento
	 * @return boolean (está ou não em um buffer)
	 */
	public boolean experimentIsStillQueued( Experiment exp ) {
		for( Fragment frag : exp.getFragments() ) {

			for( Pipeline pipe : pipelineOutputBuffer  ) {
				if( pipe.getIdFragment() == frag.getIdFragment() ) {
					return true;
				}
			}
			
			for( Pipeline pipe : pipelineInputBuffer  ) {
				if( pipe.getIdFragment() == frag.getIdFragment() ) {
					return true;
				}
			}
			for( Pipeline pipe : pipelineJoinInputBuffer  ) {
				if( pipe.getIdFragment() == frag.getIdFragment() ) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Marca um pipeline como encerrado ( já foi entregue a um nó e este já o 
	 * processou e já entregou os dados produzidos de todas as tarefas)
	 *  
	 * @param pipeline um pipeline
	 */
	public synchronized void finishPipeline( Pipeline pipeline ) {
		logger.debug("instance " + pipeline.getSerial() + " is finished");
		try {

			// Set as finished (database)

			PipelineService pipelineService = new PipelineService();
			pipelineService.finishPipeline( pipeline );
			
			// Remove from output buffer if any
			for ( Pipeline pipe : pipelineOutputBuffer ) {
				if ( pipe.getSerial().equals( pipeline.getSerial() ) ) {
					pipelineOutputBuffer.remove( pipe );
					break;
				}
			}
			
		} catch ( Exception e ) {
			logger.error( e.getMessage() );
		}
	}
	
	/**
	 * Percorre a lista de experimentos em execução e para cada experimento verifica se seus fragmentos já 
	 * foram todos executados ( todas as instancias do fragmento encerraram).
	 * 
	 * Em caso positivo, verifica se o próximo fragmento na ordem de execução pode ser
	 * executado (atividades iniciais já estão desbloqueadas). Se puder, gera os pipelines
	 * do próximo fragmento.
	 * 
	 * Se não há mais fragmentos no experimento ou os que restam ainda estiverem bloqueados
	 * (nesse caso, houve algum erro na execução dos fragmentos anteriores, pois se os desbloqueados já terminaram 
	 * então todos os dados dos próximos já deveriam estar presentes) então encerra o experimento.
	 * 
	 * As condições para um fragmento ser executado são: a) Todos os fragmentos com ordem de execução menor que ele precisam
	 * ter terminado; b) Todas as tabelas de entrada da atividade de entrada do fragmento precisam possuir dados.
	 * 
	 * experimentIsStillQueued é verdadeiro quando não há mais pipelines do fragmento enfileirados no banco, porém
	 * ainda existem alguns no buffer da saída aguardando serem processados pelos nós.
	 * 
	 * Um fragmento é gerado com status READY. Quando for possível gerar pipelines para o fragmento, ele se torna 
	 * PIPELINED (os pipelines foram gerados mas nenhum foi pego ainda para o buffer). Quando os pipelines deste 
	 * fragmento forem pegos do banco para o buffer de saída, então o fragmento está RUNNING. Então, quando não houverem
	 * mais pipelines deste fragmento NO BANCO, então ele estará FINISHED. 
	 * 
	 * Mais detalhes em updateFragments()
	 * 
	 */
	private synchronized void checkFinished() {
		for ( Experiment exp : runningExperiments ) {
			boolean allFinished = true;
			boolean haveReady = false;
			for ( Fragment frag : exp.getFragments() ) {
				// allFinished é verdadeiro quando não há mais fragmentos RUNNING (somente READY e FINISHED).
				// Os READY vão se tornar PIPELINED quando forem gerados novos pipelines. E se tornarão RUNNING
				// quando esta rotina for executada novamente.
				if ( frag.getStatus() != FragmentStatus.READY ) {
					allFinished = ( allFinished && ( frag.getStatus() == FragmentStatus.FINISHED ) );
				} else {
					haveReady = true;
					logger.debug("experiment " + exp.getTagExec() + " fragment " + frag.getSerial() +" : " + frag.getStatus() );
				}
			}
			
			// Se todos os fragmentos que estavam RUNNING já estiverem terminado e não houverem mais
			// pipelines a serem processados, então é hora de gerar os pipelines dos fragmentos 
			// que dependiam destes e iniciar sua execução. 
			// Condição: Não existem pipelines de nenhum fragmento deste 
			// experimento no banco e nem nos buffers. *** PERIGOSO QUANDO UM PIPELINE FOI PERDIDO PELO NÓ ***
			// pois se não houver uma recuperação eficiente ele ficará tempo demais no buffer de saída, impedindo
			// o prosseguimento da execução do experimento.
			// Deve haver ao menos um fragmento READY ( provavelmente é um BLOQUEANTE que 
			// dependia dos que estavam rodando ).
			if ( allFinished && haveReady && !experimentIsStillQueued( exp ) ) {
				logger.debug("generating pipelines for next fragment in experiment " + exp.getTagExec() );
				try {
					FragmentPipeliner fp = new FragmentPipeliner( exp );
					fp.generate();
					int pips = fp.getPipelines().size();
					if ( pips == 0) {
						logger.error("experiment " + exp.getTagExec() + " generate empty pipeline list" );
					} else {
						canLoadJoinBuffer = true;
					}
				} catch (Exception e) {
					logger.error("cannot generate pipelines for experiment " + exp.getTagExec() + ": " + e.getMessage() );
					haveReady = false;
				}
				logger.debug("done generating pipelines (" + exp.getTagExec() + ")");
			}

			// Se todos os pipelines deste experimento foram concluídos ( allFinished ) e 
			// não há nenhum por processar ( not haveReady ) e 
			// nenhum pipeline está no buffer ( not experimentIsStillQueued ) então o experimento 
			// está totalmente concluído!! 
			if ( allFinished && !haveReady && !experimentIsStillQueued( exp ) ) {
				
				// Verify if we still receiving data from Teapot for this experiment
				try {
					if ( !FileReceiverManager.getInstance().workingOnExperiment( exp.getTagExec() ) ) { 
						exp.setStatus( ExperimentStatus.FINISHED );
						exp.setFinishDateTime( Calendar.getInstance().getTime() );
						
							ExperimentService experimentService = new ExperimentService();
							experimentService.updateExperiment(exp);
							runningExperiments.remove( exp );
							logger.debug("experiment " + exp.getTagExec() + " is finished.");
							
							MailService ms = new MailService();
							ms.notifyExperimentFinished( exp );
							return;
					} else {
						logger.debug("cannot finish experiment " + exp.getTagExec() + ": found open sessions receiving files");
					}
					
					
					
				} catch ( Exception e ) {
					logger.error("cannot check experiment status: " + e.getMessage() );
					e.printStackTrace();
				}
				
			}
			
		}
	}
	
	
	/**
	 * Verifica se existe algum fragmento que já tenha processado
	 * todas as suas instancias.
	 * Caso encontre, tenta gerar novas instancias.
	 * Caso não gere mais nenhum instancia, marca o experimento como 
	 * encerrado ( Status = FINISHED ). 
	 */
	private synchronized void updateFragments() throws Exception {
		logger.debug("updating fragments...");
		for ( Experiment exp : runningExperiments ) {
			for ( Fragment frag : exp.getFragments() ) {
				
				FragmentStatus oldStatus = frag.getStatus();
				
				int count = frag.getRemainingPipelines();

				logger.debug("Frag: " + frag.getSerial() + " Status: " + frag.getStatus() + " Instances: " + count );
				
				// Case 1 --------------------------------------------------------------------
				if ( (frag.getStatus() == FragmentStatus.PIPELINED) && (count > 0) ) {
					logger.debug(" > " + count + " instances found. Changing fragment " + frag.getSerial() + " status from PIPELINED to RUNNING");
					frag.setStatus( FragmentStatus.RUNNING );
					canLoadJoinBuffer = true;
				}

				// Case 2 --------------------------------------------------------------------
				if ( frag.getStatus() == FragmentStatus.RUNNING ) {
					logger.debug(" > updating pipeline count");
					
					// TODO: Check buffers BEFORE check databbase.
					// If we still have instances in buffers, no need to hit database, right?
					
					try {
						PipelineService pipelineService = new PipelineService(); 
						count = pipelineService.getPipelinedList( frag.getIdFragment() ).size();
						logger.debug(" > found " + count + " instances in database");
					} catch ( NotFoundException e) {
						logger.debug(" > this fragment have no instances in database");
						count = 0;
					} 
					
					frag.setRemainingPipelines( count );
					if ( count == 0 ) {	
						logger.debug(" > no instances found: can I set fragment status to finished?");

						// TODO: Check this BEFORE hit database... In memory check is less costly...
						logger.debug("Instances in Delivery Control:");
						for( DeliveryUnit du : InstanceDeliveryControl.getInstance().getUnits()  ) {
							logger.debug( " > Instance: " + du.getPipeline().getSerial() + " FragID: " + du.getPipeline().getIdFragment() );
							logger.debug(" > Tasks: ");
							for( Activation act : du.getActivations() ) {
								logger.debug("    > " + act.getActivitySerial()	);
							}
						}

						logger.debug("Instances in Output buffer:");
						for ( Pipeline pip : pipelineOutputBuffer ) {
							logger.debug(" > Instance: " + pip.getSerial() + " FragID: " + pip.getIdFragment() );
						}
						
						
						logger.debug("current importers");
						for( FileImporter importer :  FileReceiverManager.getInstance().getImporters() ) {
							try {
								logger.debug(" > " + importer.getName() );
								for( ReceivedFile file : importer.getReceivedFiles() ) {
									logger.debug("     > " + file.getActivity() + " " + file.getFileName() );
								}
							} catch ( Exception e ) {}
						}
						
						
						if ( experimentIsStillQueued( frag.getExperiment() )  ) {
							logger.debug(" > WAIT! this fragment still have instances queued!");
						} else {
							logger.debug(" > Yeap! setting fragment " + frag.getSerial() + " to finished.");
							frag.setStatus( FragmentStatus.FINISHED );
							canLoadJoinBuffer = true;
						}
						// ===============================================================================
					}
					
					if ( oldStatus != frag.getStatus() ) {
						logger.debug(" > fragment " + frag.getSerial() + " status is now '" + frag.getStatus()  + "' in database");
						FragmentService fragmentService = new FragmentService();
						fragmentService.updateFragment(frag);
						fragmentService = null;
					} else {
						logger.debug(" > fragment " + frag.getSerial() + " status still as '" + frag.getStatus()+ "'" );
					}
					
				}
				
			}
		}
		logger.debug("done updating fragments.");
	}
	
	
	/**
	 * Entrega um pipeline a um cluster
	 * 
	 * @return Pipeline
	 */
	public synchronized Pipeline getNextPipeline() {
		Pipeline next = pipelineInputBuffer.poll();
		if ( next != null ) {
			pipelineOutputBuffer.add(next);
		}
		return next;
	}

	
	/**
	 * Retorna um pipeline que estava na fila de processamento mas foi 
	 * perdido (foi entregue para um nó mas não retornou dentro do tempo esperado)
	 * 
	 * @param pipeline
	 */
	public synchronized void returnToBuffer( Pipeline pipeline ) {
		if ( pipelineOutputBuffer.remove( pipeline ) ) {
			if ( pipeline.getType().isJoin() ) {
				pipelineJoinInputBuffer.add( pipeline );
			} else {
				pipelineInputBuffer.add( pipeline );
			}
		}
	}
	
	
	/**
	 * Entrega um pipeline JOIN (SQL) ao MainCluster
	 * 
	 * @return Pipeline
	 */
	public synchronized Pipeline getNextJoinPipeline() {
		Pipeline next = pipelineJoinInputBuffer.poll();
		if ( next != null ) {
			logger.debug("serving join pipeline " + next.getSerial() );
			pipelineOutputBuffer.add(next);
		}
		return next;
	}

	/**
	 * Dada uma lista de pipelines, separa os que serão entregues ao cluster e os que serão
	 * processados no servidor ( MainCluster ) e adiciona nas listas apropriadas.
	 * 
	 * @param pipes
	 */
	private synchronized void processAndInclude( List<Pipeline> pipes ) {
		for( Pipeline pipe : pipes ) {
			if( pipe.getType().isJoin() ) {
				// Os SELECT vão para o MainCluster
				pipelineJoinInputBuffer.add(pipe);
			} else {
				// Os demais vão para os nós.
				pipelineInputBuffer.add(pipe);
			}
		}
	}

	
	/**
	 * Recarrega os pipelines que ficaram nomo RUNNING no banco após
	 * o servidor ser reiniciado.
	 * Este método é chamado pelo Orchestrator após recuperar os experimentos 
	 * interrompidos.
	 *  
	 */
	public synchronized void reloadAfterCrash() {
		logger.debug("after crash reloading " + runningExperiments.size() + " experiments.");
		try {
			try {
				PipelineService pipelineService = new PipelineService();
				processAndInclude( pipelineService.recoverFromCrash() );
				logger.debug( pipelineInputBuffer.size() + " common pipelines recovered.");
				logger.debug( pipelineJoinInputBuffer.size() + " JOIN pipelines recovered.");
			} catch ( NotFoundException e ) {
				logger.debug("no pipelines to recover");
			}
			
		} catch ( Exception e) {
			logger.error( e.getMessage() );
		} 
		logger.debug("after crash reload done.");
	}
	
	/**
	 * Dado um experimento, retorna o fragmento que está sendo executado no momento.
	 * 
	 * @param experiment
	 * @return running fragment
	 */
	private synchronized Fragment getRunningFragment( Experiment experiment ) {
		for ( Fragment frag : experiment.getFragments() ) {
			if ( frag.getStatus() == FragmentStatus.RUNNING ) {
				return frag;
			}
		}
		return null;
	}
	
	/**
	 * Carrega do banco de dados uma lista com os primeiros N pipelines disponíveis
	 * que possam ser processados por clusters.
	 * 
	 * *** ESTE MÉTODO PRECISA SER APRIMORADO PARA SER MAIS EFICIENTE ***
	 * 
	 */
	private synchronized void loadCommonBuffer() {
		
		logger.debug("loading common buffer...");

		if( pipelineInputBuffer.size() < maxInputBufferCapacity ) {
			int diff = maxInputBufferCapacity - pipelineInputBuffer.size();
			try {
				Fragment running = getRunningFragment( experimentOnTable );
				if ( running == null ) {
					logger.debug("no fragments running");
					return;
				}

				logger.debug("running fragment found: " + running.getSerial() );
				PipelineService ps = new PipelineService();
				List<Pipeline> pipes = ps.getHead( diff, running.getIdFragment() );
				processAndInclude( pipes );
				
			} catch (NotFoundException e) {
				logger.debug("no running pipelines found for experiment " + experimentOnTable.getTagExec() );
			} catch ( Exception e) {
				logger.error( e.getMessage() );
			} 
			if ( pipelineInputBuffer.size() > 0 ) {
				logger.debug("common buffer size: " + pipelineInputBuffer.size() );
			}
		}
	}
	

	/**
	 * Carrega do banco de dados uma lista com os primeiros N pipelines disponíveis
	 * que possam ser processados pelo cluster local ( MainCluster )
	 * 
	 * *** ESTE MÉTODO PRECISA SER APRIMORADO PARA SER MAIS EFICIENTE ***
	 */
	private synchronized void loadJoinBuffer() {
		logger.debug("loading SELEC buffer");
		if( pipelineJoinInputBuffer.size() < maxInputBufferCapacity ) {
			int diff = maxInputBufferCapacity - pipelineJoinInputBuffer.size();
			try {
				PipelineService ps = new PipelineService();
				Fragment running = getRunningFragment( experimentOnTableJoin );
				if ( running == null ) {
					logger.debug("no SELECT fragments running");
					return;
				}
				logger.debug("running SELECT fragment found: " + running.getSerial() );
				processAndInclude( ps.getHeadJoin( diff, running.getIdFragment() ) );
			} catch (NotFoundException e) {
				logger.debug("no running SELECT instances found for experiment " + experimentOnTableJoin.getTagExec() );
			} catch (Exception e) {
				//logger.error( e.getMessage() );
			} 
			if ( pipelineJoinInputBuffer.size() > 0 ) {
				logger.debug("SELECT buffer size: " + pipelineJoinInputBuffer.size() );
			}
		} 
	}

	
	/**
	 * Chamado pelo Orchestrator de tempos em tempos.
	 * é o coração do sistema.
	 * O arquivo config.xml possui uma tag poolIntervalSeconds
	 * que configura o tempo em segundos que este método é chamado.
	 * 
	 * Um tempo muito curto o sistema fica mais rápido, mas pode ocasionar
	 * mais lidas no banco de dados sem necessidade (para verificar se existem pipelines
	 * produzidos).
	 * 
	 * Um tempo muito longo faz com que menos acessos ao banco sejam feitos, mas
	 * o sistema pode demorar a encher o buffer de saída, fazendo com que os nós 
	 * fiquem desocupados.
	 * 
	 * Este método faz um acesso ao banco para carregar pipelines ao buffer quando este atinge
	 * menos de 1/3 de sua capacidade (comum) ou 1/5 da capacidade (SQL). No caso no buffer de JOIN, 
	 * este acesso é quase constante, pois é um tipo de pipeline escasso e de processamento rápido, 
	 * o que faz com que o buffer fique quase sempre vazio (por isso o limite de 1/5 e não de 1/3).
	 * 
	 * Este método também gira a roleta de experimentos, escalonando os experimentos em ordem numa
	 * fila para terem seus pipelines processados de forma homogênea.
	 * 
	 * Se necessitar aumentar a velocidade, coloque um buffer pequeno, assim ele esvazia rápido
	 * o suficiente para a próxima verificação justificar o acesso ao banco.
	 * Claro que o tamanho do buffer é influenciado pela quantidade de nós de processamento e sua
	 * velocidade de trabalho. Um buffer muito pequeno com muitos nós fará com que eles fiquem desocupados
	 * aguardando esta rotina recarregar o buffer, mas terá melhor distribuição entre os experimentos
	 * ( a roleta de experimentos irá girar mais depressa ). 
	 * 
	 * Um buffer grande com poucos nós fará com que ele fique sempre cheio. Esta rotina fará acessos
	 * injustificados ao banco e fará a roleta de experimentos girar devagar, carregando uma quantidade muito
	 * grande de pipelines do mesmo experimento no buffer e causando inanição nos demais.
	 * 
	 * Procure um balanço ideal entre "poolIntervalSeconds", "maxInputBufferCapacity" e a quantidade
	 * de nós de processamento em sua rede.
	 * 
	 */
	public synchronized void loadInputBuffer() {
		
		if ( stopped || ( runningExperiments.size() == 0 ) ) {
			return;
		}
		
		if ( ClustersManager.getInstance().hasClusters() ) {
			logger.debug("COMMON buffer...");
			// Se o buffer comum está com 1/3 de sua capacidade, é hora de ler mais pipelines do banco
			if ( pipelineInputBuffer.size() < ( maxInputBufferCapacity / 3 ) ) {
				// ... mas antes rodamos a roleta de experimentos para processar o próximo da lista (comum)
				int mark = 0;
				do {
					logger.debug("electing experiment");
					electExperiment();
					mark++;
					// Não podemos colocar na mesa experimentos pausados....
					// Também não podemos repetir o laço indefinidamente, pois travaria aqui se todos
					// estivessem pausados. Usamos o contador "mark" para desistir de repetir quando verificarmos todos
					// e não encontrarmos nenhum rodando.
				} while ( experimentOnTable != null && (experimentOnTable.getStatus() == ExperimentStatus.PAUSED) && (mark <= runningExperiments.size() ) ) ;
				if ( (experimentOnTable != null) && (experimentOnTable.getStatus() != ExperimentStatus.PAUSED) ) {
					loadCommonBuffer();
				}
			}
		
			if ( canLoadJoinBuffer ) {
				logger.debug("JOIN buffer...");
				// Se o buffer JOIN (SQL) está com 1/5 de sua capacidade, é hora de ler mais pipelines do banco
				if ( pipelineJoinInputBuffer.size() < ( maxInputBufferCapacity / 5 ) ) {
					// ... mas antes rodamos a roleta de experimentos para processar o próximo da lista (JOIN)
					int mark = 0;
					do {
						electExperimentJoin();
						mark++;
						// Não podemos colocar na mesa experimentos pausados....
						// Também não podemos repetir o laço indefinidamente, pois travaria aqui se todos
						// estivessem pausados. Usamos o contador "mark" para desistir de repetir quando verificarmos todos
						// e não encontrarmos nenhum rodando.
					} while ( experimentOnTableJoin != null &&  (experimentOnTableJoin.getStatus() == ExperimentStatus.PAUSED) && (mark <= runningExperiments.size() ) ) ;
					
					if ( (experimentOnTableJoin != null) && (experimentOnTableJoin.getStatus() != ExperimentStatus.PAUSED) ) {
						loadJoinBuffer();
					} 
					
				}
				if ( pipelineJoinInputBuffer.size() == 0 ) {
					canLoadJoinBuffer = false;
				}
			} else {
				logger.debug("nothing has changed since last JOIN check so will not touch database now.");
			}
		
			try {
				updateFragments();
			} catch (Exception e) {
				logger.error( "update fragments error: " + e.getMessage() );
			}
		
			if( ( pipelineJoinInputBuffer.size() == 0 ) && ( pipelineInputBuffer.size() == 0 ) ) {
				checkFinished();
			}

		} else {
			// logger.debug("will not work until have nodes to process.");
		}
			
			
	}
	
	public boolean isRunning() {
		return ( runningExperiments.size() > 0 );
	}
	
	public synchronized static Sagitarii getInstance() {
		if ( sagitarii == null ) {
			sagitarii = new Sagitarii();
		}
		return sagitarii;
	}

	
	public void stopProcessing() {
		stopped = true;
	}

	public void resumeProcessing() {
		stopped = false;
	}

	public boolean isStopped() {
		return stopped;
	}
	
	private Sagitarii() {
		
		runningExperiments = new ArrayList<Experiment>();
		pipelineInputBuffer = new LinkedList<Pipeline>();
		pipelineJoinInputBuffer = new LinkedList<Pipeline>();
		pipelineOutputBuffer = new LinkedList<Pipeline>();
	}
	
	/**
	 * Pausa um experimento.
	 * Não retirar da lista de experimentos em execução, pois só assim ele poderá ser 
	 * colocado em execução novamente. Ver "resume()".
	 * 
	 * @param idExperiment
	 * @throws Exception
	 */
	public void pause( int idExperiment ) throws Exception {
		ExperimentService experimentService = new ExperimentService();
		for( Experiment exp : runningExperiments ) {
			if ( (exp.getIdExperiment() == idExperiment) && ( exp.getStatus() == ExperimentStatus.RUNNING ) ) {
				exp.setStatus( ExperimentStatus.PAUSED );
				experimentService.updateExperiment(exp);
				return;
			}
		}
	}

	/**
	 * Coloca um experimento pausado para executar novamente.
	 * Não busca no banco, e sim na lista se experimentos em execução
	 * para poupar acesso ao banco de dados. Somente o seu status é altarado no banco.
	 * 
	 * @param idExperiment
	 * @throws Exception
	 */
	public void resume( int idExperiment ) throws Exception {
		logger.debug("resuming experiment " + idExperiment);
		ExperimentService experimentService = new ExperimentService();
		for( Experiment exp : runningExperiments ) {
			if ( (exp.getIdExperiment() == idExperiment) && ( exp.getStatus() == ExperimentStatus.PAUSED ) ) {
				logger.debug("found "+ exp.getTagExec() + ". resuming...");
				exp.setStatus( ExperimentStatus.RUNNING );
				experimentService.updateExperiment(exp);
				logger.debug("done");
				return;
			}
		}
		logger.error("cannot resume. Experiment " + idExperiment + " not found on buffer.");
	}
	
	public synchronized List<Experiment> getRunningExperiments() {
		// Para evitar acesso concorrente, fornecer imutável.
		return new ArrayList<Experiment>( runningExperiments );
	}

	public void setRunningExperiments(List<Experiment> runningExperiments) {
		this.runningExperiments = runningExperiments;
		electExperiment();
		electExperimentJoin();
	}
	
	public synchronized void addRunningExperiment( Experiment experiment ) throws Exception {
		boolean found = false;
		for ( Experiment exp : runningExperiments ) {
			if ( exp.getTagExec().equalsIgnoreCase( experiment.getTagExec() ) ) {
				found = true;
			}
		}
		if ( !found && ( experiment.getStatus() == ExperimentStatus.RUNNING ) ) {
			runningExperiments.add( experiment );
			canLoadJoinBuffer = true;
			electExperiment();
			electExperimentJoin();
			updateFragments();
		}
	}

	public Queue<Pipeline> getPipelineInputBuffer() {
		return new LinkedList<Pipeline>( pipelineInputBuffer );
	}

	public Queue<Pipeline> getPipelineOutputBuffer() {
		// To avoid concurrency on frontend showing 
		return new LinkedList<Pipeline>( pipelineOutputBuffer );
	}

	public void setMaxInputBufferCapacity(int maxInputBufferCapacity) {
		this.maxInputBufferCapacity = maxInputBufferCapacity;
	}
	
	public int getMaxInputBufferCapacity() {
		return maxInputBufferCapacity;
	}

	public Queue<Pipeline> getPipelineJoinInputBuffer() {
		return new LinkedList<Pipeline>( pipelineJoinInputBuffer );
	}
	
	public Experiment getExperimentOnTable() {
		return experimentOnTable;
	}

	public Experiment getExperimentOnTableJoin() {
		return experimentOnTableJoin;
	}

}
