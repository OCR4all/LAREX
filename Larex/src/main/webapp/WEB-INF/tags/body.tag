<%@tag description="Main Body Tag" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>

<body>
	<header id="header">
		<nav>
			<div class="nav-wrapper">
				<a href="#" class="brand-logo"> LAREX</a>
				<ul id="nav-mobile" class="right hide-on-med-and-down">
					<li><a href="http://www.is.informatik.uni-wuerzburg.de/en/homepage/">Chair of Artificial Intelligence and Applied Computer Science</a></li>
					<li><a href="https://www.uni-wuerzburg.de/en/home/">University of Würzburg</a></li>
				</ul>
			</div>
		</nav>
	</header>

	<main id="main-div" role="main">
		<div id="content-main">
			<jsp:doBody />
		</div>
	</main>
		
	<footer id="footer" class="page-footer colorable">
		<div class="container">
			<div class="row">
				<div class="col l6 s12">
					<ul class="grey-text text-lighten-4">
						<li>
							<b>
								<a class="grey-text text-lighten-3"
								href="http://www.is.informatik.uni-wuerzburg.de/staff/reul_christian/">
									Christian Reul, M. Sc. </a>
							</b>
						</li>
						<li><i>Main Developer</i></li>
						<li><i>Research Assistant</i></li>
						<li>Email: <a href="mailto:christian.reul@uni-wuerzburg.de">christian.reul@uni-wuerzburg.de</a> </li>
					</ul>
				</div>
				<div class="col l6 s12">
					<ul class="grey-text text-lighten-4">
						<li><b>Nico Balbach, B. Sc.</b></li>
						<li><i>Web Developer</i></li>
						<li><i>Student Research Assistant</i></li>
						<li>Email: <a href="mailto:nico.balbach@informatik.uni-wuerzburg.de">nico.balbach@informatik.uni-wuerzburg.de</a></li>
					</ul>
				</div>
				<div class="col l6 s12">
					<ul class="grey-text text-lighten-4">
						<li><b>Dr. Uwe Springmann</b></li>
						<li><i>Head of Kallimachos Center for Digital Humanities</i></li>
						<li><i>University Library of Würzburg</i></li>
						<li>Email: <a href="mailto:uwe.springmann@uni-wuerzburg.de">uwe.springmann@uni-wuerzburg.de</a></li>
					</ul>
				</div>
				<div class="col l6 s12">
					<ul class="grey-text text-lighten-4">
						<li><b> <a class="grey-text text-lighten-3"
								href="http://www.is.informatik.uni-wuerzburg.de/staff/puppe_frank/">
									Prof. Dr. Frank Puppe </a>
						</b></li>
						<li><i>Head of Chair of Computer Science VI</i></li>
						<li><i>University of Würzburg</i></li>
						<li>Email: <a href="mailto:frank.puppe@uni-wuerzburg.de">frank.puppe@uni-wuerzburg.de</a></li>
					</ul>
				</div>
			</div>
		</div>
		<div class="footer-copyright">
			<div class="container">
				© 2017
			</div>
		</div>
	</footer>
</body>
