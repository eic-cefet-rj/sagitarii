
package br.cefetrj.sagitarii.action;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.InterceptorRef;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import br.cefetrj.sagitarii.core.Node;
import br.cefetrj.sagitarii.core.NodesManager;

@Action (value = "showNodeDetails", results = { @Result (location = "viewNodeDetail.jsp", name = "ok") 
}, interceptorRefs= { @InterceptorRef("seguranca")	 } ) 

@ParentPackage("default")
public class ShowNodeDetailsAction extends BasicActionClass {
	private String macAddress;
	private Node cluster;
	
	public String execute () {
		NodesManager cm = NodesManager.getInstance();
		cluster = cm.getNode( macAddress );
		return "ok";
	}

	public void setMacAddress(String macAddress) {
		this.macAddress = macAddress;
	}
	
	public Node getCluster() {
		return cluster;
	}
	
}
