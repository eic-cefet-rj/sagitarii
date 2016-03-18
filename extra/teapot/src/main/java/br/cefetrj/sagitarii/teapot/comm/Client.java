package br.cefetrj.sagitarii.teapot.comm;
/**
 * Copyright 2015 Carlos Magno Abreu
 * magno.mabreu@gmail.com 
 *
 * Licensed under the Apache  License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required  by  applicable law or agreed to in  writing,  software
 * distributed   under the  License is  distributed  on  an  "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the  specific language  governing  permissions  and
 * limitations under the License.
 * 
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import br.cefetrj.sagitarii.teapot.Configurator;
import br.cefetrj.sagitarii.teapot.LogManager;
import br.cefetrj.sagitarii.teapot.Logger;
import br.cefetrj.sagitarii.teapot.Task;
import br.cefetrj.sagitarii.teapot.ZipUtil;
import br.cefetrj.sagitarii.teapot.comm.uploadstrategies.FTPUploadStrategy;
import br.cefetrj.sagitarii.teapot.comm.uploadstrategies.IUploadStrategy;
import br.cefetrj.sagitarii.teapot.torrent.SynchFolderClient;

import com.turn.ttorrent.common.Torrent;
 
public class Client {
	private List<String> filesToSend;
	private String storageAddress;
	private int storagePort;
	private int fileSenderDelay;
	private String sessionSerial;
	private String sagiHost;
	private String announceUrl;
	private IUploadStrategy uploadStrategy;
	
	private Logger logger = LogManager.getLogger( this.getClass().getName() );

	
	public Client( Configurator configurator ) {
		filesToSend = new ArrayList<String>();
		this.storageAddress = configurator.getStorageHost();
		this.storagePort = configurator.getStoragePort();
		this.sagiHost = configurator.getHostURL();
		this.fileSenderDelay = configurator.getFileSenderDelay();
		this.announceUrl = configurator.getAnnounceUrl();
		
		
		// Choose the upload strategy:
		uploadStrategy = new FTPUploadStrategy(logger, storageAddress, storagePort, "cache", "cache", fileSenderDelay);
		
	}
	
	
	public void sendFile( String fileName, String folder, String targetTable, String experimentSerial,  
			String macAddress, Task task ) throws Exception {

		String instanceSerial = "";
		String activity = "";
		String fragment = "";
		String taskId = "";
		String exitCode = "0";
		String startTimeMillis = "";
		String finishTimeMillis = "";
		
		if ( task != null ) {
			instanceSerial = task.getActivation().getInstanceSerial();
			activity = task.getActivation().getActivitySerial();
			fragment = task.getActivation().getFragment();
			exitCode = String.valueOf( task.getExitCode() );
			taskId = task.getActivation().getTaskId();
			
			startTimeMillis = String.valueOf( task.getRealStartTime().getTime() );
			finishTimeMillis = String.valueOf( task.getRealFinishTime().getTime() );
		}			
		
		getSessionKey();
		
		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		
		xml.append("<session macAddress=\""+macAddress+"\" instance=\""+instanceSerial+
				"\" activity=\""+activity+"\"  taskId=\""+taskId+"\" exitCode=\""+exitCode+"\" fragment=\""+fragment + 
				"\" startTime=\""+startTimeMillis + "\" finishTime=\""+finishTimeMillis +
				"\" totalFiles=\"#TOTAL_FILES#\" experiment=\""+experimentSerial + "\" id=\""+sessionSerial+"\" targetTable=\""+targetTable+"\">\n");
		
		
		File fil = new File(folder + "/" + fileName);
		if ( fil.exists() ) {
			xml.append("<file name=\""+fileName+"\" type=\"FILE_TYPE_CSV\" />\n");
			filesToSend.add( folder + "/" + fileName );
		} else {
			logger.error("will not send sagi_output.txt in session.xml file: this activity instance produced no data");
		}
		

		File filesFolder = new File( folder + "/" + "outbox" );
	    for (final File fileEntry : filesFolder.listFiles() ) {
	        if ( !fileEntry.isDirectory() ) {
	        	ZipUtil.compress( folder + "/" + "outbox/" + fileEntry.getName(), folder + "/" + "outbox/" + fileEntry.getName() + ".gz" );
	    		xml.append("<file name=\""+fileEntry.getName()+"\" type=\"FILE_TYPE_FILE\" />\n");
	    		// After compress, remove the original file to avoid send both in torrent
	    		fileEntry.delete();
	        }
	    }
		
		
		File f = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath() );
		String storageRootFolder =  f.getAbsolutePath();
		storageRootFolder = storageRootFolder.substring(0, storageRootFolder.lastIndexOf( File.separator ) + 1) + "namespaces/";
		
		String folderName = "outbox";
		String folderPath = folder.replace(storageRootFolder, "").replaceAll("/+", "/");
		
		logger.debug("sending content of folder:");
		logger.debug(" > " + folderPath );
		
		SynchFolderClient sfc = new SynchFolderClient( storageRootFolder , announceUrl );
		Torrent torrent = sfc.createTorrentFromFolder(folderPath, folderName);
		String torrentFile = "";
		if ( torrent != null ) {
			//torrentFile = storageRootFolder + "/" + torrent.getHexInfoHash() + ".torrent";
			torrentFile = storageRootFolder + "/" + folderPath + "/" + torrent.getHexInfoHash() + ".torrent";

			File tor = new File(torrentFile);
			if ( tor.exists() ) {
				xml.append("<file name=\""+tor.getName()+"\" type=\"FILE_TYPE_TORRENT\" />\n");
				filesToSend.add( torrentFile );
			} else {
				logger.error("will not send Torrent file.");
			}
		}

	    xml.append("<file name=\"session.xml\" type=\"FILE_TYPE_SESSION\" />\n");
	    
	    xml.append("<console><![CDATA[");
	    if ( task != null ) {
	    	for ( String line : task.getConsole() ) {
	    		byte pline[] = line.getBytes("UTF-8");
	    		xml.append( new String(pline, "UTF-8") + "\n" );
	    	}
	    }
	    xml.append("]]></console>");

	    
	    xml.append("<execLog><![CDATA[");
	    if ( task != null ) {
	    	for ( String line : task.getExecLog() ) {
	    		byte pline[] = line.getBytes("UTF-8");
	    		xml.append( new String(pline, "UTF-8") + "\n" );
	    	}
	    }
	    xml.append("]]></execLog>");
	    
		xml.append("</session>\n");
		filesToSend.add( folder + "/" + "session.xml" );
		
		Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(folder + "/" + "session.xml"), "UTF-8"));
		writer.write( xml.toString().replace("#TOTAL_FILES#", String.valueOf(filesToSend.size()) ) );
		writer.close();

		// Send files
		long totalBytesSent = 0;
		if ( filesToSend.size() > 0 ) {
			logger.debug("need to send " + filesToSend.size() + " files to Sagitarii...");
			totalBytesSent = totalBytesSent + uploadFile( filesToSend, targetTable, experimentSerial, sessionSerial, folderPath );
			logger.debug("total bytes sent: " + totalBytesSent );
		}
		
		commit();
		
		if ( torrent != null ) {
			logger.debug("Will wait for Sagitarii to download the torrent...");
			sfc.shareFile( torrentFile );
			sfc.waiForFinish();
			logger.debug("Done. Upload task finished.");
		}
		
	}
	

	private synchronized long uploadFile( List<String> fileNames, String targetTable, String experimentSerial, 
			String sessionSerial, String sourcePath ) throws Exception {

		return uploadStrategy.uploadFile(fileNames, targetTable, experimentSerial, sessionSerial, sourcePath);
		
	}

	private void commit() throws Exception {
		logger.debug("session "+sessionSerial+" commit.");
		URL url = new URL( sagiHost + "/sagitarii/transactionManager?command=commit&sessionSerial=" + sessionSerial );
		Scanner s = new Scanner( url.openStream() );
		String response = s.nextLine();
		logger.debug("server commit response: " + response);
		s.close();
	}
	
	private void getSessionKey() throws Exception {
		URL url = new URL( sagiHost + "/sagitarii/transactionManager?command=beginTransaction");
		Scanner s = new Scanner( url.openStream() );
		sessionSerial = s.nextLine();
		logger.debug("open session " + sessionSerial );
		s.close();
	}
	
}