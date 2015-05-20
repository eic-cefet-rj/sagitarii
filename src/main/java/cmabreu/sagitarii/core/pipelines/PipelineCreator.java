package cmabreu.sagitarii.core.pipelines;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cmabreu.sagitarii.core.DomainStorage;
import cmabreu.sagitarii.core.UserTableEntity;
import cmabreu.sagitarii.persistence.entity.ActivationExecutor;
import cmabreu.sagitarii.persistence.entity.Activity;
import cmabreu.sagitarii.persistence.entity.Domain;
import cmabreu.sagitarii.persistence.entity.File;
import cmabreu.sagitarii.persistence.entity.Fragment;
import cmabreu.sagitarii.persistence.entity.Pipeline;
import cmabreu.sagitarii.persistence.entity.Relation;
import cmabreu.sagitarii.persistence.exceptions.DatabaseConnectException;
import cmabreu.sagitarii.persistence.exceptions.NotFoundException;
import cmabreu.sagitarii.persistence.services.ExecutorService;
import cmabreu.sagitarii.persistence.services.FileService;

public class PipelineCreator {
	private int order;
	private Fragment fragment;
	private List<String> nativeAttributes = new ArrayList<String>();
	private Logger logger = LogManager.getLogger( this.getClass().getName() );
	StringBuilder fileXMLEntries = new StringBuilder();
	FileService fs;
	ExecutorService cs;
	
	public PipelineCreator() throws DatabaseConnectException {
		nativeAttributes.add("id_activity");
		nativeAttributes.add("index_id");
		nativeAttributes.add("id_experiment");
		nativeAttributes.add("id_pipeline");
		fs = new FileService();
		cs = new ExecutorService();
	}
	
	
	private boolean isNative( String attribute ) {
		return nativeAttributes.contains( attribute.toLowerCase() );
	}

	// Cria um pipeline para uma atividade (ou coloca v�rias atividades em um mesmo pipeline
	// para serem executadas em sequencia pelo mesmo n�, caso activity.getNextActivities()
	// possua atividades a serem executadas em sequencia.
	public Pipeline createPipeline( Activity activity, Fragment fragment, String parameter) throws Exception {
		this.fragment = fragment;
		order = 0;
		Pipeline pipe = new Pipeline();
		pipe.setIdFragment( fragment.getIdFragment() );
		pipe.setContent( getXMLContent(activity, parameter, pipe.getSerial() )  );
		pipe.setType( activity.getType() );
		pipe.setExecutorAlias( activity.getExecutorAlias() );
		pipe.setQtdActivations( order );
		return pipe;
	}
	
	// Gera um CSV no formato: 1a linha = colunas // 2a linha = dados
	public String generateInputData( UserTableEntity parameter ) {
		String retorno = "";
		String firstLine = "";
		String secondLine = "";
		for( String column : parameter.getColumnNames() ) {
			if ( !isNative( column ) ) {
				firstLine = firstLine + column + ",";
			}
		}
		for( String column : parameter.getColumnNames() ) {
			if ( !isNative( column ) ) {
				String value = parameter.getData( column );
				secondLine = secondLine + value + ",";
			}
		}
		if ( ( firstLine.length() > 0 ) && ( secondLine.length() > 0 ) ) {
			firstLine = firstLine.substring(0, firstLine.length() - 1);
			secondLine = secondLine.substring(0, secondLine.length() - 1);
			retorno = firstLine + "\n" + secondLine;
		}
		return retorno;
	}	

	// Ap�s generateInputData criar uma linha com os nomes das colunas CSV e uma linha com dados,
	// pode ser necess�rio adicionar mais linhas de dados.
	public String appendInputData( UserTableEntity parameter, String sourceInputData ) {
		String retorno = "";
		String secondLine = "";
		for( String column : parameter.getColumnNames() ) {
			if ( !isNative( column ) ) {
				String value = parameter.getData( column );
				secondLine = secondLine + value + ",";
			}
		}
		if ( ( sourceInputData.length() > 0 ) && ( secondLine.length() > 0 ) ) {
			secondLine = secondLine.substring(0, secondLine.length() - 1);
			retorno = sourceInputData + "\n" + secondLine;
		}
		return retorno;
	}	

