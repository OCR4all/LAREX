<%@tag description="Main Body Tag" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="contextmenu" class="card hide infocus">
	<div class="select-regions">
		<ul class="collection highlight">
			<c:forEach var="type" items="${segmenttypes}">
				<c:if test="${!(type.key eq 'ignore')}">
					<li class="collection-item contextTypeOption contextregionlegend" data-type="${type.key}">
						<div class="legendicon ${type.key}"></div>${type.key}
					</li>
				</c:if>
			</c:forEach>
		</ul>
	</div>
</div>