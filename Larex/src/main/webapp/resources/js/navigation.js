$(document).ready(function () {
	$('.bookopen').click(function () {
		//$().redirect('viewer', {'bookid': $(this).attr('id'), 'page': '0'});
		var form = $('<form action="viewer" method="get">' +
			'<input type="hidden" name="book" value="' + $(this).attr('id') + '" />' +
			'</form>');
		$('body').append(form);
		$(form).submit();
	});
});
