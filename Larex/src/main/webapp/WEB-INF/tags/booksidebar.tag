<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<div id="pagecontainer" class="row">
	<div class="col s12">
		<c:forEach items="${book.getPages()}" var="bookpage">
			<div class="chagePage pageImageContainer emptyImage emptyPreview card col s12" data-bookpath="${bookPath}" data-page="${bookpage.getId()}" data-image="${bookpage.getImage()}">
			</div>
		</c:forEach>
	</div>
</div>
<div id="pageLegend" class="col s12">
	<i class="material-icons pagestatusIcon pageIconTodo circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="There is no segmentation for this page." >assignment_late</i>
	<i class="material-icons pagestatusIcon pageIconSession circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="Segmentation was saved in this session localy, but is not saved on the server.">save</i>
	<i class="material-icons pagestatusIcon pageIconServer circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="Segmentation is on the server.">lock</i>
	<i class="material-icons pagestatusIcon pageIconUnsaved circle tooltipped checked"
		data-position="top" data-delay="50" data-tooltip="Segmentation has changed but has not been saved.">warning</i>
</div>