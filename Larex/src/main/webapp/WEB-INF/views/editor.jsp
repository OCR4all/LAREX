<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>
<t:webpage>
	<t:head>
		<!-- Load the Paper.js library -->
		<script type="text/javascript" src="resources/js/paper.js"></script>

		<!-- Viewer scripts -->
		<script type="text/javascript" src="resources/js/viewer/actions.js"></script>
		<script type="text/javascript"
			src="resources/js/viewer/navigationcontroller.js"></script>
		<script type="text/javascript" src="resources/js/viewer/guiInput.js"></script>
		<script type="text/javascript"
			src="resources/js/viewer/viewerInput.js"></script>
		<script type="text/javascript" src="resources/js/viewer/keyInput.js"></script>
		<script type="text/javascript" src="resources/js/viewer/communicator.js"></script>
		<script type="text/javascript" src="resources/js/viewer/gui.js"></script>
		<script type="text/javascript" src="resources/js/viewer/viewer.js"
			canvas="viewerCanvas"></script>
		<script type="text/javascript" src="resources/js/viewer/editor.js"
			canvas="viewerCanvas"></script>
		<script type="text/javascript" src="resources/js/viewer/selector.js"></script>
		<script type="text/javascript" src="resources/js/viewer/controller.js"></script>

		<!-- Main Method -->
		<script>
		var colors = [	
						new paper.Color(0,1,0),
						new paper.Color(1,0,0),
						new paper.Color(1,1,0),
						new paper.Color(0,1,1),
						new paper.Color(0,0,0),
						new paper.Color(0.5,0,0),
						new paper.Color(0.5,0.5,0.5),
						new paper.Color(1,0,1),
						new paper.Color(0.9,0.6,1),
						new paper.Color(0.5,0,0.55),
						new paper.Color(0.25,0.51,1),
						new paper.Color(0,0,0.5),
						new paper.Color(0,0.5,0.5),
						new paper.Color(0,0.6,0),
						new paper.Color(1,0.95,0.7),
						new paper.Color(0.8,0.6,0),
						new paper.Color(0.7,0.45,0.2),
						new paper.Color(0.5,0,0),
						new paper.Color(0.4,0,0.55)];
			
		var globalSettings ={
			downloadPage:${globalSettings.getSetting("websave").equals("") ? true : globalSettings.getSetting("websave")}
		}
		
		//specify specific colors
		var specifiedColors = {
				image: colors[0],
				paragraph: colors[1],
				marginalia: colors[2],
				page_number: colors[3],
				ignore: colors[4]
		};
		var controller = new Controller(${book.getId()},'viewerCanvas',specifiedColors,colors,globalSettings);
		$(document).ready(function() {
			$(".button-collapse").sideNav();
		    $('select').material_select();
		});
		</script>

		<link rel="stylesheet" href="resources/css/viewer.css">
		<title>Larex - Editor</title>
	</t:head>

	<body>
		<div id="menu" class="grey lighten-4">
			<div class="mainMenu">
				<ul class="tabs">
					<li class="tab"><a href="#file">File</a></li>
					<li class="tab"><a href="#nav">Navigation</a></li>
					<li class="tab"><a class="active" href="#edit">Edit</a></li>
				</ul>
			</div>
			<div class="secondMenu">
				<div id="file" class="">
					<div class="">
						<t:menuIcon url="${pageContext.request.contextPath}/" jsClass="open" icon="folder_open"
							tooltip="Open a different book">Open</t:menuIcon>
						<t:menuIcon url="" jsClass="reload" icon="autorenew"
							tooltip="Reload the current book">Reload</t:menuIcon>
						<t:menuIcon url="" jsClass="exportPageXML" icon="code"
							tooltip="Export as PageXML">PageXML Export</t:menuIcon>
					</div>
				</div>
				<div id="nav" class="">
					<div class="">
						<t:menuIconCategory name="Move" >
							<t:menuIcon jsClass="moveup" icon="keyboard_arrow_up"
								tooltip="Moves the image up (Shortcut: arrow up or drag image)">Up</t:menuIcon>
							<t:menuIcon jsClass="movedown" icon="keyboard_arrow_down"
								tooltip="Moves the image down (Shortcut: arrow down or drag image)">Down</t:menuIcon>
							<t:menuIcon jsClass="moveleft" icon="keyboard_arrow_left"
								tooltip="Moves the image left (Shortcut: arrow left or drag image)">Left</t:menuIcon>
							<t:menuIcon jsClass="moveright" icon="keyboard_arrow_right"
								tooltip="Moves the image right (Shortcut: arrow right or drag image)">Right</t:menuIcon>
						</t:menuIconCategory>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="movecenter" icon="crop_free"
							tooltip="Centers the images position (Shortcut: space)">Center</t:menuIcon>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="zoomout" icon="zoom_out"
							tooltip="Zoom the image out (Shortcut: - or scroll wheel)">Zoom out</t:menuIcon>
						<div class="menuTextIcon">
							<span class="zoomvalue">100.00</span>%
						</div>
						<t:menuIcon jsClass="zoomin" icon="zoom_in"
							tooltip="Zoom the image in (Shortcut: + or scroll wheel)">Zoom in</t:menuIcon>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="zoomfit" icon="zoom_out_map"
							tooltip="Zooms image to fit the screen (Shortcut: space)">Zoom fit</t:menuIcon>
					</div>
				</div>
				<div id="edit" class="">
					<div class="">
						<t:menuIconCategory name="RoI" >
							<t:menuIcon jsClass="setRegionOfInterest" icon="video_label"
								tooltip="Set the Region of Interest (RoI)">RoI</t:menuIcon>
							<t:menuIcon jsClass="createIgnore" icon="layers_clear"
								tooltip="Create a ignore rectangle">Ignore</t:menuIcon> 
						</t:menuIconCategory>
						<t:menuIconCategory name="Region" >
							<t:menuIcon jsClass="createRegionRectangle" icon="crop_5_4"
								tooltip="Create a region rectangle (Shortcut: 1)">Rectangle</t:menuIcon>
							<t:menuIcon jsClass="createRegionBorder" icon="border_left"
								tooltip="Create a region border (Shortcut: 2)">Border</t:menuIcon>
						</t:menuIconCategory>
						<div class="menuIconDivider col"></div>
						<t:menuIconCategory name="Segment" >
							<t:menuIcon jsClass="createSegmentRectangle" icon="crop_5_4"
								tooltip="Create a fixed segment rectangle (Shortcut: 3)">Rectangle</t:menuIcon>
							<t:menuIcon jsClass="createSegmentPolygon" icon="star_border"
								tooltip="Create a fixed segment polygon. Back to start or double click to end (Shortcut: 4)">Polygon</t:menuIcon>
						</t:menuIconCategory>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="createCut" icon="content_cut"
							tooltip="Create a cut line. Double click to end (Shortcut: 5)">Line</t:menuIcon>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="undo" icon="undo"
							tooltip="Undo: Revokes the last action (Shortcut: ctrl+z)">Undo</t:menuIcon>
						<t:menuIcon jsClass="redo" icon="redo"
							tooltip="Redo: Executes the most recent undone action (Shortcut: ctrl+y)">Redo</t:menuIcon>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="editPoints" icon="linear_scale"
							tooltip="Switch between editing points and polygons">Points</t:menuIcon>
						<div class="menuIconDivider col"></div>
						<t:menuIcon jsClass="deleteSelected" icon="delete"
							tooltip="Delete selected items (Shortcut: DEL)">Delete</t:menuIcon>
						<t:menuIcon jsClass="moveSelected" icon="open_with"
							tooltip="Move selected items (Shortcut: M)">Move</t:menuIcon>
						<t:menuIcon jsClass="scaleSelected" icon="photo_size_select_small"
							tooltip="Scale selected items (Shortcut: S)">Scale</t:menuIcon>
						<t:menuIcon jsClass="combineSelected" icon="add_circle"
							tooltip="Combine selected Segments (Shortcut: C)">Combine</t:menuIcon>
						<t:menuIcon jsClass="fixSelected" icon="lock"
							tooltip="Fix/unfix Segments (Shortcut: F)">Fix</t:menuIcon>
						<div class="menuIconDivider col"></div>
						<t:menuIconCategory name="Order" jsClass="readingOrderCategory">
							<t:menuIcon jsClass="createReadingOrder" icon="timeline"
								tooltip="Set a reading order">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="saveReadingOrder hide" icon="save"
								tooltip="Save the current reading order (Shortcut: right click)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="autoGenerateReadingOrder" icon="subject"
								tooltip="Auto generate a reading order">readingOrder</t:menuIcon> 
						</t:menuIconCategory>
					</div>
				</div>
			</div>
		</div>

		<div id="viewerRwapper" class="row">
			<div class="sidebar col s3 m1 l1">
				<t:booksidebar/>
			</div>
			<div id="viewer" class="col s5 m9 l9">
				<canvas id="viewerCanvas" class="grey darken-1" resize="true"></canvas>
			</div>

			<div class="sidebar col s4 m2 l2">
			<t:sidebar/>
			</div>
		</div>

		<t:preloader/>
		<t:regionsettings/>
		<t:contextmenu/>
	</body>
</t:webpage>
