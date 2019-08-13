<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="pagecontainer" class="row">
	<div class="col s12">
		<c:forEach items="${book.getPages()}" var="bookpage">
			<div class="changePage pageImageContainer emptyImage emptyPreview card col s12"
						data-title="${book.getName()}/${bookpage.getName()}" data-page="${bookpage.getId()}" data-image="${bookpage.getImages().get(0)}">
				<c:if test="${bookpage.getImages().size() > 1}">
					<ul class="image_versions">
						<c:forEach items="${bookpage.getImages()}" var="version" varStatus="loop">
							<li class="changePage image_version" data-page="${bookpage.getId()}" data-imagenr="${loop.index}" title="${version}">
								<c:out value="${loop.index}"/>
							</li>
						</c:forEach>
					</ul>
				</c:if>	
			</div>
		</c:forEach>
	</div>

</div>
<div id="pageLegend" class="col s12">
	<i class="material-icons pagestatusIcon pageIconTodo circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="There is no segmentation for this page." >assignment_late</i>
	<i class="material-icons pagestatusIcon pageIconUnsaved circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="Current segmentation may be unsaved.">warning</i>
	<i class="material-icons pagestatusIcon pageIconSession circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="Segmentation was saved in this session.">save</i>
	<i class="material-icons pagestatusIcon pageIconServer circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="There is a segmentation for this page on the server.">lock</i>
</div>