package cmabreu.sagitarii.persistence.services;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cmabreu.sagitarii.persistence.entity.Workflow;
import cmabreu.sagitarii.persistence.exceptions.DatabaseConnectException;
import cmabreu.sagitarii.persistence.exceptions.DeleteException;
import cmabreu.sagitarii.persistence.exceptions.InsertException;
import cmabreu.sagitarii.persistence.exceptions.NotFoundException;
import cmabreu.sagitarii.persistence.exceptions.UpdateException;
import cmabreu.sagitarii.persistence.repository.WorkflowRepository;

public class WorkflowService { 
	private WorkflowRepository rep;
	private Logger logger = LogManager.getLogger( this.getClass().getName() );
	
	public WorkflowService() throws DatabaseConnectException {
		this.rep = new WorkflowRepository();
	}
	

	public void newTransaction() {
		rep.newTransaction();
	}
	
	/**
	 * Inclui um novo workflow no banco de dados.
	 * 
	 * @param workflow o workflow a ser incluído.
	 * @throws InsertException
	 */
	public void insertWorkflow(Workflow workflow) throws InsertException {
		logger.debug("inserting workflow " + workflow.getTag() );
		rep.insertWorkflow( workflow );
	}
	
	
	/**
	 * Atualiza um workflow no banco de dados.
	 * 
	 * @param workflow o objeto workflow a ser atualizado.
	 * @throws UpdateException
	 */
	public void updateWorkflow(Workflow workflow) throws UpdateException {
		Workflow oldWorkflow;
		try {
			oldWorkflow = rep.getWorkflow( workflow.getIdWorkflow() );
		} catch (NotFoundException e) {
			logger.debug( e.getMessage() );
			throw new UpdateException( e.getMessage() );
		}
		
		oldWorkflow.setDescription( workflow.getDescription() );
		oldWorkflow.setTag( workflow.getTag() );

		rep.newTransaction();
		rep.updateWorkflow(oldWorkflow);
	}	

	
	/**
	 * Atualiza a definicao de fluxo de um workflow no banco de dados.
	 * 
	 * @param workflow o objeto workflow a ser atualizado.
	 * @throws UpdateException
	 */
	public void updateWorkflowActivities(Workflow workflow) throws UpdateException {
		Workflow oldWorkflow;
		try {
			oldWorkflow = rep.getWorkflow( workflow.getIdWorkflow() );
		} catch (NotFoundException e) {
			logger.debug( e.getMessage() );
			throw new UpdateException( e.getMessage() );
		}
		
		oldWorkflow.setActivitiesSpecs( workflow.getActivitiesSpecs() );
		oldWorkflow.setImagePreviewData( workflow.getImagePreviewData() );

		rep.newTransaction();
		rep.updateWorkflow(oldWorkflow);
	}	

	
	/**
	 * Retorna um objeto workflow do banco de dados usando o seu ID como critério de busca.
	 * 
	 * @param idWorkflow o id do workflow no banco de dados.
	 * @return um objeto WorkflowDB.
	 * 
	 * @throws NotFoundException
	 */
	public Workflow getWorkflow(int idWorkflow) throws NotFoundException{
		return rep.getWorkflow(idWorkflow);
	}

	
	/**
	 * Retorna um objeto workflow do banco de dados usando a sua TAG como critério de busca.
	 * 
	 * @param tag a tag do workflow no banco de dados.
	 * @return um objeto Workflow.
	 * 
	 * @throws NotFoundException em caso de erro.
	 */
	public Workflow getWorkflow(String tag) throws NotFoundException{
		return  rep.getWorkflow(tag);
	}

	
	
	/**
	 * Exclui um workflow do banco de dados.
	 * @param idWorkflow
	 * @throws DeleteException
	 */
	public Workflow deleteWorkflow( int idWorkflow ) throws DeleteException {
		Workflow workflow = null;
		try {
			workflow = rep.getWorkflow(idWorkflow);
			
			if ( workflow.getExperiments().size() > 0 ) {
				throw new DeleteException("This workflow still have experiments");
			}
			
			rep.newTransaction();
			rep.deleteWorkflow(workflow);
		} catch (NotFoundException e) {
			logger.error( e.getMessage() );
			throw new DeleteException( e.getMessage() );
		}
		return workflow;
	}

	
	public Workflow getPendent() throws NotFoundException {
		return rep.getPendent();
	}
	
	
	/**
	 * Retorna uma lista de workflows.
	 * 
	 * @return uma lista com todos os workflows do banco de dados.
	 * 
	 * TODO: Filtar por algum critério. 
	 * 
	 * @throws NotFoundException
	 */
	public List<Workflow> getList() throws NotFoundException {
		logger.debug( "retrieving workflow list..." );  
		List<Workflow> preList = rep.getList();
		logger.debug( "done." );  
		return preList;	
	}
	
}