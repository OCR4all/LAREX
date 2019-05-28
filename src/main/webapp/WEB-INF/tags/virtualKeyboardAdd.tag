<%@tag description="Tag for the addition of keys to the virtual keyboard" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>


<div id="virtual-keyboard-add" class="card hide infocus">
	<div id="vk-settings">
		<div class="col s12 vk-setting">
			<span class="settings-input">New button</span>
			<span class="settings-input">
				<input value="" id="vk-btn-value" class="input-number"
					type="text" class="validate" size="4" />
			</span>
		</div>
	</div>
	<a href="#!" id="vk-save"
		class="waves-effect waves-green btn-flat">Save</a>
	<a href="#!" id="vk-cancel"
			class="waves-effect waves-green btn-flat">Cancel</a>
</div>