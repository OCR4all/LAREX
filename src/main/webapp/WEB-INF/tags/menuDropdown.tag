<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>

<t:menuIconCategory name="Page" jsClass="page_selection">
    <select class="menuPageSelector">
        <c:forEach items="${book.getPages()}" var="bookpage">
            <option data-title="${book.getName()}/${bookpage.getName()}"
                    data-page="${bookpage.getId()}" data-image="${bookpage.getImages().get(0)}">${bookpage.getName()}
            </option>
        </c:forEach>
    </select>
</t:menuIconCategory>
<div class="menuIconDivider col"></div>