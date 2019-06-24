<%@tag description="Menu Icon" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>

<div class="">
	<t:menuIcon url="${pageContext.request.contextPath}/" jsClass="open" icon="folder_open"
		tooltip="Open a different book">Open</t:menuIcon>
	<div class="menuIconDivider col"></div>
	<t:menuIconCategory name="Navigation">
		<t:menuIcon jsClass="zoomout" icon="zoom_out"
			tooltip="Zoom the image out (Shortcut: - or scroll wheel)">Zoom out</t:menuIcon>
		<div class="menuTextIcon">
			<span class="zoomvalue">100.00</span>%
		</div>
		<t:menuIcon jsClass="zoomin" icon="zoom_in"
			tooltip="Zoom the image in (Shortcut: + or scroll wheel)">Zoom in</t:menuIcon>
		<t:menuIcon jsClass="zoomfit" icon="zoom_out_map"
			tooltip="Zooms image to fit the screen (Shortcut: space)">Zoom fit</t:menuIcon>
	</t:menuIconCategory>
	<t:menuIcon jsClass="undo" icon="undo"
		tooltip="Undo: Revokes the last action (Shortcut: ctrl+z)">Undo</t:menuIcon>
	<t:menuIcon jsClass="redo" icon="redo"
		tooltip="Redo: Executes the most recent undone action (Shortcut: ctrl+y)">Redo</t:menuIcon>
	<div class="menuIconDivider col"></div>
	<t:menuIcon jsClass="deleteSelected" icon="delete"
		tooltip="Delete selected items (Shortcut: DEL)">Delete</t:menuIcon>
	<div class="menuIconDivider col"></div>
</div>