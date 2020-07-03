<%@tag description="Menu Icon" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ attribute name="jsClass" required="true" %>
<%@ attribute name="icon" required="true" %>
<%@ attribute name="id" required="false" %>
<%@ attribute name="tooltip" required="true" %>
<%@ attribute name="url" required="false" %>

<a <c:if test="${not empty url}">href="${url}"</c:if> class="${jsClass} waves-effect waves-light menuIcon col tooltipped infocus" 
	data-position="bottom" id="${id}" data-delay="50" data-tooltip="${tooltip}">
	<span class="menuIconMain">
		<i class="material-icons">${icon}</i>
	</span>
	<span class="menuIconSecond"><jsp:doBody /></span>
</a>
