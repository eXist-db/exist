$(function() {
    $('#keyword-form').submit(function () {
        loadIndexTerms();
        return false; 
    });
    initCollectionTree();
});


/* collection action buttons */
$(document).ready(function(){ 
    $('#collection-create-folder').hide();
    $('#collection-move-folder').hide();
    $('#collection-remove-folder').hide();
    $('#collection-permissions').hide();
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
            showHideCollectionWriteableControls();
            showHideCollectionOwnerControls();
            
//            form.submit();
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

function showHideCollectionWriteableControls() {
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: "can-write-collection", collection: collection };
    $.get("checkuser.xql", params, function(data) {
        if($(data).text() == 'true') {
            $('#collection-create-folder').show();
            $('#collection-move-folder').show();
            $('#collection-remove-folder').show();
        } else {
            $('#collection-create-folder').hide();
            $('#collection-move-folder').hide();
            $('#collection-remove-folder').hide();
        }
    });
};

function showHideCollectionOwnerControls() {
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: "is-collection-owner", collection: collection };
    $.get("checkuser.xql", params, function(data) {
        if($(data).text() == 'true') {
            $('#collection-permissions').show();
        } else {
            $('#collection-permissions').hide();
        }
    });
};

/*
    Called when the user clicks on the "remove" button in the remove resource dialog
 */
function removeResource(dialog) {
    var resource = $('#remove-resource-form input[name = resource]').val();
    var params = { action: 'remove-resource', resource: resource };
    $.get("operations.xql", params, function (data) {
        dialog.dialog("close");
        $(location).attr('href', 'index.xml');
    });
}

/*
    Called when the user clicks on the "remove" button in the remove resource dialog
 */
function moveResource(dialog) {
    var path = $('#move-resource-form select[name = path]').val();
    var resource = $('#move-resource-form input[name = resource]').val();
    var params = { action: 'move-resource', path: path, resource: resource };
    $.get("operations.xql", params, function (data) {
        dialog.dialog("close");
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

/*
    Called when the user clicks on the "move" button in the move collection dialog.
 */
function moveCollection(dialog) {
    var path = $('#move-collection-form select[name = path]').val();
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: 'move-collection', path: path, collection: collection };
    $.get("operations.xql", params, function (data) {
        $("#collection-tree-tree").dynatree("getRoot").reload();
        dialog.dialog("close");
    });
}

/*
    Called when the user clicks on the "remove" button in the remove collection dialog.
 */
function removeCollection(dialog) {
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: 'remove-collection', collection: collection };
    $.get("operations.xql", params, function (data) {
        $("#collection-tree-tree").dynatree("getRoot").reload();
        dialog.dialog("close");
    });
}

/*
    Called when the user clicks on the "create" button in the create collection dialog.
 */
function updateCollectionPermissions(dialog) {
    var restriction = $('#update-collection-permissions-form input[name = restriction]:checked').val();
    var userGroup = $('#update-collection-permissions-form select[name = userGroup]').val();
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: 'update-collection-permissions', restriction: restriction, userGroup: userGroup, collection: collection };
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
        data: "user=" + user.val() + "&password=" + password.val(),
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
    $('.actions-toolbar .save', this).click(function (ev) {
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
        return false;
    });
    
    /** add remove resource action */
    $('.actions-toolbar .remove-resource', this).click(function() {{
    	alert("FOUND");
        $('#remove-resource-id').val($('#' + this.id).attr('href').substr(1));
        $('#remove-resource-dialog').dialog('open');
        return false;
    }});
    
    /** add move resource action */
    $('.actions-toolbar .move-resource', this).click(function() {{
        $('#move-resource-id').val($('#' + this.id).attr('href').substr(1));
        $('#move-resource-dialog').dialog('open');
        return false;
    }});
}

function searchTabSelected(ev, ui) {
    if (ui.index == 3) {
        $('#personal-list-size').load('user.xql', { action: 'count' });
    }
}