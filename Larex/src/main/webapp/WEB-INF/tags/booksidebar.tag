<%@tag description="Edit Segment Window" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<div id="pagecontainer" class="row">
	<div class="col s12">
		<c:forEach items="${book.getPages()}" var="bookpage">
			<div class="chagePage pageImageContainer card col s12" data-page="${bookpage.getId()}">
				<img class="pageImage"
					src="${bookPath}${bookpage.getImage()}"
					id="${bookpage.getImage()}" />
			</div>
		</c:forEach>
	</div>
</div>