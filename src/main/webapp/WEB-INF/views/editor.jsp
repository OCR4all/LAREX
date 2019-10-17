<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="b" tagdir="/WEB-INF/tags/base"%>
<b:webpage>
	<b:head>
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
		<script type="text/javascript" src="resources/js/viewer/colors.js"></script>
		<script type="text/javascript" src="resources/js/viewer/viewer.js" 
			canvas="viewerCanvas"></script>
		<script type="text/javascript" src="resources/js/viewer/editor.js"
			canvas="viewerCanvas"></script>
		<script type="text/javascript" src="resources/js/viewer/textViewer.js"></script>
		<script type="text/javascript" src="resources/js/viewer/selector.js"></script>
		<script type="text/javascript" src="resources/js/viewer/controller.js"></script>

		<!-- Main Method -->
		<script>
		const colors = [	
						new paper.Color(0,1,0),
						new paper.Color(1,0,0),
						new paper.Color(1,1,0),
						new paper.Color(0,1,1),
						new paper.Color(0,0,0),
						new paper.Color(0.5,0,0),
						new paper.Color(0,0.5,0),
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
						new paper.Color(0.4,0,0.55)];
			
		let globalSettings ={
			downloadPage:${globalSettings.getSetting("websave").equals("") ? true : globalSettings.getSetting("websave")}
		}
		
		//specify specific colors
		let specifiedColors = {
				ImageRegion: 0,
				paragraph: 1,
				marginalia: 2,
				page_number: 3,
				ignore: 4,
				TextLine: 5,
				TextLine_gt: 6,
		};
		const accessible_modes = ['${globalSettings.getSetting("modes").trim().replace(" ","','")}']
		let controller = new Controller(${book.getId()},accessible_modes,'viewerCanvas',specifiedColors,colors,globalSettings);
		$(document).ready(function() {
			$(".button-collapse").sideNav();
		    $('select').material_select();
		});
		</script>

		<link rel="stylesheet" href="resources/css/viewer.css">
		<title>Larex - Editor</title>
	</b:head>

	<body>
		<div id="menu" class="grey lighten-4">
			<div class="mainMenu">
				<ul class="tabs">
					<li class="tab mode mode-segment hide infocus" data-mode="segment"><a href="#segment_tab">Segments</a></li>
					<li class="tab mode mode-edit hide infocus" data-mode="edit"><a href="#edit_tab">Segments</a></li>
					<li class="tab mode mode-lines hide infocus" data-mode="lines"><a href="#line_tab">Lines</a></li>
					<li class="tab mode mode-text hide infocus" data-mode="text"><a href="#text_tab">Text</a></li>
				</ul>

  			</div>
			<div class="secondMenu">
				<div id="segment_tab">
					<b:baseMenu/>
					<div class="">
						<t:menuIconCategory name="RoI" jsClass="menu-roi">
							<t:menuIcon jsClass="setRegionOfInterest" icon="video_label"
								tooltip="Set the Region of Interest (RoI)">RoI</t:menuIcon>
							<t:menuIcon jsClass="createIgnore" icon="layers_clear"
								tooltip="Create a ignore rectangle">Ignore</t:menuIcon> 
						</t:menuIconCategory>
						<t:menuIconCategory name="Area" jsClass="menu-region">
							<t:menuIcon jsClass="createRegionAreaRectangle" icon="crop_5_4"
								tooltip="Create an area rectangle in which to find regions (Shortcut: 1)">Rectangle</t:menuIcon>
							<t:menuIcon jsClass="createRegionAreaBorder" icon="border_left"
								tooltip="Create an area border in which to find regions (Shortcut: 2)">Border</t:menuIcon>
						</t:menuIconCategory>
						<div class="menuIconDivider col"></div>
						<t:menuIconCategory name="Region" >
							<t:menuIcon jsClass="createSegmentRectangle" icon="crop_5_4"
								tooltip="Create a fixed region rectangle (Shortcut: 3)">Rectangle</t:menuIcon>
							<t:menuIcon jsClass="createSegmentPolygon" icon="star_border"
								tooltip="Create a fixed region polygon. Back to start or double click to end (Shortcut: 4)">Polygon</t:menuIcon>
							<t:menuIcon jsClass="createCut cutPolygon" icon="content_cut"
								tooltip="Create a cut line that forces the segmentation algorithm to split regions. Double click to end (Shortcut: 5)">Line</t:menuIcon>
						<t:menuIcon jsClass="editContours" icon="font_download"
							tooltip="Select contours to combine (with 'C') to regions (see function combine). (Shortcut: 6)">Contours</t:menuIcon>
							<t:menuIcon jsClass="combineSelected" icon="add_circle"
								tooltip="Combine selected regions or contours (Shortcut: C)">Combine</t:menuIcon>
							<t:menuIcon jsClass="fixSelected" icon="lock"
								tooltip="Fix/unfix region, for it to persist a new segmentation. (Shortcut: F)">Fix</t:menuIcon>
						</t:menuIconCategory>
						<t:menuIconCategory name="Order" jsClass="readingOrderCategory">
							<t:menuIcon jsClass="addToReadingOrder" icon="playlist_add"
								tooltip="Add a region to the reading order. (Shortcut: R)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="editReadingOrder" icon="timeline"
								tooltip="Add multiple regions to the reading order. Add with leftclick and end with rightclick, clicking the button again or ESC. (Shortcut: CTRL+R)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="autoGenerateReadingOrder" icon="subject"
								tooltip="Auto generate a reading order">readingOrder</t:menuIcon> 
						</t:menuIconCategory>
					</div>

				</div>
				<div id="edit_tab">
					<b:baseMenu/>
					<div class="">
						<t:menuIconCategory name="Region" >
							<t:menuIcon jsClass="createSegmentRectangle" icon="crop_5_4"
								tooltip="Create a fixed region rectangle (Shortcut: 3)">Rectangle</t:menuIcon>
							<t:menuIcon jsClass="createSegmentPolygon" icon="star_border"
								tooltip="Create a fixed region polygon. Back to start or double click to end (Shortcut: 4)">Polygon</t:menuIcon>
							<t:menuIcon jsClass="createCut cutPolygon" icon="content_cut"
								tooltip="Create a cut line that forces the segmentation algorithm to split regions. Double click to end (Shortcut: 5)">Line</t:menuIcon>
						<t:menuIcon jsClass="editContours" icon="font_download"
							tooltip="Select contours to combine (with 'C') to regions (see function combine). (Shortcut: 6)">Contours</t:menuIcon>
							<t:menuIcon jsClass="combineSelected" icon="add_circle"
								tooltip="Combine selected regions or contours (Shortcut: C)">Combine</t:menuIcon>
						</t:menuIconCategory>
						<t:menuIconCategory name="Order" jsClass="readingOrderCategory">
							<t:menuIcon jsClass="addToReadingOrder" icon="playlist_add"
								tooltip="Add a regions to the reading order. (Shortcut: R)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="editReadingOrder" icon="timeline"
								tooltip="Add multiple regions to the reading order. Add with leftclick and end with rightclick, clicking the button again or ESC. (Shortcut: CTRL+R)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="autoGenerateReadingOrder" icon="subject"
								tooltip="Auto generate a reading order">readingOrder</t:menuIcon> 
						</t:menuIconCategory>
					</div>

				</div>
				<div id="line_tab">
					<b:baseMenu/>
					<div class="">
						<t:menuIconCategory name="Lines" >
							<t:menuIcon jsClass="createTextLineRectangle" icon="crop_5_4"
								tooltip="Create a fixed textline rectangle (Shortcut: 3)">Rectangle</t:menuIcon>
							<t:menuIcon jsClass="createTextLinePolygon" icon="star_border"
								tooltip="Create a fixed textline polygon. Back to start or double click to end (Shortcut: 4)">Polygon</t:menuIcon>
						<t:menuIcon jsClass="editContours" icon="font_download"
							tooltip="Select contours to combine (with 'C') to textlines (see function combine). (Shortcut: 6)">Contours</t:menuIcon>
						<t:menuIcon jsClass="combineSelected" icon="add_circle"
							tooltip="Combine selected textlines or contours (Shortcut: C)">Combine</t:menuIcon>
						</t:menuIconCategory>
						<div class="menuIconDivider col"></div>
						<t:menuIconCategory name="Order" jsClass="readingOrderCategory">
							<t:menuIcon jsClass="addToReadingOrder" icon="playlist_add"
								tooltip="Add a textline to the reading order inside a region (Shortcut: R)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="editReadingOrder" icon="timeline"
								tooltip="Add multiple textlines to the reading order inside a region. Add with leftclick and end with rightclick, clicking the button again or ESC. (Shortcut: CTRL+R)">readingOrder</t:menuIcon>
							<t:menuIcon jsClass="autoGenerateReadingOrder hide" icon="subject"
								tooltip="Auto generate a reading order">readingOrder</t:menuIcon> 
						</t:menuIconCategory>
					</div>

				</div>
				<div id="text_tab">
					<b:baseMenu/>
					<t:menuIcon jsClass="displayTextView" icon="subject"
						tooltip="Switch to Text View.">textView</t:menuIcon> 
					<t:menuIcon jsClass="hideTextView hide" icon="description"
						tooltip="Switch to Page View.">pageView</t:menuIcon> 
				</div>
			</div>
		</div>

		<div id="viewerRwapper" class="row">
			<div class="sidebar col s3 m1 l1">
				<t:sidebarNavigation/>
			</div>
			<div id="viewer" class="col s5 m9 l9">
				<canvas id="viewerCanvas" class="grey darken-1" resize="true"></canvas>
				<span id="textline-viewer-buffer"></span>
				<div id="viewerText" class="hide">
					<div id="viewerTextContainer"></div>
				</div>
			</div>

			<div class="sidebar col s4 m2 l2">
			<t:sidebarSegmentation/>
			<t:sidebarLines/>
			<t:sidebarText/>
			</div>
		</div>

		<b:preloader/>
		<t:regionSettings/>
		<t:contextmenu/>
		<t:virtualKeyboardAdd/>
		<div id="textline-content" class="hide infocus">
			<input id="textline-text" type="text">			
			<span id="textline-buffer" class=""></span>
		</div>
	</body>
</b:webpage>
