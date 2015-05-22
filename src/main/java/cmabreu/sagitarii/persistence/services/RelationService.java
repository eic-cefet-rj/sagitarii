package cmabreu.sagitarii.persistence.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cmabreu.sagitarii.core.DomainStorage;
import cmabreu.sagitarii.core.ReceivedData;
import cmabreu.sagitarii.core.Sagitarii;
import cmabreu.sagitarii.core.SchemaGenerator;
import cmabreu.sagitarii.core.TableAttribute;
import cmabreu.sagitarii.core.UserTableEntity;
import cmabreu.sagitarii.core.config.Configurator;
import cmabreu.sagitarii.core.sockets.FileImporter;
import cmabreu.sagitarii.misc.DatabaseConnectionItem;
import cmabreu.sagitarii.misc.json.JsonUserTableConversor;
import cmabreu.sagitarii.persistence.entity.Consumption;
import cmabreu.sagitarii.persistence.entity.CustomQuery;
import cmabreu.sagitarii.persistence.entity.Domain;
import cmabreu.sagitarii.persistence.entity.Experiment;
import cmabreu.sagitarii.persistence.entity.Relation;
import cmabreu.sagitarii.persistence.exceptions.DatabaseConnectException;
import cmabreu.sagitarii.persistence.exceptions.DeleteException;
import cmabreu.sagitarii.persistence.exceptions.NotFoundException;
import cmabreu.sagitarii.persistence.exceptions.UpdateException;
import cmabreu.sagitarii.persistence.repository.RelationRepository;

public class RelationService { 
	private RelationRepository rep;
	private Logger logger = LogManager.getLogger( this.getClass().getName() );
	
	public RelationService() throws DatabaseConnectException {
		this.rep = new RelationRepository();
	}
	
	public void copy( String table, int sourceExperiment, int targetExperiment ) throws Exception {
		logger.debug("copy table from experiment " + sourceExperiment + " to " + targetExperiment );
		Set<UserTableEntity> structure = getTableStructure( table );
		
		String tableColumns = "";
		for ( UserTableEntity ute : structure ) {
			String columnName = ute.getData("column_name");
			if ( ( !columnName.equals("id_experiment") ) && ( !columnName.equals("index_id") ) ) {
				tableColumns = tableColumns + columnName + ",";
			}
		}
		tableColumns = tableColumns.substring(0, tableColumns.length()-1);
		String selectSourceData = "select " + targetExperiment + " as id_experiment," + 
				tableColumns + " from " + table + " where id_experiment = " + sourceExperiment;
		
		String insertTargetData = "insert into " + table + "(id_experiment," + tableColumns + ")" + selectSourceData;
		logger.debug("transfer query: " + insertTargetData );
		
		newTransaction();
		executeQuery( insertTargetData );
		
	}
	
	public void commitAndClose() throws Exception {
		try {
			rep.commit();
			rep.closeSession();
		} catch ( Exception e ) {
			rep.rollBack();
			rep.closeSession();
			throw e;
		}
	}

	public void rollbackAndClose() throws Exception {
		try {
			rep.rollBack();
			rep.closeSession();
		} catch ( Exception e ) {
			throw e;
		}
	}

	public String inspectExperimentQueryPagination(CustomQuery query, String sortColumn, String sSortDir0,
			String iDisplayStart, String iDisplayLength, String sEcho) throws Exception {
		
		String sql = "select * from (" + query.getQuery() + ") as qq order by " + 
				sortColumn + " " + sSortDir0 + " offset " + iDisplayStart + " limit " + iDisplayLength ;

		Set<UserTableEntity> result = new HashSet<UserTableEntity>();
		int totalRecords = 0;
		
		if ( !sortColumn.equals("ERROR") ) {
			result = genericFetchList( sql );
			newTransaction();
			
			String countSql = "select count(*) as count from (" + query.getQuery() + ") as qq";
			Set<UserTableEntity> resultCount = genericFetchList( countSql );
			UserTableEntity count = resultCount.iterator().next();
			totalRecords = Integer.valueOf( count.getData("count") ); 
		} else {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR", "No data in query '" + query.getName() + "'");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);

		}
		return new JsonUserTableConversor().asJson( result, totalRecords, Integer.valueOf( sEcho ) );
	}

	
	public String inspectExperimentTablePagination(String tableName, int idExperiment, String sortColumn, String sSortDir0,
			String iDisplayStart, String iDisplayLength, String sEcho) throws Exception {
		
		String sql = "select * from " + tableName + " where id_experiment = " + idExperiment + " order by " + 
				sortColumn + " " + sSortDir0 + " offset " + iDisplayStart + " limit " + iDisplayLength ;

		Set<UserTableEntity> result = new HashSet<UserTableEntity>();
		int totalRecords = 0;
		
		if ( !sortColumn.equals("ERROR") ) {
			result = genericFetchList( sql );
			
			// Show ID pipeline as a link
			for ( UserTableEntity ute : result  ) {
				String idPipeline = ute.getData("id_pipeline"); 
				if ( (idPipeline != null) && ( !idPipeline.equals("") ) ) {
					ute.setData("id_pipeline", "<a href='viewPipeline?tableName="+tableName+"&idExperiment="+idExperiment+"&idPipeline="+idPipeline+"'>"+idPipeline+"</a>");
				} else {
					ute.setData("id_pipeline", "n/e");
				}
			}
			
			newTransaction();
			totalRecords = getCount( tableName, "where id_experiment = " + idExperiment);
		} else {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR", "No data in table '" + tableName + "' for this experiment");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);

		}
		return new JsonUserTableConversor().asJson( result, totalRecords, Integer.valueOf( sEcho ) );
	}
	