	/**
	 * Para cada campo de cada tabela de entrada, verificar se � do tipo "File".
	 * Se for, inserir uma entrada "File" no pipeline para o arquivo especificado e
	 * trocar o �ndice pelo nome do arquivo no CSV de dados.
	 * 
	 * Exemplo:
	 * Trocar : 
	 * 		nome,idade,dados;
	 * 		Joao,34,67
	 * 		Maria,23,48
	 * Por:
	 * 		nome,idade,dados;
	 * 		Joao,34,data1.zip
	 * 		Maria,23,data3.zip
	 * 
	 * E criar no pipeline:
	 * 	<file domain='clientes.dados' name='data1.zip' index='67' />
	 * 	<file domain='clientes.dados' name='data3.zip' index='48' />
	 * 
	 */
	private String getFilesAndConvertData( Activity activity, String parameter ) throws Exception {
		if ( parameter == null ) {
			logger.debug("no parameter do convert data");
			return "";
		}
		// Resetar o buffer de entradas xml de arquivos.
		fileXMLEntries.setLength(0);
		boolean changed = false;
		
		String lines[] = parameter.split("\n");
		if ( lines.length == 0 ) {
			logger.debug("no data to convert");
			return ""; 
		}

		String columnList = lines[0];
		logger.debug("processing columns " + columnList );
		
		// Preparar para refazer o CSV de dados com os novos valores em "result"...
		StringBuilder result = new StringBuilder(); 
		result.append(columnList + "\n");
		
		String columns[] = columnList.split(",");
		int columnIndex = 0;
		// Para cada coluna
		for ( String column : columns ) {
			
			logger.debug("checking domain for column " + column + "...");
			if ( activity.getInputRelations().size() == 0 ) {
				logger.debug("no input relations found for activity " + activity.getSerial() );
			}
			
			for ( Relation inputRelation : activity.getInputRelations() ) {
				String sourceTable = inputRelation.getName();
				String domainName = sourceTable + "." + column;
				// ...pegar o domínio correspondente (se houver) 
				logger.debug("checking domain " + domainName + "..." );
				Domain domain = DomainStorage.getInstance().getDomain( domainName );
				if ( domain != null ) {
					changed = true;
					logger.debug( domainName + " is a File type field. scanning data lines..." );
					
					// A coluna número columnIndex é tipo "File". Em todas as linhas de dados do CSV,
					// trocar a coluna columnIndex pelo nome do arquivo correspondente ao
					// índice existente lá.
					// Preparar para refazer a linha com os valores CSV...
					// Para cada linha...
					for ( int lineNumber = 1; lineNumber < lines.length; lineNumber++ ) {
						String newLine = "";
						String prefix = "";
						// Desmembra cada item de dados da linha CSV.
						String dataValues[] = lines[lineNumber].split(",");
						// Pega o valor do �ndice do arquivo, que est� na coluna columnIndex
						int fileIndex = Integer.valueOf( dataValues[columnIndex] );
						logger.debug( "try to retrieve file of index number " + fileIndex + "..." );
						// Com o valor da coluna columnIndex 
						// tenta pegar o arquivo correspondente (este valor � o �ndice do arquivo).
						fs.newTransaction();
						File file = fs.getFile(fileIndex);
						logger.debug("found file " + file.getFileName() );
						// Troca o �ndice pelo nome na lista de dados da linha em quest�o.
						dataValues[columnIndex] = file.getFileName();
						
						fileXMLEntries.append("<file name='" + file.getFileName() + "' table='" + domain.getTable().getName() + "' attribute='" + column + 
								"' index='"+ file.getIdFile() +"' />");
						
						// Refazer a linha, mas com o valor do arquivo trocado pelo nome.
						for ( String dataValue : dataValues ) {
							newLine = newLine + prefix + dataValue;
							prefix = ",";
						}
						// Adicionar a nova linha na nova lista CSV...
						result.append( newLine +  "\n");
						
					}
				} else {
					logger.debug(domainName + " is not a file domain.");
				}
			}
			columnIndex++;
		}
		if ( !changed ) {
			// Não foi encontrado nenhum campo do tipo "File".
			// Retornar exatamente o que foi entregue.
			return parameter;
		} else {
			// Houve mudançaas nos dados. Retornar o que foi modificado.
			return result.toString();
		}
	}
	
