package br.cefetrj.sagitarii.core;

import br.cefetrj.sagitarii.core.delivery.InstanceDeliveryControl;
import br.cefetrj.sagitarii.metrics.MetricController;


/**
 * Main system heartbeat
 * @author Carlos Magno Abreu
 *
 */
public class MainHeartBeat implements Runnable {
	
    @Override
    public void run() {
    	ClustersManager.getInstance().updateClustersStatus();
    	Sagitarii.getInstance().loadInputBuffer();
   		InstanceDeliveryControl.getInstance().checkLostPackets();
   		ClustersManager.getInstance().saveMetrics();
   		MetricController.getInstance().saveMetrics();
    }
	

}
