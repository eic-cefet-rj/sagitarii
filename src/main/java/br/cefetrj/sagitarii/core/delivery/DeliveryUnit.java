package br.cefetrj.sagitarii.core.delivery;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import br.cefetrj.sagitarii.misc.Activation;
import br.cefetrj.sagitarii.misc.XMLParser;
import br.cefetrj.sagitarii.persistence.entity.Instance;

public class DeliveryUnit {
	private Instance instance;
	private List<Activation> activations;
	private String macAddress;
	//private Date deliverTime;
	//private Date receiveTime;
	private Date endTime;
	private String hash;
	private boolean delayed = false;
	//private Logger logger = LogManager.getLogger( this.getClass().getName() );
	
	public String getInstanceActivities() {
		String prefix = "";
		StringBuilder sb = new StringBuilder();
		for ( Activation act : activations ) {
			sb.append( prefix + act.getExecutor()  );
			prefix = ", ";
		}
		return sb.toString();
	}
	
	public boolean isDelayed() {
		return this.delayed;
	}
	
	public List<Activation> getActivations() {
		return new ArrayList<Activation>( activations );
	}
	
	public Date getEndTime() {
		return endTime;
	}
	
	public String getHash() {
		return hash;
	}
	
	public Instance getInstance() {
		return instance;
	}
	
	public void setDelayed() {
		this.delayed = true;
	}

	private String getHashSHA1( byte[] subject ) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] digest = md.digest( subject );
		
		String result = "";
		for (int i=0; i < digest.length; i++) {
			result +=
				Integer.toString( ( digest[i] & 0xff ) + 0x100, 16).substring( 1 );
		}
		return result;
	}
	
	public void setInstance(Instance instance) {
		this.instance = instance;
		try {
			this.activations = new XMLParser().parseActivations( instance.getContent() );
			StringBuilder sb = new StringBuilder();
			for ( Activation act : this.activations ) {
				sb.append( act.getExecutor() );
			}
			hash = getHashSHA1( sb.toString().getBytes() );
		} catch (Exception e) { }
	}


	public String getMacAddress() {
		return macAddress;
	}
	
	public void setMacAddress(String macAddress) {
		this.macAddress = macAddress;
	}
	
	/*
	public long getAgeMillis() {
		try {
			endTime = Calendar.getInstance().getTime();
			if( receiveTime != null ) {
				endTime = receiveTime;
			} 
			
			Long endMillis = endTime.getTime();
			Long deliverMillis = deliverTime.getTime(); 
			
			logger.debug("calculating age : start=" + deliverMillis + " end="+endMillis + " age=" + (endMillis - deliverMillis));
			
			return endMillis - deliverMillis;
		} catch ( Exception e ) {
			logger.error("error calculating instance age: " + e.getMessage() );
			return 0;
		}
	}
	*/

	public String getAgeTime() {
		return instance.getElapsedTime();
		/*
		long millis = getAgeMillis();
		String retorno = String.format("%02d:%02d:%02d", 
				TimeUnit.MILLISECONDS.toHours(millis),
				TimeUnit.MILLISECONDS.toMinutes(millis) -  
				TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)), 
				TimeUnit.MILLISECONDS.toSeconds(millis) - 
				TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))); 	
		return retorno;
		*/
	}
	
	public Date getDeliverTime() {
		return instance.getStartDateTime();
	}

	/*
	public void setDeliverTime(Date deliverTime) {
		this.deliverTime = deliverTime;
	}

	public Date getReceiveTime() {
		return receiveTime;
	}

	public void setReceiveTime(Date receiveTime) {
		this.receiveTime = receiveTime;
	}

	*/
	
}
