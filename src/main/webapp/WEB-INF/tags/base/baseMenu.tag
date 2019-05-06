<%@tag description="Menu Icon" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>

<div class="">
	<t:menuIcon url="${pageContext.request.contextPath}/" jsClass="open" icon="folder_open"
		tooltip="Open a different book">Open</t:menuIcon>
	<div class="menuIconDivider col"></div>
	<t:menuIcon jsClass="undo" icon="undo"
		tooltip="Undo: Revokes the last action (Shortcut: ctrl+z)">Undo</t:menuIcon>
	<t:menuIcon jsClass="redo" icon="redo"
		tooltip="Redo: Executes the most recent undone action (Shortcut: ctrl+y)">Redo</t:menuIcon>
	<div class="menuIconDivider col"></div>
	<t:menuIcon jsClass="deleteSelected" icon="delete"
		tooltip="Delete selected items (Shortcut: DEL)">Delete</t:menuIcon>
	<div class="menuIconDivider col"></div>
</div>