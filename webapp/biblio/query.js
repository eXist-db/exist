$(document).ready(function() {
    $('#input1').autocomplete("autocomplete.xql", {
        width: 300,
		multiple: false,
		matchContains: false
    });
});

function resultsLoaded(options) {
    if (options.itemsPerPage > 1)
        $('tbody > tr:even > td', this).addClass('even');
    $('#filters').css('display', 'block');
    $('#filters .include-target').empty();
    $('#filters .expand').removeClass('expanded');
}