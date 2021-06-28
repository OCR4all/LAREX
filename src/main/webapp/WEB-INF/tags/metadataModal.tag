<%@tag description="Main Body Tag" pageEncoding="UTF-8" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<div id="metadataModal" class="modal modal-fixed-footer">
    <div class="modal-content">
        <h4>Metadata</h4>
        <div class="row">
            <form class="col s12" id="metadataForm">
                <div class="row">
                    <div class="input-field col s12">
                        <input id="metadataCreator" placeholder="Creator" type="text" class="validate">
                        <label class="active" for="metadataCreator">Creator</label>
                    </div>
                </div>
                <div class="row">
                    <div class="input-field col s12">
                        <input id="metadataComments" placeholder="Comments" type="text" class="validate">
                        <label class="active" for="metadataComments">Comments</label>
                    </div>
                </div>
                <div class="row">
                    <div class="input-field col s12">
                        <input id="metadataExternalRef" placeholder="External Ref" type="text" class="validate">
                        <label class="active" for="metadataExternalRef">External Ref</label>
                    </div>
                </div>
                <div class="row">
                    <div class="input-field col s12">
                        <input disabled id="metadataCreated" placeholder="Created" type="text" class="validate">
                        <label class="active" for="metadataCreated">Created</label>
                    </div>
                </div>
                <div class="row">
                    <div class="input-field col s12">
                        <input disabled id="metadataLastModified" placeholder="Last Modified" type="text" class="validate">
                        <label class="active" for="metadataLastModified">Last Modified</label>
                    </div>
                </div>
            </form>
        </div>
    </div>
    <div class="modal-footer">
        <a href="#!" id="metadata-save" class="waves-effect waves-green btn-flat">Save</a>
        <a href="#!" class="modal-close waves-effect waves-red btn-flat">Close</a>
    </div>
</div>
