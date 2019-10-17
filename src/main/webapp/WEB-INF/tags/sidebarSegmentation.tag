<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="sidebar-segment" class="col s12 hide">
	<ul class="collapsible row" data-collapsible="accordion">
		<li id="collapsible-settings">
			<div class="collapsible-header">
				<i class="material-icons">import_export</i>
				<span>
					Settings
				</span>
			</div>
			<div id="import-export-settings" class="collapsible-body">
				<a class="col s6 waves-effect waves-light btn saveSettingsXML">
					Save
					<div class="progress hide">
			    		<div class="indeterminate"></div>
					</div>       
					<i class="material-icons right">file_download</i>
				</a>

				<form action="#">
					<div class="btn col s6" onclick="$('#upload-input').click()">
						Load
						<i class="material-icons right">file_upload</i></div>
					<input id="upload-input" class="uploadSettings hide" type="file">
				</form>

				<div class="row">
					<div class="col s12">
						<span class="settings-header">Advanced Settings</span>
					</div>
					<div class="col s12">
						<span class="settings-load-existing-xml switch tooltipped" data-position="bottom" data-delay="50" data-tooltip="Automatically load existing segmentations on start if avaiable"> 
							<label>
								<input type="checkbox" checked="checked"> 
									<span class="lever"></span>
								Load existing segmentations
							</label>
						</span>	
					</div>
					<a class="col s10 offset-s1 waves-effect waves-light btn loadExistingSegmentation tooltipped" data-position="bottom" data-delay="50" data-tooltip="Load existing segmentations if available">Load now</a>
					<div class="col s12">
						<span class="settings-autosegment switch tooltipped" data-position="bottom" data-delay="50" data-tooltip="Automatically segment unsegmented pages when opened."> 
							<label>
								<input type="checkbox" checked="checked"> 
									<span class="lever"></span>
								Auto segment page 
							</label>
						</span>	
					</div>
				</div>
			</div>
		</li>
		<li id="collapsible-region">
			<div class="collapsible-header">
				<i class="material-icons">dashboard</i>
				<span>
					Regions
				</span>
			</div>
			<div id="sidebarRegions" class="collapsible-body">
				<div class="settings-regions">
					<ul class="collection">
						<li class="regionlegendAll collection-item">
							--Show all--
							<span class="secondary-content switch"> 
								<label><input type="checkbox"> 
									<span class="lever"></span>
								</label>
							</span>	
						</li>
						<c:forEach var="type" items="${regionTypes}">
							<li class="regionlegend collection-item" data-type="${type.key}">
								<span class="regionSettings infocus" data-type="${type.key}">
									<div class="legendicon ${type.key}"></div>${type.key}
								</span>
								
								<span class="secondary-content ">
									<span class="switch">
										<label><input type="checkbox"> 
											<span class="lever"></span>
										</label>
									</span>
								</span>
							</li>
						</c:forEach>
						<li class="collection-item regionCreate infocus">
							<span>
								--Create--
							</span>
						</li>
					</ul>
				</div>
			</div>
		</li>
		<li>
			<div id="reading-order-header" class="collapsible-header">
				<i class="material-icons">reorder</i>Reading Order
				<div class="collapsible-setting"><i class="delete-reading-order material-icons">delete</i></div>
			</div>
			<div class="collapsible-body">
				<div class="reading-order">
				  	<ul id="reading-order-list" class="collection">
				  	</ul>
			  	</div>
			</div>
		</li>
		<li id="collapsible-parameters">
			<div class="collapsible-header active">
				<i class="material-icons">settings</i>Parameters
			</div>
			<div class="collapsible-body">
				<div id="parameter-settings">
					<div>
						<p class="settings-header settings-input">Text Dilation</p>
						<p class="settings-input">
							<input value="" id="textdilationX" class="input-number textdilationX" type="number" min="0" class="validate" /> :
							<input value="" id="textdilationY" class="input-number textdilationY" type="number" min="0" class="validate" />
						</p>
					</div>
					<div>
						<p class="settings-header settings-input">Image Dilation</p>
						<p class="settings-input">
							<input value="" id="imagedilationX"
								class="input-number imagedilationX" type="number" min="0" class="validate" />
							: <input value="" id="imagedilationY"
								class="input-number imagedilationY" type="number" min="0" class="validate" />
						</p>
					</div>
					<div class="settings-input settings-imagesegmentation">
						<p class="settings-header settings-input" >Image Segmentation</p>
						<div class="input-field settings-input">
							<select class="settings-image-mode">
								<option value="NONE">None</option>
								<option value="CONTOUR_ONLY">Contour only</option>
								<option value="STRAIGHT_RECT">Straight rectangle</option>
								<option value="ROTATED_RECT">Rotated rectangle</option>
							</select>
						</div>
						<div class="">
							<span class="settings-combine-image switch"> 
								<label>
									combine
									<input type="checkbox"> 
									<span class="lever"></span>
								</label>
							</span>	
						</div>
					</div>
				</div>
			</div>
		</li>
	</ul>
	<a class="col s12 waves-effect waves-light btn doSegment tooltipped" data-position="left" data-delay="50" data-tooltip="Segment the current page automatically (Shortcut: CTRL+Space)">Segment</a>
	
	<a class="col s12 waves-effect waves-light btn exportPageXML tooltipped" data-position="left" data-delay="50" data-tooltip="Save the current segmentation as PageXML (Shortcut: CTRL+S)">
		Save Result
		<div class="progress hide">
    		<div class="indeterminate"></div>
		</div>       
		<span class="pageXMLVersion">version</span>
		<i class="material-icons right">file_download</i>
		<div class="dropDownPageXMLCorner"></div>
	</a>
	
	<div href="#!"class='dropdown-button dropDownPageXML' data-activates='dropdownPageXMLVersion0'></div>
	<ul id='dropdownPageXMLVersion0' class='dropdown-content'>
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