$(function() {
    $('#keyword-form').submit(function () {
        loadIndexTerms();
        return false; 
    });
});

function loadIndexTerms() {
    var input = $('input[name = input-keyword-prefix]');
    $('#keywords-result').load("filters.xql?type=keywords&prefix=" + input.val(), function () {
        if ($('#keywords-result ul').hasClass('complete'))
            input.css('display', 'none');
    });
}

function autocompleteCallback(node, params) {
    var name = node.attr('name');
    var select = node.parent().parent().find('select[name ^= field]');
    if (select.length == 1) {
        params.field = select.val();
    }
}

function repeatCallback() {
    var input = $('input[name ^= input]', this);
    input.autocomplete({
        source: function(request, response) {
            var data = { term: request.term };
            autocompleteCallback(input, data);
            $.ajax({
                url: "autocomplete.xql",
                dataType: "json",
                data: data,
                success: function(data) {
                    response(data);
                }});
        },
        minLength: 3
    });

    $('select[name ^= operator]', this).each(function () {
        $(this).css('display', '');
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