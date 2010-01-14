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
    $('.actions .save', this).click(function (ev) {
        var img = $('img', this);
        var pos = this.hash.substring(1);
        if (img.hasClass('stored')) {
            var id = this.id;
            img.removeClass('stored');
            img.attr('src', 'disk.gif');
            $.get('user.xql', { list: 'remove', id: id });
        } else {
            img.attr('src', 'disk_gew.gif');
            img.addClass('stored');
            $.get('user.xql', { list: 'add', pos: pos });
        }
        $('#personal-list-size').load('user.xql', { action: 'count' });
    });
}

function searchTabSelected(ev, ui) {
    if (ui.index == 3) {
        $('#personal-list-size').load('user.xql', { action: 'count' });
    }
}