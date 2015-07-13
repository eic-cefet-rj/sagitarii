package cmabreu.sagitarii.core.delivery;

import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cmabreu.sagitarii.core.processor.Activation;
import cmabreu.sagitarii.core.processor.XMLParser;
import cmabreu.sagitarii.misc.DateLibrary;
import cmabreu.sagitarii.persistence.entity.Instance;

public class DeliveryUnit {
	private Instance instance;
	private List<Activation> activations;
	private String macAddress;
	private Date deliverTime;
	private Date receiveTime;
	private String hash;
	
	public List<Activation> getActivations() {
		return activations;
	}
	
	public String getHash() {
		return hash;
	}
	
	public Instance getInstance() {
		return instance;
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
	
	public long getAgeMillis() {
		try {
			Date endTime = Calendar.getInstance().getTime();
			if( receiveTime != null ) {
				endTime = receiveTime;
			} 
			DateLibrary.getInstance().setTo( deliverTime );
			Calendar data = Calendar.getInstance();
			data.setTime(endTime);
			long millis = DateLibrary.getInstance().getDiffMillisTo( data );
			return millis;
		} catch ( Exception e ) {
			return 0;
		}
	}

	public String getAgeTime() {
		long millis = getAgeMillis();
		String retorno = String.format("%02d:%02d:%02d", 
				TimeUnit.MILLISECONDS.toHours(millis),
				TimeUnit.MILLISECONDS.toMinutes(millis) -  
				TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)), 
				TimeUnit.MILLISECONDS.toSeconds(millis) - 
				TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))); 	
		return retorno;
	}
	
	public Date getDeliverTime() {
		return deliverTime;
	}

	public void setDeliverTime(Date deliverTime) {
		this.deliverTime = deliverTime;
	}

	public Date getReceiveTime() {
		return receiveTime;
	}

	public void setReceiveTime(Date receiveTime) {
		this.receiveTime = receiveTime;
	}

	
	
}