	public Set<UserTableEntity> inspectExperimentTable( String tableName, Experiment experiment ) throws Exception {
		String sql = "select * from " + tableName + " where id_experiment = " + experiment.getIdExperiment();
		Set<UserTableEntity> result = genericFetchList( sql );

		if ( result.size() == 0 ) {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR", "No Data");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);
		} else {
		
			for ( UserTableEntity ute : result  ) {
				
				for ( String columnName : ute.getColumnNames() ) {
					String domainName = tableName + "." + columnName;
					Domain domain = DomainStorage.getInstance().getDomain( domainName );
					if ( domain != null ) {
						String idFile = ute.getData( columnName ); 
						ute.setData(columnName, "<a href='viewPipeline?tableName="+idFile+"'>"+columnName+"</a>");
					}
				}
				
				
				String idPipeline = ute.getData("id_pipeline"); 
				if ( (idPipeline != null) && ( !idPipeline.equals("") ) ) {
					ute.setData("id_pipeline", "<a href='viewPipeline?tableName="+tableName+"&idExperiment="+experiment.getIdExperiment()+"&idPipeline="+idPipeline+"'>"+idPipeline+"</a>");
				} else {
					ute.setData("id_pipeline", "n/e");
				}
			}
			
		}
		
		return result;
	}


	public Set<UserTableEntity> viewSql( String tableName ) throws Exception {
		String sql = "select * from " + tableName;
		Set<UserTableEntity> result = genericFetchList( sql );
		if ( result.size() == 0 ) {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR", "No Data");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);
		}
		return result;
	}

	public String viewSqlPagination(String tableName, String sortColumn, String sSortDir0,
			String iDisplayStart, String iDisplayLength, String sEcho) throws Exception {
		
		String sql = "select * from " + tableName +  " order by " + 
				sortColumn + " " + sSortDir0 + " offset " + iDisplayStart + " limit " + iDisplayLength ;

		Set<UserTableEntity> result = new HashSet<UserTableEntity>();
		int totalRecords = 0;
		
		if ( !sortColumn.equals("ERROR") ) {
			result = genericFetchList( sql );
			newTransaction();
			totalRecords = getCount( tableName, "where 1 = 1");
		} else {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR", "No data in table '" + tableName + "' for this experiment");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);

		}
		return new JsonUserTableConversor().asJson( result, totalRecords, Integer.valueOf( sEcho ) );
	}

	
	public Set<UserTableEntity> getGeneratedData( String tableName, int idPipeline, int idExperiment ) throws Exception {
		String sql = "select * from " + tableName + " where id_pipeline = " + idPipeline;
		Set<UserTableEntity> result = genericFetchList( sql );
		if ( result.size() == 0 ) {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR",  "No data in table '" + tableName + "' for this pipeline");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);
		} else {
			for ( UserTableEntity ute : result ) {
				String sIdPipeline = ute.getData("id_pipeline"); 
				if ( (sIdPipeline != null) && ( !sIdPipeline.equals("") ) ) {
					ute.setData("id_pipeline", "<a href='viewPipeline?tableName="+tableName+"&idExperiment="+idExperiment+"&idPipeline="+sIdPipeline+"'>"+sIdPipeline+"</a>");
				} else {
					ute.setData("id_pipeline", "n/e");
				}					
			}
		}
		return result;
	}

	
	public Set<UserTableEntity> getConsumptionsData( Set<Consumption> consumptions, int idExperiment ) throws Exception {
		Set<UserTableEntity> result = new HashSet<UserTableEntity>();
		for ( Consumption consumption : consumptions ) {
			int idTable = consumption.getIdTable();
			newTransaction();
			Relation relation = getTable(idTable);
			String sql = "select t.name as table_name,u.* from " + relation.getName() + " u join tables t on t.id_table = "+idTable+" where index_id = " + consumption.getIdRow();
			newTransaction();
			result.addAll( genericFetchList( sql ) );
		}
		
		if ( result.size() == 0 ) {
			Map<String,String> data = new HashMap<String,String>();
			data.put("ERROR",  "No consumptions found for this pipeline");
			UserTableEntity ute = new UserTableEntity(data);
			result.add(ute);
		} else {
			for ( UserTableEntity ute : result ) {
				String sIdPipeline = ute.getData("id_pipeline"); 
				if ( (sIdPipeline == null) || ( sIdPipeline.equals("") ) ) {
					ute.setData("id_pipeline", "n/e");
				}					
			}
		}

		return result;
	}

	
	public void newTransaction() {
		if ( !rep.isOpen() ) {
			rep.newTransaction();
		}
	}
	
