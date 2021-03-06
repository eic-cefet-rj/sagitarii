package br.cefetrj.sagitarii.persistence.services;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import br.cefetrj.sagitarii.core.NodesManager;
import br.cefetrj.sagitarii.core.types.ExecutorType;
import br.cefetrj.sagitarii.misc.PathFinder;
import br.cefetrj.sagitarii.persistence.entity.ActivationExecutor;
import br.cefetrj.sagitarii.persistence.exceptions.DatabaseConnectException;
import br.cefetrj.sagitarii.persistence.exceptions.DeleteException;
import br.cefetrj.sagitarii.persistence.exceptions.InsertException;
import br.cefetrj.sagitarii.persistence.exceptions.NotFoundException;
import br.cefetrj.sagitarii.persistence.exceptions.UpdateException;
import br.cefetrj.sagitarii.persistence.repository.ExecutorRepository;

public class ExecutorService {
	private ExecutorRepository rep;
	private Logger logger = LogManager.getLogger( this.getClass().getName() );

	public String getAsManifest() {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<manifest>\n");
		Set<ActivationExecutor> preList = null;
		try {
			preList = rep.getList();
		} catch ( NotFoundException e ) {
			
		}
		
		sb.append("\t<wrapper activity=\"r-wrapper.jar\" name=\"RRUNNER\" type=\"SYSTEM\" hash=\"RWRAPPER\" target=\"ANY\" version=\"1.0\" />\n");
		
		for ( ActivationExecutor executor :  preList  ) {
			ExecutorType type = executor.getType();
			if ( type != ExecutorType.SELECT ) {
				String alias = executor.getExecutorAlias();
				String wrapper = executor.getActivationWrapper();
				String hash = executor.getHash();
				sb.append("\t<wrapper activity=\""+wrapper+"\" name=\""+ alias +"\" type=\"" + type.toString() + "\" hash=\""+hash+"\" target=\"ANY\" version=\"1.0\" />\n");
			}
		}
		sb.append("</manifest>\n\n");
		return sb.toString();
	}
	
	public ExecutorService() throws DatabaseConnectException {
		this.rep = new ExecutorRepository();
	}

	public void updateExecutor(ActivationExecutor executor) throws UpdateException {
		ActivationExecutor oldExecutor;
		try {
			oldExecutor = rep.getActivationExecutor( executor.getIdActivationExecutor() );
		} catch (NotFoundException e) {
			
			e.printStackTrace();
			
			throw new UpdateException( e.getMessage() );
		}
		
		// If the file name is different, delete the older file.
		if ( ( oldExecutor.getActivationWrapper() != null ) && 
				!oldExecutor.getActivationWrapper().equals( executor.getActivationWrapper() ) ) {
			try {
				String filePath = PathFinder.getInstance().getPath() + "/repository";
				File fil = new File( filePath + "/" + oldExecutor.getActivationWrapper() );
				fil.delete();
				logger.debug("wrapper " + oldExecutor.getExecutorAlias() + " changed from " + oldExecutor.getActivationWrapper() + "to " + executor.getActivationWrapper() );
			} catch ( Exception e ) {
				e.printStackTrace();
				logger.error("cannot remove old executor file: " + oldExecutor.getActivationWrapper() );
			}
		}
		
		String hash = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		oldExecutor.setHash(hash);

		oldExecutor.setSelectStatement( executor.getSelectStatement() );
		
		if( ( executor.getActivationWrapper() != null ) && ( !executor.getActivationWrapper().equals("") )) {
			oldExecutor.setActivationWrapper( executor.getActivationWrapper()  );
		}
		
		newTransaction();
		rep.updateActivationExecutor(oldExecutor);
		NodesManager.getInstance().reloadWrappers();
	}	
	
	public ActivationExecutor getExecutor(int idExecutor) throws NotFoundException{
		return rep.getActivationExecutor(idExecutor);
	}

	public ActivationExecutor getExecutor(String executorAlias) throws NotFoundException{
		return rep.getActivationExecutor(executorAlias);
	}
	
	public void newTransaction() {
		if ( !rep.isOpen() ) {
			rep.newTransaction();
		}
	}
	
	public void updateExecutorHash( int idExecutor ) throws Exception {
		ActivationExecutor executor = rep.getActivationExecutor(idExecutor);
		String hash = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		executor.setHash(hash);
		rep.newTransaction();
		rep.updateActivationExecutor( executor );
		NodesManager.getInstance().reloadWrappers();
	}
	
	public ActivationExecutor insertExecutor(ActivationExecutor executor) throws InsertException {
		logger.debug("inserting executor " + executor.getExecutorAlias() );
		String hash = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		executor.setHash(hash);
		ActivationExecutor expRet = rep.insereActivationExecutor( executor );
		NodesManager.getInstance().reloadWrappers();
		logger.debug("done");
		return expRet;
	}	
	
	public void deleteExecutor( int idExecutor ) throws DeleteException {
		try {
			ActivationExecutor executor = rep.getActivationExecutor(idExecutor);
			rep.newTransaction();
			rep.excluiActivationExecutor(executor);

			if ( (executor.getActivationWrapper() != null) && ( !executor.getActivationWrapper().equals("") ) ) {
				String executorFile = PathFinder.getInstance().getPath() + "/repository/" + executor.getActivationWrapper();
				File file = new File( executorFile );
				file.delete();
			}
			
		} catch (NotFoundException | UnsupportedEncodingException e) {
			logger.error( e.getMessage() );
			throw new DeleteException( e.getMessage() );
		}
	}

	public Set<ActivationExecutor> getList() throws NotFoundException {
		Set<ActivationExecutor> preList = rep.getList();
		return preList;	
	}
	
}
