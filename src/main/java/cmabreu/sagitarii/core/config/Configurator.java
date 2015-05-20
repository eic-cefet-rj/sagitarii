package cmabreu.sagitarii.core.config;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Configurator {

	private int poolIntervalSeconds;
	private int pseudoClusterIntervalSeconds;
	private Document doc;
	private int pseudoMaxTasks;
	private int maxInputBufferCapacity;
	private int mainNodesQuant;
	private static Configurator instance;
	private int fileReceiverPort;
	private int fileReceiverChunkBufferSize;
	private char CSVDelimiter;
	
	public char getCSVDelimiter() {
		return CSVDelimiter;
	}
	
	public int getFileReceiverChunkBufferSize() {
		return fileReceiverChunkBufferSize;
	}
	
	public int getFileReceiverPort() {
		return fileReceiverPort;
	}
	
	public int getMaxInputBufferCapacity() {
		return maxInputBufferCapacity;
	}
	
	public int getMainNodesQuant() {
		return mainNodesQuant;
	}
	
	public int getPseudoMaxTasks() {
		return pseudoMaxTasks;
	}

	public int getPseudoClusterIntervalSeconds() {
		return pseudoClusterIntervalSeconds;
	}

	public int getPoolIntervalSeconds() {
		return poolIntervalSeconds;
	}


	
	private String getTagValue(String sTag, Element eElement) throws Exception{
		try {
			NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();
	        Node nValue = (Node) nlList.item(0);
			return nValue.getNodeValue();
		} catch ( Exception e ) {
			System.out.println("Error: Element " + sTag + " not found in configuration file.");
			throw e;
		}
	 }
	
	public String getValue(String container, String tagName) {
		String tagValue = "";
		try {
			NodeList postgis = doc.getElementsByTagName(container);
			Node pgconfig = postgis.item(0);
			Element pgElement = (Element) pgconfig;
			tagValue = getTagValue(tagName, pgElement) ; 
		} catch ( Exception e ) {
		}
		return tagValue;
	}


	public static Configurator getInstance() throws Exception {
		if ( instance == null ) {
			throw new Exception("Configurator not initialized");
		}
		return instance;
	}
	
	public static Configurator getInstance( String file ) throws Exception {
		if ( instance == null ) {
			instance = new Configurator(file);
		}
		return instance;
	}
	
	private Configurator(String file) throws Exception {
		try {
			InputStream is = this.getClass().getClassLoader().getResourceAsStream(file);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(is);
			doc.getDocumentElement().normalize();
			loadMainConfig();
		  } catch (Exception e) {
				System.out.println("Error: XML file " + file + " not found.");
		  }			
	}
	
	
	public void loadMainConfig()  {
		NodeList mapconfig = doc.getElementsByTagName("orchestrator");
		Node mpconfig = mapconfig.item(0);
		Element mpElement = (Element) mpconfig;
		try {
			poolIntervalSeconds = Integer.valueOf( getTagValue("poolIntervalSeconds", mpElement) );
			pseudoClusterIntervalSeconds = Integer.valueOf( getTagValue("pseudoClusterIntervalSeconds", mpElement) );
			pseudoMaxTasks = Integer.valueOf( getTagValue("pseudoMaxTasks", mpElement) );
			maxInputBufferCapacity = Integer.valueOf( getTagValue("maxInputBufferCapacity", mpElement) );
			mainNodesQuant = Integer.valueOf( getTagValue("mainNodesQuant", mpElement) );
			fileReceiverPort = Integer.valueOf( getTagValue("fileReceiverPort", mpElement) );
			fileReceiverChunkBufferSize = Integer.valueOf( getTagValue("fileReceiverChunkBufferSize", mpElement) );
			CSVDelimiter = getTagValue("CSVDelimiter", mpElement).charAt(0);
		} catch ( Exception e ) {
			System.out.println( e.getMessage() );
		}
	}
	
	
}