	private String getActivityAsXML( Activity activity, String parameter ) throws Exception {
		StringBuilder sbu = new StringBuilder();
		String command = "";
		
		// Verifica se h� colunas do tipo "File" e modifica os dados (troca o ID pelo nome).
		parameter = getFilesAndConvertData( activity, parameter );

		cs.newTransaction();
		logger.debug("generating xml entry for activity " + activity.getSerial() + " " + activity.getTag() + " (" + activity.getType() + ")");
		logger.debug("will use executor " + activity.getCommand() );
		
		
		ActivationExecutor executor;
		try {
			executor = cs.getExecutor( activity.getCommand() );
		} catch ( NotFoundException e ) {
			throw new Exception( e.getMessage() );
		}
		
		
		if ( activity.getType().isJoin() ) {
			logger.debug("this activity will run on Main Cluster (SQL)");
			
			String sql = executor.getSelectStatement().replace("%ID_EXP%",  String.valueOf( this.fragment.getExperiment().getIdExperiment() ) );
			sql = sql.replace("%ID_WFL%", String.valueOf( this.fragment.getExperiment().getWorkflow().getIdWorkflow() ));
			sql = sql.replace("%ID_ACT%", String.valueOf( activity.getIdActivity() ) );
			
			command = sql;
			logger.debug(sql);
		} else {
			command = executor.getActivationWrapper();
			logger.debug("this activity will run " + activity.getCommand() + " node application ");
		}

		
		sbu.append( "<activity>" );
		sbu.append( "<order>" );
		sbu.append( order );
		sbu.append( "</order>" );
		
		sbu.append( "<serial>" );
		sbu.append( activity.getSerial() );
		sbu.append( "</serial>" );

		sbu.append( "<executorType>" );
		sbu.append( executor.getType() );
		sbu.append( "</executorType>" );
		
		sbu.append( "<executor>" );
		sbu.append( activity.getExecutorAlias() );
		sbu.append( "</executor>" );
		
		sbu.append( "<type>" );
		sbu.append( activity.getType() );
		sbu.append( "</type>" );
		
		sbu.append( "<inputData>" );
			if ( parameter != null ) {
				sbu.append( parameter );
			}
		sbu.append( "</inputData>" );

		// Se getFilesAndConvertData produziu algum arquivo, as tags estar�o em fileXMLEntries
		sbu.append( "<files>" );
			sbu.append( fileXMLEntries.toString() );
		sbu.append( "</files>" );
		
		sbu.append( "<targetTable>" );
		sbu.append( activity.getOutputRelation().getName() );
		sbu.append( "</targetTable>" );

		
		sbu.append( "<command>" );
		sbu.append( command );
		sbu.append( "</command>" );
		
		sbu.append( "</activity>" );
		order++;
		return sbu.toString();
	}
	
	/**
	 * Checa se uma atividade est� contida no fragmento sendo processado.
	 * 
	 * @param fragment
	 * @param act
	 * @return
	 */
	private boolean contains( Activity act ) {
		for ( Activity activity : fragment.getActivities() ) {
			if ( activity.equals( act ) ) {
				return true;
			}
		}
		return false;
	}

	private String goDeep( Activity act ) throws Exception {
		StringBuilder sbu = new StringBuilder();
		if ( contains(act) ) {
			sbu.append( getActivityAsXML(act, null) ); 

			logger.debug("processing " + act.getSerial() + " next activities");
			for ( Activity next : act.getNextActivities() ) {
				sbu.append( goDeep(next) );
			}
		} 
		return sbu.toString();
	}
	
	private String getXMLContent( Activity act, String parameter, String pipelineSerial ) throws Exception {
		logger.debug("will generate xml data for pipeline " + pipelineSerial + " with activity entry point " + act.getSerial() + " (" + act.getTag() + ")");
		StringBuilder sb = new StringBuilder();
		sb.append( "<?xml version='1.0' encoding='UTF-8'?>" );
		sb.append("<pipeline workflow='" +
				act.getFragment().getExperiment().getWorkflow().getTag() + "' experiment='" + 
				act.getFragment().getExperiment().getTagExec() + "' serial='" + pipelineSerial + "' id='##TAG_ID_PIPELINE##'" +  
				" fragment='" + fragment.getSerial() + "'>" );
		sb.append( getActivityAsXML(act, parameter) );
		
		logger.debug("processing " + act.getSerial() + " next activities");
		
		for( Activity next : act.getNextActivities() ) {
			sb.append( goDeep( next ) );
		}
		
		logger.debug("pipeline " + pipelineSerial + " xml data generation is done");
		sb.append( "</pipeline>" );
		return sb.toString();
	}

	
}
