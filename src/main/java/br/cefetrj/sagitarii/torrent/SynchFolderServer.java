package br.cefetrj.sagitarii.torrent;

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;


// http://www.tools4noobs.com/online_tools/torrent_decode/
// https://github.com/fujohnwang/fujohnwang.github.com/blob/master/posts/2012-04-09-playing-bittorrent-with-ttorrent.md
// https://github.com/mpetazzoni/ttorrent

public class SynchFolderServer {
	private String serverRootFolder;
	private String storageFolder;
	private Tracker tracker;
	private InetAddress bindAddress;
	private Logger logger = LogManager.getLogger( this.getClass().getName() );
	

	private void config( String rootFolder, InetAddress bindAddress ) throws Exception {
		this.serverRootFolder = rootFolder;
		this.storageFolder = serverRootFolder + "/storage" ;
		try {
			new File( storageFolder ).mkdirs();
		} catch ( Exception e ) { 
			//
		}
		tracker = new Tracker( bindAddress );
		this.bindAddress = bindAddress;
	}
	
	public String getStorageFolder() {
		return storageFolder;
	}
	
	public SynchFolderServer( String rootFolder, InetAddress bindAddress ) throws Exception {
		config( rootFolder, bindAddress );
	}
	
	public SynchFolderServer( String rootFolder ) throws Exception {
		config( rootFolder, getFirstNonLoopbackAddress(true,false) );
	}
	
	/*
	private List<File> getFiles( String folderName ) {
		File folder = new File( folderName );
		List<File> files = new ArrayList<File>();
		for (File fileEntry : folder.listFiles() ) {
	        if ( !fileEntry.isDirectory() ) {
	        	files.add( fileEntry );
	        } else {
	        	files.addAll( getFiles( fileEntry.getAbsolutePath() ) );
	        }
	    }
		return files;
	}
	*/

	/*
	public String createTorrentFromFolder( String folderPath, String torrentFileName, String author ) throws Exception {
		String sourceFolder = storageFolder + "/" + folderPath;
		Torrent torrent = Torrent.create(
				new File(sourceFolder), 
				getFiles( sourceFolder ), 
				tracker.getAnnounceUrl().toURI(), author);
		String torrentFile = serverRootFolder + "/" + torrentFileName;
	    FileOutputStream fos = new FileOutputStream( torrentFile );
	    torrent.save( fos );		    
		fos.close();
		return torrentFile;
	}
	*/
	
	/*
	public void showTrackedTorrents() {
		Collection<TrackedTorrent> trackedTorrents = tracker.getTrackedTorrents();
		for ( TrackedTorrent tr : trackedTorrents ) {
			System.out.println( "> Name : " + tr.getName() );
			for ( Entry<String,TrackedPeer> peer : tr.getPeers().entrySet() ) {
				System.out.println( " > Peer:  " + peer.getValue().getIp() + " " + peer.getValue().isCompleted() );
			}
		}
	}
	*/
	
	public String getAnnounceUrl() {
		return tracker.getAnnounceUrl().toString();
	}

	public void stopTracker() {
		tracker.stop();
		logger.debug("Tracker stopped.");
	}

	public void removeFromTracker(String torrentFile) throws Exception {
		File file = new File(torrentFile);
		logger.debug("Will remove from tracker : " + torrentFile );
		if ( !file.exists() ) {
			logger.error("Torrent file not exists.");
			throw new Exception("Torrent file " + torrentFile + " not exists.");
		} else {
			tracker.remove( TrackedTorrent.load(file) );
			logger.debug("Torrent file removed from tracker.");
		}
	}
	
	public void addToTracker(String torrentFile) throws Exception {
		File file = new File(torrentFile);
		logger.debug("will add file " + torrentFile + " to tracker"	);
		if ( !file.exists() ) {
			logger.error("Torrent file not exists.");
			throw new Exception("Torrent file " + torrentFile + " not exists.");
		} else {
			tracker.announce( TrackedTorrent.load( file ) );
			logger.debug("Torrent file " + torrentFile + " added to tracker.");
		}
	} 
	
	public Client downloadFile( String torrentFile ) throws Exception {
		logger.debug("Will download torrent:");

		File tf = new File(torrentFile);
		Torrent tr = Torrent.load( tf, false );
		String parentFolder = tr.getCreatedBy();

		logger.debug(" > " + parentFolder );

		File targetContentFolder = new File( storageFolder + "/" + parentFolder );

		targetContentFolder.mkdirs();
		SharedTorrent st = SharedTorrent.fromFile( tf, targetContentFolder );
		Client client = new Client( bindAddress, st);
		
	    client.download();
	    logger.debug("Downloader created.");
	    return client;
	}
	
	public void startTracker() throws Exception {
		/*
	    FilenameFilter filter = new FilenameFilter() {
	    	  @Override
	    	  public boolean accept(File dir, String name) {
	    		  boolean result = name.endsWith(".torrent");
	    		  return result;
	    	  }
	    };
	    
	    for (File f : new File( serverRootFolder ).listFiles(filter) ) {
	    	logger.debug("Announce " + f.getName() );
	    	tracker.announce( TrackedTorrent.load(f) );
	    }
	    */
	    
		tracker.start();
		
		logger.debug("Tracker running.");
	}
	
	
    private InetAddress getFirstNonLoopbackAddress(boolean preferIpv4, boolean preferIPv6) throws SocketException {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface i = en.nextElement();
            for (Enumeration<InetAddress> en2 = i.getInetAddresses(); en2.hasMoreElements();) {
                InetAddress addr = en2.nextElement();
                if (!addr.isLoopbackAddress()) {
                    if (addr instanceof Inet4Address) {
                        if (preferIPv6) {
                            continue;
                        }
                        return addr;
                    }
                    if (addr instanceof Inet6Address) {
                        if (preferIpv4) {
                            continue;
                        }
                        return addr;
                    }
                }
            }
        }
        return null;
    }    

}