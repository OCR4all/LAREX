<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<t:webpage>
	<t:head>
		<script type="text/javascript" src="resources/js/navigation.js"></script>
		<title>Larex - Error - <c:out value="${code}"/></title>
	</t:head>

	<t:body>
		<div id="error_wrapper" class="container row">
			<div id="error" class="card col s12">
				<div id="error_main valign" class="col s12">
					<p>
						<i class="material-icons">error_outline</i> Ooopps!
					</p>
					<p>That should not have been happened!</>
				</div>
				<div id="error_text" class="col s12">
					<p>
						<c:out value="${message}"/>
					</p>
					<p>
						Error code:
						<c:out value="${code}"/>
					</p>
				</div>
			</div>
		</div>
	</t:body>
</t:webpage>