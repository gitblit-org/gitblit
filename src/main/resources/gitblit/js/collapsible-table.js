$(function() {
	$('i.table-group-collapsible')
		.click(function(){
			$(this).closest('tr.group.collapsible').nextUntil('tr.group.collapsible').toggle();
			$(this).toggleClass('fa-minus-square-o');
			$(this).toggleClass('fa-plus-square-o');
		});
	
	$('i.table-openall-collapsible')
		.click(function(){
			$('tr.group.collapsible').first().find('i').addClass('fa-minus-square-o');
			$('tr.group.collapsible').first().find('i').removeClass('fa-plus-square-o');
			$('tr.group.collapsible').first().nextAll('tr:not(tr.group.collapsible)').show();
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').addClass('fa-minus-square-o');
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').removeClass('fa-plus-square-o');
		});
	
	$('i.table-closeall-collapsible')
		.click(function(){
			$('tr.group.collapsible').first().find('i').addClass('fa-plus-square-o');
			$('tr.group.collapsible').first().find('i').removeClass('fa-minus-square-o');
			$('tr.group.collapsible').first().nextAll('tr:not(tr.group.collapsible)').hide();
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').addClass('fa-plus-square-o');
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').removeClass('fa-minus-square-o');
		});
	
	$( document ).ready(function() {
		if($('tr.group.collapsible').first().find('i').hasClass('fa-plus-square-o')) {
			$('tr.group.collapsible').first().nextAll('tr:not(tr.group.collapsible)').hide();
		}
	});
});