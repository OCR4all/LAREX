<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="sidebar-text" class="col s12 hide">

	<div class="virtual-keyboard infocus"> </div>

	<a class="col s12 waves-effect waves-light btn exportPageXML tooltipped" data-position="left" data-delay="50" data-tooltip="Save the current segmentation as PageXML (Shortcut: CTRL+S)">
		Save Result
		<div class="progress hide">
    		<div class="indeterminate"></div>
		</div>       
		<span class="pageXMLVersion">version</span>
		<i class="material-icons right">file_download</i>
		<div class="dropDownPageXMLCorner"></div>
	</a>

	<div href="#!" class='dropdown-button dropDownPageXML' data-activates='dropdownPageXMLVersion2'></div>
	<ul id='dropdownPageXMLVersion2' class='dropdown-content'>
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