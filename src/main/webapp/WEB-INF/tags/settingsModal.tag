<%@tag description="Tag for changing and displaying the settings of a region" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="settings-modal" class="modal">
    <div class="modal-content">
        <h4>User Settings</h4>
        <div class="row">
            <form class="col s12" id="settingsForm">
                <ul class="collapsible" data-collapsible="accordion">
                <li>
                    <div class="collapsible-header"><i class="material-icons">mouse</i>Input</div>
                    <div class="collapsible-body">
                        <div class="row">
                            <div class="input-field col s12">
                                <input id="settingsDoubleClickTimeDelta" placeholder="2" type="number" class="validate">
                                <label class="active" for="settingsDoubleClickTimeDelta">Double Click Time Delta (Maximum)</label>
                            </div>
                        </div>
                        <div class="row">
                            <div class="input-field col s12">
                                <input id="settingsDoubleClickDistance" placeholder="10" type="number" class="validate">
                                <label class="active" for="settingsDoubleClickDistance">Double Click Distance (Maximum)</label>
                            </div>
                        </div>
                    </div>
                </li>
                </ul>
            </form>
        </div>
    </div>
    <div class="modal-footer">
        <a id="saveUserSettings" class="col s12 waves-effect waves-light btn doSaveUserSettings tooltipped" data-position="left"
           data-delay="50" data-tooltip="Save and apply the user settings">Save<i class="material-icons right">save</i></a>
        <a href="#!" class="modal-close waves-effect waves-green btn-flat">Close</a>
    </div>
</div>
