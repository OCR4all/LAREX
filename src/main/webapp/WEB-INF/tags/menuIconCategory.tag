<%@tag description="Menu Icon" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ attribute name="name" required="true" %>
<%@ attribute name="jsClass" required="false" %>


<div class="menuIconCategory <c:if test="${not empty jsClass}">${jsClass}</c:if> ">
	<div><jsp:doBody /></div>
	<span class="menuIconCategoryText"><c:out value="${name}"/></span>
</div>
