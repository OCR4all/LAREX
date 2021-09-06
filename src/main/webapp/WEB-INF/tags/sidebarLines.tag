<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="sidebar-lines" class="col s12 hide">

	<div class="card legend">
		<h5>
			Legend
		</h5>
		<ul class="legend-regions">
			<c:forEach var="type" items="${regionTypes}">
				<li class="regionlegend" data-type="${type.key}">
					<span>
						<div class="legendicon ${type.key}"></div>${type.key}
					</span>
				</li>
			</c:forEach>
		</ul>
	</div>
	<ul class="collapsible row infocus" data-collapsible="accordion">
		<li>
			<div id="reading-order-header-lines" class="collapsible-header">
				<i class="material-icons">reorder</i>Reading Order
				<div class="collapsible-setting"><i class="delete-reading-order material-icons">delete</i></div>
			</div>
			<div class="collapsible-body">
				<div class="reading-order">
				  	<ul id="reading-order-list-lines" class="collection">
				  	</ul>
			  	</div>
			</div>
		</li>
		<li id="collapsible-actions">
			<div class="collapsible-header active">
				<i class="material-icons">adjust</i>Actions
			</div>
			<div class="collapsible-body">
				<div class="segmentationToggle row">
					<span class="col s12 switch">
						<label>
							<input id="toggleLineVisibility" type="checkbox">
							<span class="lever"></span>
							Hide existing line polygons
						</label>
					</span>
				</div>
				<div class="segmentationToggle row">
					<span class="col s12 switch">
						<label>
							<input id="toggleBaselineVisibility" type="checkbox">
							<span class="lever"></span>
							Hide existing baselines
						</label>
					</span>
				</div>
			</div>
		</li>
	</ul>

	<a class="col s12 waves-effect waves-light btn exportPageXML tooltipped" data-position="left" data-delay="50" data-tooltip="Save the current segmentation as PageXML (Shortcut: CTRL+S)">
		Save Result
		<div class="progress hide">
    		<div class="indeterminate"></div>
		</div>
		<span class="pageXMLVersion">version</span>
		<i class="material-icons right">file_download</i>
		<div class="dropDownPageXMLCorner"></div>
	</a>

	<div href="#!" class='dropdown-button dropDownPageXML' data-activates='dropdownPageXMLVersion1'></div>
	<ul id='dropdownPageXMLVersion1' class='dropdown-content'>
		<li><a class="pageXMLVersionSelect" data-version="2019-07-15">2019-07-15</a></li>
		<li><a class="pageXMLVersionSelect" data-version="2017-07-15">2017-07-15</a></li>
		<li><a class="pageXMLVersionSelect" data-version="2010-03-19">2010-03-19</a></li>
	</ul>
	<form action="#">
	<div class="col s12 waves-effect waves-light btn tooltipped" onclick="$('#upload-segmentation-input').click()" data-position="left" data-delay="50" data-tooltip="Supports PageXML v2010-03-19 and v2013-07-15">
		Load Result
		<i class="material-icons right">file_upload</i></div>
		<input id="upload-segmentation-input" class="uploadSegmentation hide" type="file">
	</form>


</div>
