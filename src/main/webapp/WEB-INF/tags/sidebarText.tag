<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>

<div id="sidebar-text" class="col s12 hide">

	<div class="virtual-keyboard-settings row infocus">
		<a class="btn col s4 waves-effect waves-light tooltipped vk-upload"
			data-position="left" data-delay="50" data-tooltip="Load Virtual Keyboard configuration from file"
			onclick="$('#upload-vk-input').click()">
			Load
			<i class="material-icons right">file_upload</i>
		</a>
		<input id="upload-vk-input" class="upload-virtual-keyboard hide" type="file">
		<a class="btn col s4 tooltipped vk-download" data-position="left" data-delay="50" data-tooltip="Save Virtual Keyboard to file">
			Save <i class="material-icons right"> file_download </i>
		</a>
		<a class='btn col s4 waves-effect waves-light tooltipped vk-preset dropdown-button' href='#' data-position="left" data-delay="50" data-tooltip="Use predefined virtual keyboard"
		   data-activates='vk-preset-dropdown'>Preset <i class="material-icons right">arrow_drop_down</i></a>
		<ul id='vk-preset-dropdown' class='dropdown-content'>
			<li><a href="#!" class="vk-preset-entry" data-language="default">Default</a></li>
			<li><a href="#!" class="vk-preset-entry" data-language="latin_no_pua">Latin (no PUA)</a></li>
			<li><a href="#!" class="vk-preset-entry" data-language="old_greek">Old Greek</a></li>

			<li><a href="#vk-preset-modal" class="vk-preset-info modal-trigger"><i class="material-icons left">info</i>Info</a></li>
		</ul>
	</div>
	<div class="virtual-keyboard infocus"> </div>
	<div class="virtual-keyboard-tools row infocus">
		<a class="btn col s4 waves-effect waves-light tooltipped vk-add" data-position="left" data-delay="50" data-tooltip="Add new buttons to the virtual keyboard">
			<i class="material-icons"> add </i>
		</a>	
		<a class="btn col s4 tooltipped vk-delete draggable" data-position="left" data-delay="50" data-tooltip="Drop virtual keyboard buttons here to delete them (Only possible if virtual keyboard is unlocked)">
			<i class="material-icons"> delete </i>
		</a>	
		<a class="btn col s4 waves-effect waves-light tooltipped hide vk-lock" data-position="left" data-delay="50" data-tooltip="Lock buttons of the virtual keyboard in place">
			<i class="material-icons"> lock_open </i>
		</a>	
		<a class="btn col s4 waves-effect waves-light tooltipped vk-unlock" data-position="left" data-delay="50" data-tooltip="Unlock buttons of the virtual keyboard in order to move them">
			<i class="material-icons"> lock </i>
		</a>	
	</div>
	<ul class="collapsible">
		<li>
			<div class="collapsible-header"><i class="material-icons">settings</i>Settings</div>
			<div class="collapsible-body">
				<div id="textMode-options">
					<input type="checkbox" id="displayPrediction"/>
					<label for="displayPrediction">Display Prediction</label>
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
