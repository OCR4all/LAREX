<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="b" tagdir="/WEB-INF/tags/base"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<b:webpage>
	<b:head>
		<script type="text/javascript" src="resources/js/viewer/communicator.js"></script>
		<script type="text/javascript" src="resources/js/navigation.js"></script>
		<link rel="stylesheet" href="resources/css/lib.css">
		<title>Larex - Library</title>
	</b:head>

	<b:body>
		<div id="menu" class="grey lighten-4">
			<div class="mainMenu">
				<ul class="tabs" style="padding-left: 15%;">
					<li class="tab"><a class="active" href="#lib">Library</a></li>
					<li class="tab hide" id="mets_tab"><a class="active" href="#mets">OCR-D</a></li>
					<li class="tab hide"><a class="active" href="#settings">Settings</a></li>
				</ul>
			</div>
			<div class="container">
				<div id="lib" class="">
					<div class="col s12">
						<table class="highlight">
							<thead>
								<tr>
									<th data-field="open">Open</th>
									<th data-field="type">Type</th>
									<th data-field="name">Book Name</th>
									<th data-field="name">Book Path</th>
								</tr>
							</thead>
							<tbody>
								<c:forEach items="${library.getSortedBooks()}" var="bookentry">
									<tr id="${bookentry.getKey()}" class="bookopen" data-path=${bookentry.getValue().get(2)} data-type=${bookentry.getValue().get(1)}>
										<td><i class="material-icons">book</i></td>
										<td>${bookentry.getValue().get(1)}</td>
										<td>${bookentry.getValue().get(0)}</td>
										<td>${bookentry.getValue().get(2)}</td>
									</tr>
								</c:forEach>
							</tbody>
						</table>
					</div>
				</div>
				<div id="mets" class="">
						<div class="section col s4">
							<h5>Open mets file</h5>
							<a class="btn col s2 waves-effect waves-light tooltipped" data-tooltip="Open a project from an ocr-d mets.xml">
								Open
							</a>
						</div>
				</div>
				<div id="settings" class="">
					<div class="divider"></div>
					<div class="section">
						<h5>Page XML</h5>
						<div class="advanced-setting library-setting col s12">
						<span class="switch tooltipped library-xml-setting" data-position="bottom" data-delay="50" data-tooltip="If switched off pagexml will be saved in image folder">
							<label>
								<input type="checkbox">
									<span class="lever"></span>
								<span style="font-size: 16px; color: black">Custom XML Directory</span>
							</label>
						</span>
						</div>
						<br>
			<%--		<div id="xml_folder_input" class="input-field col s6">
                                <input placeholder="/var/ocr4all/library" id="xml_folder" type="text" class="validate">
                                <label for="first_name">Page XML Custom Directory</label>
                            </div>
                        </div>--%>
                    </div>
                </div>
            </div>

        </b:body>
        <div id="openBookModal" class="modal modal-fixed-footer">
            <div class="modal-content">
                <h4>Open Book</h4>
                <p>Please select the pages you want to open in Larex</p>
                <ul class="collapsible" data-collapsible="expandable">
                    <li>
                        <div class="collapsible-header active"><i class="material-icons">settings</i>Options</div>
                        <div class="collapsible-body collapsible-body-batch">
                            <ul>
                                <li>
									<br>
                                    <div class="advanced-setting library-setting col s12">
										<span class="switch tooltipped library-xml-setting" data-position="bottom" data-delay="50" data-tooltip="If switched off pagexml will be saved in image folder">
											<label>
												<input type="checkbox">
													<span class="lever"></span>
												<span style="font-size: 16px; color: black">Custom XML Directory</span>
											</label>
										</span>
                                    </div>
									<br>
                                </li>
								<li>
									<div id="xml_folder_input" class="input-field col s6 hide">
										<input placeholder="/var/ocr4all/library" id="xml_folder" type="text" class="validate">
										<label for="xml_folder">Page XML Custom Directory</label>
									</div>
								</li>
                            </ul>
                        </div>
                    </li>
					<li>
						<div id='fileGrp-div' class="section col s4" style="padding-left: 2%;">
							<h5>Choose a File Group</h5>
							<a id='FileGrpBtn' class='dropdown-button btn' href='#' constrainWidth="false" data-activates='file-grp'>File Group</a>
							<ul id='file-grp' class='dropdown-content'>

							</ul>
						</div>
					</li>
                    <li>
                        <div id="pageSection" class="collapsible-header active"><i class="material-icons">library_books</i>Pages</div>
                        <div class="collapsible-body collapsible-body-batch">
                            <ul id="bookImageList" style="padding-left: 2%;">
                            </ul>
                        </div>
                    </li>
                </ul>

            </div>
            <div class="modal-footer">
                <a href="#!" class="modal-close waves-effect waves-green btn-flat">Close</a>
                <a id="viewerNext" class="col s12 waves-effect waves-light btn tooltipped autoLoadPagesBatch"
				   data-position="left" data-delay="50"
                   data-tooltip="Open Selected pages in Larex">Next</a>
            </div>
        </div>
    </b:webpage>