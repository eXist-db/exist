$(function() {
    $('#keyword-form').submit(function () {
        loadIndexTerms();
        return false; 
    });
    initCollectionTree();
});

/*
    Initialize the collection tree. Connect toolbar button events.
 */
function initCollectionTree() {
    var dynaTree = $('#collection-tree-tree');
    var treeDiv = $('#collection-tree-main').css('display', 'none');
    dynaTree.dynatree({
        persist: true,
        rootVisible: false,
        initAjax: {url: "collections.xql" },
        onActivate: function (dtnode) {
            var key = dtnode.data.key;
            var form = $('#simple-search-form');
            $('input[name = collection]', form).val(key);
            form.submit();
        }
    });
    $('#toggle-collection-tree').click(function () {
        if (treeDiv.css('display') == 'none') {
            $('#collection-tree').css({width: '300px', height: 'auto', 'background-color': 'transparent'});
            $('#main-content').css('margin-left', '310px');
            treeDiv.css('display', '');
        } else {
            $('#collection-tree').css({width: '40px', height: '400px', 'background-color': '#CCC'});
            $('#main-content').css('margin-left', '50px');
            treeDiv.css('display', 'none');
        }
    });
    $('#collection-expand-all').click(function () {
        $("#collection-tree-tree").dynatree("getRoot").visit(function(dtnode){
            dtnode.expand(true);
        });
        return false;
    });
    $('#collection-collapse-all').click(function () {
        $("#collection-tree-tree").dynatree("getRoot").visit(function(dtnode){
            dtnode.expand(false);
        });
        return false;
    });
    $('#collection-reload').click(function () {
        $("#collection-tree-tree").dynatree("getRoot").reload();
        return false;
    });
}

/*
    Called when the user clicks on the "create" button in the create collection dialog.
 */
function createCollection(dialog) {
    var name = $('#create-collection-form input[name = name]').val();
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: 'create-collection', name: name, collection: collection };
    $.get("operations.xql", params, function (data) {
        $("#collection-tree-tree").dynatree("getRoot").reload();
        dialog.dialog("close");
    });
}

/**
 * Called after the user clicked "Login" on the login form.
 * Checks if the supplied credentials are valid. If yes, submit
 * the form to reload the page.
 */
function login() {
    var user = $('#login-dialog input[name = user]');
    var password = $('#login-dialog input[name = password]');
    $('#login-message').text('Checking ...');
    $.ajax({
        url: "checkuser.xql",
        data: "user=" + user.val() + "&amp;password=" + password.val(),
        type: 'POST',
        success:
            function(data, message) { $('#login-form').submit(); },
        error: function (response, message) { $('#login-message').html('Login failed: ' + response.responseText); }
    });
}

/**
 * Called from the create indexes dialog if the user clicks on "Start".
 */
function createIndexes() {
    var pass = $('#optimize-dialog input[name = password]');
    $('#optimize-message').text('Running ...');
    $.get('optimize.xql?pass=' + pass.val(),
        function (data, status) {
            if (status != "success")
                $('#optimize-message').text('Error during optimize!');
            else
                $('#optimize-dialog').dialog("close");
    });
}

function loadIndexTerms() {
    var input = $('input[name = input-keyword-prefix]');
    $('#keywords-result').load("filters.xql?type=keywords&prefix=" + input.val(), function () {
        if ($('#keywords-result ul').hasClass('complete'))
            $('#keyword-form').css('display', 'none');
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