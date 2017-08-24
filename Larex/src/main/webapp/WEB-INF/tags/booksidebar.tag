<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<div id="pagecontainer" class="row">
	<div class="col s12">
		<c:forEach items="${book.getPages()}" var="bookpage">
			<div class="chagePage pageImageContainer card col s12" data-page="${bookpage.getId()}">
				<img class="pageImage"
					alt="${bookpage.getImage()}"
					title="${bookpage.getImage()}"
					src="${bookPath}${bookpage.getImage()}"
					id="${bookpage.getImage()}" />
				<div class="pagestatus">
					<i class="material-icons pagestatusIcon pageIconExported circle tooltipped hide"
						data-position="bottom" data-delay="50" data-tooltip="This page has been exported.">file_download</i>
					<i class="material-icons pagestatusIcon pageIconSaved circle tooltipped hide"
						data-position="bottom" data-delay="50" data-tooltip="This page has been saved.">lock</i>
					<i class="material-icons pagestatusIcon pageIconError circle tooltipped hide"
						data-position="bottom" data-delay="50" data-tooltip="There has been an error with this page. Reload the webpage to reslove.">error</i>
				</div>
			</div>
		</c:forEach>
	</div>
</div>