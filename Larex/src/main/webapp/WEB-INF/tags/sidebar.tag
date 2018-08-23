<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div class="col s12">
	<ul class="collapsible row" data-collapsible="accordion">
		<li>
			<div class="collapsible-header">
				<i class="material-icons">import_export</i>
				<span>
					Settings
				</span>
			</div>
			<div id="import-export-settings" class="collapsible-body">
				<a class="col s12 waves-effect waves-light btn saveSettingsXML">
					Save Settings
					<div class="progress hide">
			    		<div class="indeterminate"></div>
					</div>       
					<i class="material-icons right">file_download</i>
				</a>

				<form action="#">
					<div class="btn col s12" onclick="$('#upload-input').click()">
						Load Settings
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
					<a class="col s10 offset-s1 waves-effect waves-light btn loadExistingSegmentation tooltipped" data-position="bottom" data-delay="50" data-tooltip="Load existing segmentations if avaiable">Load now</a>
				</div>
			</div>
		</li>
		<li>
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
						<c:forEach var="type" items="${segmenttypes}">
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
			</div>
			<div class="collapsible-body">
				<div class="reading-order">
				  	<ul id="reading-order-list" class="collection">
				  	</ul>
			  	</div>
			</div>
		</li>
		<li>
			<div class="collapsible-header active">
				<i class="material-icons">settings</i>Parameters
			</div>
			<div class="collapsible-body">
				<div id="parameter-settings">
					<div class="hide">
						<p class="settings-header settings-input" >Binary Thresh</p>
						<p class="settings-input">
							<input value="" id="binarythreash" class="input-number"
								type="number" class="validate" size="4" />
						</p>
					</div>
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
								<c:forEach var="type" items="${imageSegTypes}">
									<option value="${type.key}">${type.value}</option>
								</c:forEach>
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
		<span id="pageXMLVersion">version</span>
		<i class="material-icons right">file_download</i>
		<div id="dropDownPageXMLCorner"></div>
	</a>
	
	<div id="dropDownPageXML" href="#!"class='dropdown-button' data-activates='dropdownPageXMLVersion'></div>
	<ul id='dropdownPageXMLVersion' class='dropdown-content'>
		<li><a class="pageXMLVersion" data-version="2017-07-15">2017-07-15</a></li>
		<li><a class="pageXMLVersion" data-version="2010-03-19">2010-03-19</a></li>
	</ul>
	<form action="#">
	<div class="col s12 waves-effect waves-light btn tooltipped" onclick="$('#upload-segmentation-input').click()" data-position="left" data-delay="50" data-tooltip="Supports PageXML v2010-03-19 and v2013-07-15">
		Load Result
		<i class="material-icons right">file_upload</i></div>
		<input id="upload-segmentation-input" class="uploadSegmentation hide" type="file">
	</form>
</div>