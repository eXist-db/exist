function autocompleteCallback() {
    var name = this.attr('name');
    var select = this.parent().parent().find('select[name ^= field]');
    if (select) {
        return select.val();
    } else
        return 'noparam';
}

function repeatCallback() {
    var input = $('input[name ^= input]', this);
    input.autocomplete("autocomplete.xql", {
        extraParams: { field: function () { return autocompleteCallback.call(input);} },
        width: 300,
        multiple: false,
        matchContains: false
    });
}

function resultsLoaded(options) {
    if (options.itemsPerPage > 1)
        $('tbody > tr:even > td', this).addClass('even');
    $('#filters').css('display', 'block');
    $('#filters .include-target').empty();
    $('#filters .expand').removeClass('expanded');
}