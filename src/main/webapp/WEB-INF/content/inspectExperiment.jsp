<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="../../header.jsp" %>

				<div id="leftBox"> 
					<div id="bcbMainButtons" class="basicCentralPanelBar">
						<%@ include file="buttons.jsp" %>
					</div>
					
					<div id="basicCentralPanel">
					
						<div class="basicCentralPanelBar">
							<img src="img/experiment.png">
							<div class="basicCentralPanelBarText">Inspect Experiment ${experiment.tagExec}</div>
						</div>

						<div class="menuBarMain" style="position:relative">
							<img alt="" onclick="back();" title="Back" class="button dicas" src="img/back.png" />
						</div>
												
						<form method="post" action="inspectExperiment" id="frmSave">
							<input type="hidden" id="sqlQuery" name="sql">
							<input type="hidden" name="idExperiment" value="${experiment.idExperiment}">
						</form>						
						
						<div class="basicCentralPanelBar" style="margin-top:20px;">
							<img src="img/right.png">
							<div class="basicCentralPanelBarText">Tables used by this experiment</div>
						</div>
						<div style="margin : 0 auto; width : 95%; margin-bottom:10px;margin-top:10px;display:table"  >
							<table class="tableForm"  id="customTables" >
								<thead>
									<tr>
										<th>Name</th>
										<th>Description</th>
										<th style="width:50px">&nbsp;</th>
									</tr>
								</thead>
								<tbody>
									<c:forEach var="table" items="${customTables}">
										<tr>
											<td>${table.name}</td>
											<td>${table.description}</td>
											<td>
												<img class="miniButton dicas" title="View Data and Schema" onclick="showTable('${table.name}')" src="img/search.png">
											</td>
										</tr>
									</c:forEach>
									<c:forEach var="query" items="${queries}">
										<tr>
											<td style="color:#F90101">Custom Query</td>
											<td style="color:#F90101">${query.name}</td>
											<td>
												<img class="miniButton dicas" title="Execute Query" onclick="executeQuery('${query.idCustomQuery}')" src="img/settings1.png">
											</td>
										</tr>
									</c:forEach>
								</tbody>
							</table>
						</div>

						<div class="basicCentralPanelBar">
							<img src="img/right.png">
							<div class="basicCentralPanelBarText">Fragments</div>
						</div>
						<div style="margin : 10px auto 50px; width : 95%;display:table;" >

							<table class="tableForm"  >
								<tr>
									<th style="width:5%">Order</th>
									<th style="width:5%">Serial</th>
									<th style="width:5%">Instances</th>
									<th>Activities</th>
								</tr>
								<c:forEach var="fragment" items="${experiment.fragments}">
									<tr>
										<td>${fragment.indexOrder}</td>
										<td>${fragment.serial}</td>
										<td>${fragment.totalInstances}</td>
										<td>
											<table id="subActivity" style="width:250px">
											<tr>
												<th style="width:40px">Serial</th>
												<th style="width:90px">Tag</th>
											</tr>
											<c:forEach var="fragActivity" items="${fragment.activities}">
												<tr>
													<td ><a href="showSystemLog?activitySerial=${fragActivity.serial}" target="__BLANK">${fragActivity.serial}</a></td>
													<td >${fragActivity.tag}</td>
												</tr>
											</c:forEach>
											</table>
										</td>
									</tr>
								</c:forEach>
							</table>						

						</div>

					
					</div>												
					
					
				</div>
				<div id="rightBox"> 
					<%@ include file="commonpanel.jsp" %>
				</div>
				
				
<script>

	function viewImageCanvas() {
		var cyImage = "${experiment.imagePreviewData}";
	    var image = "<img name='compman' style='border-radius:5px;margin:0px;height:100%;width:100%' src='"+cyImage+"' />";
	    $("#imageCanvas").html(image);
	}
	
	$(document).ready(function() {
		
		$("#subActivity tr td").css("background-color", "#f6f6f6");
		viewImageCanvas();

		$('#experimentFiles').dataTable({
	        "oLanguage": {
	            "sUrl": "js/pt_BR.txt"
	        },	
			
	        "bLengthChange": false,
			"sPaginationType": "full_numbers",	
			"iDisplayLength" : 30,	
			"bAutoWidth" : false,
			"sScrollX": "100%",
	        "bProcessing": true,
	        "bServerSide": true,
	        "sAjaxSource" : "experimentFilesAjaxProcess?idExperiment=${experiment.idExperiment}", 
	        "sServerMethod": "POST",
	        "aoColumns": [
             			{ "mDataProp": "id_experiment" },
             			{ "mDataProp": "filename" },
             			{ "mDataProp": "activity" },
             			{ "mDataProp": "table" },
	      	        ],
	        
	        "fnServerData": function ( sSource, aoData, fnCallback, oSettings ) {
	        	aoData.push( { "name": "columns", "value": "id_experiment" } );
	        	aoData.push( { "name": "columns", "value": "filename" } );
	        	aoData.push( { "name": "columns", "value": "activity" } );
	        	aoData.push( { "name": "columns", "value": "table" } );
	        	oSettings.jqXHR = $.ajax({
	              "dataType": 'json',
	              "type": "POST",
	              "url": sSource,
	              "data": aoData,
	              "success": fnCallback
	         	}); 
	         },
	         
	        "fnInitComplete": function(oSettings, json) {
	        	doComplete();
	        }
		});	

		
		
		function doComplete() {
			doTableComplete();
			$("#experimentFiles_wrapper .dataTable").each(function() {
				$(this).css("padding-top","0px");
				$(this).css("margin-bottom","0px");
			});
			$(".dataTables_scroll").css("margin-bottom","10px");
			$(".dataTables_scroll").css("padding-top","10px");
		}
		
		
		$('#customTables').dataTable({
	        "oLanguage": {
	            "sUrl": "js/pt_BR.txt"
	        },	
	        "iDisplayLength" : 5,
			"bLengthChange": false,
			"fnInitComplete": function(oSettings, json) {
				this.fnSort( [[0,'asc']] );
				doTableComplete();
			},
			"bAutoWidth": false,
			"sPaginationType": "full_numbers",
			"aoColumns": [ 
						  { "sWidth": "10%" },
						  { "sWidth": "30%" },
						  { "sWidth": "10%" }]						
		});		

		
	});

	function showTable( tblName ) {
		window.location.href="viewTableData?idExperiment=${experiment.idExperiment}&tableName=" + tblName;
	}

	function back() {
		window.location.href="viewExperiment?idExperiment=${experiment.idExperiment}";
	}
	
	function executeQuery( idQuery ) {
		window.location.href="executeQuery?idQuery=" + idQuery;
	}

</script>				
				
<%@ include file="../../footer.jsp" %>
				