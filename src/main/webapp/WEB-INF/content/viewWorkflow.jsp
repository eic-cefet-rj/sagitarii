<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="../../header.jsp" %>

				<div id="leftBox"> 
					<div id="bcbMainButtons" class="basicCentralPanelBar" style="height:50px">
						<%@ include file="buttons.jsp" %>
					</div>
					
					<div id="basicCentralPanel">
					
						<div class="basicCentralPanelBar">
							<img src="img/workflow.png">
							<div class="basicCentralPanelBarText">Workflow ${workflow.tag}</div>
						</div>
						
						
						<div id="pannel" style="width:95%; margin:0 auto;margin-top:10px;margin-bottom:60px;">
							<div style="height:60px;">
								<table style="width:100%"> 
									<tr>
										<td class="tableCellFormLeft">Description</td>
										<td class="tableCellFormRight"> 
											${workflow.description} 
										</td>
									</tr>
								
									<tr>
										<td class="tableCellFormLeft">Label</td>
										<td class="tableCellFormRight"> 
											${workflow.tag} 
										</td>
									</tr>

									<tr>
										<td class="tableCellFormLeft">Owner</td>
										<td class="tableCellFormRight"> 
											${workflow.owner.fullName} 
										</td>
									</tr>
									
									<tr>
										<td class="tableCellFormLeft">Experiments</td>
										<td class="tableCellFormRight"> 
											${fn:length(workflow.experiments)}
										</td>
									</tr>
								</table>
							</div>

							<div title="Back to Workflow List" onclick="viewList()" class="basicButton dicas">Back to List</div>
							<c:if test="${fn:length(workflow.activitiesSpecs) != '' }">
								<div title="Create new Experiment" onclick="newExperiment('${workflow.idWorkflow}')" class="basicButton dicas">New Experiment</div>
							</c:if>
							
						</div>

						<div class="basicCentralPanelBar">
							<img src="img/experiment.png">
							<div class="basicCentralPanelBarText">Experiments</div>
						</div>

						<div style="margin : 0 auto; width : 95%; margin-top:10px;" id="dtTableContainer">
							<table class="tableForm"  id="example">
								<thead>
								<tr>
									<th>Experiment</th>
									<th>Created</th>
									<th>Owner</th>
									<th>Last Edit</th>
									<th>Last Run</th>
									<th>Status</th>
									<th>&nbsp;</th>
									
								</tr>
								</thead>
								<tbody>
								<c:forEach var="experiment" items="${workflow.experiments}">
									<tr>
										<td class="tableCellFormRight">${experiment.tagExec}&nbsp;</td>
										<td class="tableCellFormRight">
											<fmt:formatDate type="both" timeStyle="short" value="${experiment.creationDate}"/>&nbsp;
										</td>
										<td class="tableCellFormRight">
											${experiment.owner.loginName}
										</td>
										<td class="tableCellFormRight">
											<fmt:formatDate type="both" timeStyle="short" value="${experiment.alterationDate}"/>&nbsp;
										</td>
										<td class="tableCellFormRight">
											<fmt:formatDate type="both" timeStyle="short" value="${experiment.lastExecutionDate}"/>&nbsp;
										</td>
										<td class="tableCellFormRight">${experiment.status}&nbsp;</td>
										<td class="tableCellFormRight">
											<c:if test="${experiment.status != 'RUNNING'}">
												<img class="miniButton dicas" title="Delete Experiment" onclick="deleteExperiment('${experiment.idExperiment}','${workflow.idWorkflow}')" src="img/delete.png">
											</c:if>
											<img class="miniButton dicas" title="More Details" onclick="viewExperiment('${experiment.idExperiment}')" src="img/search.png">
											<img class="miniButton dicas" title="Manage Activities" onclick="activity('${experiment.idExperiment}')" src="img/family3.png">
											<img class="miniButton dicas" title="Edit Custom Queries" onclick="queries('${experiment.idExperiment}')" src="img/sql.png">
										</td>
									</tr>
								</c:forEach>
								</tbody>
							</table>						
						</div>

					</div>												
					
				</div>
				<div id="rightBox"> 
					<%@ include file="commonpanel.jsp" %>
					<div id="imageCanvas" class="userBoard" style="height:180px"></div>					
				</div>
				
<script>

	function viewImageCanvas() {
		var cyImage = "${workflow.imagePreviewData}";
	    var image = "<img name='compman' style='border-radius:5px;margin:0px;height:100%;width:100%' src='"+cyImage+"' />";
	    $("#imageCanvas").html(image);
	}

	$(document).ready(function() {
		viewImageCanvas();
		
		$('#example').dataTable({
	        "oLanguage": {
	            "sUrl": "js/pt_BR.txt"
	        },	
	        "iDisplayLength" : 10,
			"bLengthChange": false,
			"fnInitComplete": function(oSettings, json) {
				doTableComplete();
			},
			"bAutoWidth": false,
			"sPaginationType": "full_numbers",
			"aoColumns": [ 
						  { "sWidth": "20%" },
						  { "sWidth": "20%" },
						  { "sWidth": "10%" },
						  { "sWidth": "10%" },
						  { "sWidth": "10%" },
						  { "sWidth": "10%" },
						  { "sWidth": "20%" }]						
		} ).fnSort( [[0,'desc']] );
		
	});

	function newExperiment(idWf) {
		window.location.href="doNewExperiment?idWorkflow=" + idWf;
	}

	function viewExperiment(idWf) {
		window.location.href="viewExperiment?idExperiment=" + idWf;
	}

	function deleteExperiment(idExp, idWf) {
		showDialogBox( "This will delete Experiment and all its related data.<br><br>ARE YOU SURE?", "deleteExperiment?idExperiment=" + idExp + "&idWorkflow=" + idWf );
	}
	
	function activity(idWf) {
		window.location.href="editExperiment?idExperiment=" + idWf;
	}
	
	function viewList() {
		window.location.href="indexRedir";
	}
	
	function queries(idExp) {
		window.location.href="viewQueries?idExperiment=" + idExp;
	}

	
</script>
				
<%@ include file="../../footer.jsp" %>
				