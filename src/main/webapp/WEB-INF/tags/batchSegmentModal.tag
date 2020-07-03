<%@tag description="Main Body Tag" pageEncoding="UTF-8" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<div id="batchSegmentModal" class="modal modal-fixed-footer">
    <div class="modal-content">
        <h4>Batch Segmentation</h4>
        <p>Run segmentation on multiple pages with the current settings</p>
        <ul class="collapsible" data-collapsible="expandable">
<%--            <li>--%>
<%--                <div class="collapsible-header active"><i class="material-icons">settings</i>Options</div>--%>
<%--                <div class="collapsible-body collapsible-body-batch">--%>
<%--                    <input type="checkbox" class="" id="batchSaveSegmentation"/>--%>
<%--                    <label for="batchSaveSegmentation">--%>
<%--                        Autosave pages--%>
<%--                    </label>--%>
<%--                </div>--%>
<%--            </li>--%>
            <li>
                <div class="collapsible-header active"><i class="material-icons">library_books</i>Pages</div>
                <div class="collapsible-body collapsible-body-batch">
                    <ul id="batchImageList">
                        <li class="select-all">
                            <input type="checkbox" class="" id="selectAllBatch"/>
                            <label for="selectAllBatch">
                                Select all
                            </label>
                        </li>
                        <c:forEach items="${book.getPages()}" var="bookpage">
                            <li>
                                <input type="checkbox" id="${bookpage.getName()}" data-page="${bookpage.getId()}"
                                       class="batchPageCheck"/>
                                <label for="${bookpage.getName()}">
                                        ${bookpage.getName()}
                                </label>
                            </li>
                        </c:forEach>
                    </ul>
                </div>
            </li>
        </ul>

    </div>
    <div class="modal-footer">
        <a href="#!" class="modal-close waves-effect waves-green btn-flat">Close</a>
        <a class="col s12 waves-effect waves-light btn confirmBatchSegment tooltipped"
           href="#batchSegmentConfirmationModal" data-position="left" data-delay="50"
           data-tooltip="Run the batch segmentation">Next</a>
    </div>
</div>
<div id="batchSegmentConfirmationModal" class="modal">
    <div class="modal-content">
        <div class="card-panel center red" id="batchWarning">
			<span class="white-text">Running the batch segmentation will override the segmentation results for all selected pages.
			</span>
        </div>
        <div class="progress" id="batch-segmentation-progress">
            <div class="indeterminate"></div>
        </div>
        <div class="center">
            <a class="col s12 waves-effect waves-light btn doBatchSegment tooltipped" data-position="left"
               data-delay="50" data-tooltip="Run the batch segmentation">Run<i class="material-icons right">send</i></a>
            <a href="#!" class="modal-close btn waves-effect waves-light red">Abort</a>
        </div>
    </div>
</div>