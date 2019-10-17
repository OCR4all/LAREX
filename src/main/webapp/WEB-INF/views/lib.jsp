<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="b" tagdir="/WEB-INF/tags/base"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<b:webpage>
	<b:head>
		<script type="text/javascript" src="resources/js/navigation.js"></script>
		<link rel="stylesheet" href="resources/css/lib.css">
		<title>Larex - Library</title>
	</b:head>

	<b:body>
		<div id="menu" class="grey lighten-4">
			<div class="mainMenu">
				<ul class="tabs">
					<li class="tab"><a class="active" href="#lib">Library</a></li>
				</ul>
			</div>
			<div class="row">
				<div id="lib" class="col s12">
					<div class="col s12">
						<table class="highlight">
							<thead>
								<tr>
									<th data-field="open">Open</th>
									<th data-field="name">Book Name</th>
								</tr>
							</thead>
							<tbody>
								<c:forEach items="${library.getSortedBooks()}" var="bookentry">
									<tr id="${bookentry.getKey()}" class="bookopen">
										<td>
											<i class="material-icons">book</i></td>
										<td>${bookentry.getValue()}</td>
									</tr>
								</c:forEach>
							</tbody>
						</table>
					</div>
				</div>
			</div>
		</div>
	</b:body>
</b:webpage>