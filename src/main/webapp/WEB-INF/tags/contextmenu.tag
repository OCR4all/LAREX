<%@tag description="Main Body Tag" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="contextmenu" class="card hide infocus">
	<div class="card">
		<div class="card-tabs">
			<ul class="tabs tabs-fixed-width" id="contextMenuTab">
				<li class="tab"><a href="#types">Types</a></li>
				<li class="tab"><a href="#directions">Reading Direction</a></li>
			</ul>
		</div>
		<div class="card-content">
			<div id="types">
				<div class="select-regions">
					<ul class="collection highlight">
						<c:forEach var="type" items="${regionTypes}">
							<c:if test="${!(type.key eq 'ignore')}">
								<li class="collection-item contextTypeOption contextregionlegend" data-type="${type.key}">
									<div class="legendicon ${type.key}"></div>${type.key}
								</li>
							</c:if>
						</c:forEach>
					</ul>
				</div>
			</div>
			<div id="directions">
				<div class="select-directions">
					<ul class="collection highlight">
						<li class="reading-direction-item contextReadingDirectionOption" data-direction="unspecified">
							Unspecified
						</li>
						<li class="reading-direction-item contextReadingDirectionOption" data-direction="left-to-right">
							Left To Right
						</li>
						<li class="reading-direction-item contextReadingDirectionOption" data-direction="right-to-left">
							Right To Left
						</li>
						<li class="reading-direction-item contextReadingDirectionOption" data-direction="top-to-bottom">
							Top To Bottom
						</li>
						<li class="reading-direction-item contextReadingDirectionOption" data-direction="bottom-to-top">
							Bottom To Top
						</li>
					</ul>
				</div>
			</div>
		</div>
	</div>
</div>