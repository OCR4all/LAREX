<%@tag description="Main Body Tag" pageEncoding="UTF-8"%>


<div id="regioneditor" class="card hide infocus">
	<div id="regioneditor-settings">
		<div id="regionTypeLegendIcon" class="legendicon"></div><span id="regionType">Test</span>
		<div id="regioneditorSelect" class="select-regions hide">
			<ul class="collection highlight">
				<c:forEach var="type" items="${regionTypes}">
					<li class="collection-item regioneditorSelectItem" data-type="${type.key}">
						${type.key}
					</li>
				</c:forEach>
			</ul>
		</div>
		
		<div class="col s12 regionSetting">
			<span class="settings-input">minSize</span>
			<span class="settings-input">
				<input value="" id="regionMinSize" class="input-number"
					type="number" class="validate" size="4" />
			</span>
		</div>
		<div class="col s12 regionSetting ">
			<span class="settings-input hide">maxOccurence</span>
			<span class="settings-input hide">
				<input value="" id="regionMaxOccurances" class="input-number"
					type="number" class="validate" size="4" />
			</span>
		</div>
		<div class="col s12 regionSetting regionColorSettings">
			<div class="legendicon regionColorIcon"></div>
			<span id="regionColor" data-color="">color <i class="material-icons">arrow_drop_down</i></span>
			<div id="regioneditorColorSelect" class="hide">
				<ul class="matrixCollection collection highlight">
				</ul>
			</div>
		</div>
	</div>
	<a href="#!" id="regioneditorSave"
		class="waves-effect waves-green btn-flat">Save</a>
	<a href="#!" id="regioneditorCancel"
			class="waves-effect waves-green btn-flat">Cancel</a>
	<a href="#!" id="regionDelete"
		class="regionDelete waves-effect waves-green btn-flat"><i class="material-icons">delete</i></a>
</div>