
package cmabreu.sagitarii.action;

import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.InterceptorRef;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import cmabreu.sagitarii.core.delivery.DeliveryUnit;
import cmabreu.sagitarii.core.delivery.InstanceDeliveryControl;
import cmabreu.sagitarii.core.statistics.AgeCalculator;
import cmabreu.sagitarii.core.statistics.Accumulator;

@Action (value = "viewDeliveryControl", results = { @Result (location = "viewDC.jsp", name = "ok") 
}, interceptorRefs= { @InterceptorRef("seguranca")	 } ) 

@ParentPackage("default")
public class ViewDeliveryControlAction extends BasicActionClass {
	private List<DeliveryUnit> units;
	private List<Accumulator> ageStatistics;
	
	public String execute () {
		units = InstanceDeliveryControl.getInstance().getUnits();
		ageStatistics = AgeCalculator.getInstance().getList();
		return "ok";
	}

	public List<DeliveryUnit> getUnits() {
		return units;
	}

	public List<Accumulator> getAgeStatistics() {
		return ageStatistics;
	}

}