	public int insertTable(Relation table, List<TableAttribute> attributes) throws Exception{
		
		table.setName( table.getName().toLowerCase().replace(" ", "") );
		
		if ( Character.isDigit( table.getName().charAt(0) ) ) {
			throw new Exception("Table name cannot start with numbers.");
		}

		for ( TableAttribute attr : attributes) {
			attr.setName( attr.getName().toLowerCase().replace(" ", "") );
			if ( Character.isDigit( attr.getName().charAt(0) )  ) {
				throw new Exception("Attribute name cannot start with numbers ("+attr.getName()+").");
			}
		}
		logger.debug("generating schema for " + table.getName() );
		table.setSchema( SchemaGenerator.generateSchema( table.getName(), attributes) );
		logger.debug("inserting relation " + table.getName() + "..." );
		rep.insertTable( table, attributes );
		logger.debug("done inserting relation " + table.getName() );
		
		Sagitarii.getInstance().stopProcessing();
		try {
			RelationRepository newRepo = new RelationRepository();
			logger.debug("executing DDL schema creation for " + table.getName() + "..." );
			newRepo.createDatabaseTable( table.getSchema() );
			logger.debug("finished table creation for table " + table.getName() + ". Creating index..."  );
			newRepo.newTransaction();
			newRepo.createInternalIndex( table.getName() );
			
		} finally {
			Sagitarii.getInstance().resumeProcessing();
		}
		
		return 0;
	}

	
	public void executeQuery(String query) throws Exception {
		if ( !rep.isOpen() ) {
			newTransaction();
		}
		rep.executeQuery(query);
	}
	public void executeQueryAndKeepOpen(String query) throws Exception {
		if ( !rep.isOpen() ) {
			newTransaction();
		}
		rep.executeQueryAndKeepOpen(query);
	}

	/**
	 * Retorna a estrutura de determinada tabela 
	 * 
	 * 	***** ALERTA : CONTÉM CÓDIGO ESPECÍFICO PARA POSTGRE SQL *****
	 * 
	 * @param tableName
	 * @return Lista de UserTableEntity
	 * @throws Exception
	 */
	public Set<UserTableEntity> getTableStructure(String tableName) throws Exception {
		logger.debug("get schema from " + tableName );
		if ( !rep.isOpen() ) {
			rep.newTransaction();
		}
		return genericFetchList("SELECT column_name, data_type FROM information_schema.columns WHERE table_name='"+tableName+"'");
	}
	
	
	/**
	 * Retorna as conexões abertas pelo Sagitarii e a query que está sendo executada por cada uma. 
	 * 
	 * 	***** ALERTA : CONTÉM CÓDIGO ESPECÍFICO PARA POSTGRE SQL *****
	 */
	public List<DatabaseConnectionItem> getConnectionUse() throws Exception {
		logger.debug("get database connection use");
		if ( !rep.isOpen() ) {
			rep.newTransaction();
		}
		Set<UserTableEntity> connections = genericFetchList("select application_name, query_start, state_change, query, datname, state FROM pg_stat_activity");
		List<DatabaseConnectionItem> result = new ArrayList<DatabaseConnectionItem>();
		for ( UserTableEntity conn : connections ) {
			// exclude this query from result
			if ( !conn.getData("query").contains("select application_name,") ) {
				DatabaseConnectionItem query = new DatabaseConnectionItem(conn.getData("application_name"), conn.getData("query_start"), conn.getData("datname"),
						conn.getData("state"), conn.getData("query") );
				result.add( query );
			}
		}
		return result;
	}

	
	public int getCount( String tableName, String criteria ) throws Exception {
		if ( !rep.isOpen() ) {
			rep.newTransaction();
		}
		return rep.getCount( tableName, criteria );
	}
	
