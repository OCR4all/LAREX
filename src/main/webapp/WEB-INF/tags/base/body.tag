<%@tag description="Main Body Tag" pageEncoding="UTF-8"%>

<body>
	<header id="header">
		<nav>
			<div class="nav-wrapper">
				<a href="#" class="brand-logo"> LAREX</a>
				<ul id="nav-mobile" class="right hide-on-med-and-down">
					<li><a href="https://www.uni-wuerzburg.de/en/zpd/startseite/">Centre for Philology and Digitality</a></li>
					<li><a href="https://www.uni-wuerzburg.de/en/home/">University of Würzburg</a></li>
				</ul>
			</div>
		</nav>
	</header>

	<main id="main-div" class="grey lighten-4" role="main">
		<div id="content-main">
			<jsp:doBody />
		</div>
	</main>
		
	<footer id="footer" class="page-footer colorable">
			<div class="row center-align">
				<div class="col offset-s3 s2">
					<a target="_blank" href="https://github.com/OCR4all/LAREX">GitHub</a>
				</div>
				<div class="col s2">
					<a target="_blank" href="https://mobile.twitter.com/uniwue_zpd">Twitter</a>
				</div>
				<div class="col s2">
					<a target="_blank" href="https://www.uni-wuerzburg.de/en/zpd/startseite/">Website</a>
				</div>
		</div>
		<div class="footer-copyright">
			<div class="row center-align">
				<div class="col s2">
					<span>© <%= new java.text.SimpleDateFormat("yyyy").format(new java.util.Date()) %></span>
				</div>
				<div class="col offset-s8 s2">
					<span id="larex-version"></span>
				</div>
			</div>
		</div>
	</footer>
</body>
