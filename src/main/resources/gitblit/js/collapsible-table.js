$(function() {
	$('i.table-group-collapsible')
		.click(function(){
			var nodeId = $(this).closest('tr.group.collapsible.tree').data('nodeId');
			if(nodeId!==undefined){
				//we are in tree view
				if($(this).hasClass('fa-minus-square-o')){
					$(this).closest('tr.group.collapsible.tree').nextAll('tr.child-of-'+nodeId).hide();
					$(this).closest('tr.group.collapsible.tree').nextAll('tr.child-of-'+nodeId).addClass('hidden-by-'+nodeId);
				}else{
					$(this).closest('tr.group.collapsible.tree').nextAll('tr.child-of-'+nodeId).removeClass('hidden-by-'+nodeId);
					$(this).closest('tr.group.collapsible.tree').nextAll('tr.child-of-'+nodeId+':not([class*="hidden-by-"])').show();
				}
			}else{
				$(this).closest('tr.group.collapsible').nextUntil('tr.group.collapsible').toggle();
			}
			$(this).toggleClass('fa-minus-square-o');
			$(this).toggleClass('fa-plus-square-o');
		});


	$('i.table-openall-collapsible')
		.click(function(){
			$('tr.group.collapsible').first().find('i').addClass('fa-minus-square-o');
			$('tr.group.collapsible').first().find('i').removeClass('fa-plus-square-o');
			$('tr.group.collapsible').first().nextAll('tr:not(tr.group.collapsible),tr.group.collapsible.tree').show();
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').addClass('fa-minus-square-o');
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').removeClass('fa-plus-square-o');

			var nodeId = $('tr.group.collapsible.tree').data('nodeId');
			if(nodeId!==undefined){
				//we are in tree view
				$('tr[class*="child-of-"]').removeClass(function(index, className){
					return (className.match(/\hidden-by-\S+/g)||[]).join(' ');
				});
				$('tr.group.collapsible > i').addClass('fa-minus-square-o');
				$('tr.group.collapsible > i').removeClass('fa-plus-square-o');
			}
		});

	$('i.table-closeall-collapsible')
		.click(function(){
			$('tr.group.collapsible').first().find('i').addClass('fa-plus-square-o');
			$('tr.group.collapsible').first().find('i').removeClass('fa-minus-square-o');
			$('tr.group.collapsible').first().nextAll('tr:not(tr.group.collapsible),tr.group.collapsible.tree').hide();
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').addClass('fa-plus-square-o');
			$('tr.group.collapsible').first().nextAll('tr.group.collapsible').find('i').removeClass('fa-minus-square-o');

			var nodeId = $('tr.group.collapsible.tree').first().data('nodeId');
			if(nodeId!==undefined){
				//we are in tree view, hide all sub trees
				$('tr[class*="child-of-"]').each(function(){
					var row = $(this);
					var classList = row.attr('class').split('/\s+/');
					$.each(classList, function(index, c){
						if(c.match(/^child-of-*/)){
							row.addClass(c.replace(/^child-of-(\d)/, 'hidden-by-$1'));
						}
					});
				});
				$('tr.group.collapsible i').addClass('fa-plus-square-o');
				$('tr.group.collapsible i').removeClass('fa-minus-square-o');
			}
		});

	$( document ).ready(function() {
		if($('tr.group.collapsible').first().find('i').hasClass('fa-plus-square-o')) {
			$('tr.group.collapsible').first().nextAll('tr:not(tr.group.collapsible),tr.group.collapsible.tree').hide();
		}
	});
});