	@SuppressWarnings("rawtypes")
	public Set<UserTableEntity> genericFetchList(String query) throws Exception {
		logger.debug("generic fetch " + query );
		if ( !rep.isOpen() ) {
			rep.newTransaction();
		}
		Set<UserTableEntity> result = new LinkedHashSet<UserTableEntity>();
		for ( Object obj : rep.genericFetchList(query) ) {
			UserTableEntity ut = new UserTableEntity( (Map)obj );
			result.add(ut);
		}
		return result;
	}
	
	
	/**
	 * Verifica se uma coluna de determinado nome existe na estrutura de uma tabela.
	 * @param attribute
	 * @param structure
	 * @return
	 */
	private boolean attributeExists( String attribute, Set<UserTableEntity> structure ) {
		for ( UserTableEntity ute : structure  ) {
			if ( ute.hasContent( attribute ) ) {
				return true;
			}
		}
		return false;
	}
	
	public void updateTable(Relation table) throws UpdateException {
		Relation oldTable;
		try {
			oldTable = rep.getTable( table.getIdTable() );
		} catch (NotFoundException e) {
			logger.debug( e.getMessage() );
			throw new UpdateException( e.getMessage() );
		}
		oldTable.setDescription( table.getDescription() );
		rep.newTransaction();
		rep.updateTable(oldTable);
	}	

	
	/**
	 * Importa uma lista de strings contendo valores CSV para uma tabela do usuário.
	 * (importação de dados iniciais)
	 * 
	 * @param rd um objeto ReceivedData
	 * @param owner o Experimento que será o proprietário dos dados
	 * @param importer um objeto FileImporter  
	 * 
	 * @throws Exception
	 */
	public void importCSVData(ReceivedData rd, Experiment owner, FileImporter importer) throws Exception {
		String sql = "";
		List<String> contentLines = rd.getContentLines();

		// Get table schema: Time consuming + One more database hit!! Need to find a way to avoid this.
		String output = rd.getActivity().getOutputRelation().getName();
		Set<UserTableEntity> structure = getTableStructure(output);
		// ===================
		
		if ( importer != null ) {
			importer.setActivity( rd.getActivity().getTag() );
		}
		
		
		logger.debug("received " + contentLines.size() + " lines of data from teapot. pipeline " + rd.getPipeline().getIdPipeline() + " | activity " + rd.getActivity().getIdActivity() );
		for ( int x = 1; x < contentLines.size(); x++ ) {
			
			String ss = contentLines.get(x);
			
			logger.debug("data: " + ss);
			
			String values = "";
			String columns = "id_experiment,id_activity,id_pipeline," ;
			
			String[] columnsArray = contentLines.get(0).split( String.valueOf( Configurator.getInstance().getCSVDelimiter() ) );
			String[] valuesArray = contentLines.get(x).split( String.valueOf( Configurator.getInstance().getCSVDelimiter() ) );
			
			for ( int z = 0; z < columnsArray.length; z++ ) {
				try {
					if ( attributeExists( columnsArray[z], structure ) ) {
						columns = columns + columnsArray[z] + ",";		
						values = values + "'" + valuesArray[z] + "',";
					}
				} catch ( Exception ex ) {
					logger.error("Error when inserting data into table " + rd.getTable().getName() );
					ex.printStackTrace();
				}
			}
			if ( values.trim().length() > 0 ) {
				values = values.substring(0, values.length()-1);
			} else {
				throw new Exception("None of your CSV columns match table '"+output+"' attribute list. Is this the right table?");
			}

			sql = "insert into " + rd.getTable().getName() + "("+columns.substring(0, columns.length()-1).toLowerCase()+") values ("+ owner.getIdExperiment() + 
					"," + rd.getActivity().getIdActivity() + "," + rd.getPipeline().getIdPipeline() + "," + values + ");";
			
			executeQueryAndKeepOpen(sql);
			if ( importer != null ) {
				if ( importer.forcedToStop() ) {
					rollbackAndClose();
					throw new Exception("Canceled by the user");
				}
				importer.setInsertedLines( x );
			}
			
		}
		commitAndClose();
	}

	public Relation getTable(String name) throws NotFoundException{
		return rep.getTable(name);
	}
	
	public Relation getTable(int idTable) throws NotFoundException{
		return rep.getTable(idTable);
	}
	
	public String deleteTable( int idTable ) throws DeleteException {
		logger.debug( "delete table " + idTable );  
		String tableName = "";
		try {
			Relation table = rep.getTable(idTable);

			if ( getCount( table.getName(), "") > 0 ) {
				throw new DeleteException( "Table " + table.getName() + " is not empty and cannot be deleted." );
			}
			
			rep.newTransaction();
			rep.deleteTable(table);
			tableName = table.getName();
		} catch (Exception e) {
			logger.error( e.getMessage() );  
			throw new DeleteException( e.getMessage() );
		}
		logger.debug( "done." );
		return tableName;
	}
	

	public List<Relation> getList() throws NotFoundException {
		return rep.getList();	
	}	
}
