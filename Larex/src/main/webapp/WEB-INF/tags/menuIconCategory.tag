<%@tag description="Menu Icon" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ attribute name="name" required="true" %>


<div class="menuIconCategory">
	<div><jsp:doBody /></div>
	<span class="menuIconCategoryText"><c:out value="${name}"/></span>
</div>