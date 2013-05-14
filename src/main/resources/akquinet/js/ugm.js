$(function() {
	$("#sortableUgm").sortable();
	$("#sortableUgm").disableSelection();
});
$(function() {
	$("#sortableUgmInactive").sortable();
	$("#sortableUgmInactive").disableSelection();
});
$(function() {
	$("#sortableUgm, #sortableUgmInactive").sortable({
		connectWith : ".connectedUgmSortable"
	}).disableSelection();
});
function toggleUgmDetailsVisibility(details, otherDetails) {
	otherDetails.slideUp(500);
	details.slideToggle(600);	
};
$($('.connectorButton').click(
		function() {
			window.toggleUgmDetailsVisibility($(this).parent().nextAll('.connectorDetails'), $(this).parent().nextAll(
					'.mappingRules'));
		}));

$($('.mappingButton').click(
		function() {
			window.toggleUgmDetailsVisibility($(this).parent().nextAll('.mappingRules'), $(this).parent().nextAll(
					'.connectorDetails'));
		}));