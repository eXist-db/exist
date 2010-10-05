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
    $('#collection-sharing').hide();
});

/* sharing dialog actions */
$(document).ready(function(){

    $('#sharing-collection-with-group').click(function(){
            if($(this).is(':checked')) {
                $('#group-sharing-panel').show();
            } else {
                $('#group-sharing-panel').hide();
            }
        });
    
    $('#sharing-collection-with-other').click(function(){
            if($(this).is(':checked')) {
                $('#other-sharing-panel').show();
            } else {
                $('#other-sharing-panel').hide();
            }
    });

    //when the sharing dialog is opened
    $('#sharing-collection-dialog').bind("dialogopen", function(event, ui) {
    
        //show/hide group sharing panel
        if(!$('#sharing-collection-with-group').is(':checked')) {
            $('#group-sharing-panel').hide()
        }
        
        //show/hide other sharing panel
        if(!$('#sharing-collection-with-other').is(':checked')) {
            $('#other-sharing-panel').hide()
        }
    });
    
    updateSharingGroupMembers($('#group-list').val());
    $('#group-list').change(function(){
        updateSharingGroupMembers($('#group-list').val());
    });
    
    //add member to group events
    $('#add-new-member-to-group-button').click(function(){
        $('#add-member-to-group-sharing-dialog').dialog('open');
    });
    
    $('#add-member-to-group-button').click(function(){
        addMemberToSharingGroupMembers($('#members-list').val(), true);
        $('#add-member-to-group-sharing-dialog').dialog('close');
    });
    
    //add group events
    $('#new-group-button').click(function(){
        $('#new-group-sharing-dialog').dialog('open');
    });
    
    $('#add-group-button').click(function(){
        addNewGroupToGroupList($('#new-group-name').val());
        $('#new-group-sharing-dialog').dialog('close');
    });
});

function getActiveGroup()
{
    var selectedGroupId = $('#group-list').val();
    if(selectedGroupId){
        return selectedGroupId;
    } else {
        return $('#group-list option[0]').val();
    }
}

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
            var title = dtnode.data.title;
            var key = dtnode.data.key;
            updateCollectionPaths(title, key);
            showHideCollectionWriteableControls();
            showHideCollectionOwnerControls();
            var groupId = getActiveGroup();
            if(groupId){
                updateSharingGroupCheckboxes(groupId);
            }
            updateSharingOtherCheckboxes();
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

function updateCollectionPaths(title, key) {
    
    //search forms
    var form = $('#simple-search-form');
    $('input[name = collection]', form).val(key);
    
    var form = $('#advanced-search-form');
    $('input[name = collection]', form).val(key);
    
    //dialog collection paths
    $('span[id $= collection-path_]').text(title);
    $('input[id $= collection-path_]').val(key);
    
};

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
    var params = { action: "is-collection-owner-and-not-home", collection: collection };
    $.get("checkuser.xql", params, function(data) {
        if($(data).text() == 'true') {
            $('#collection-sharing').show();
        } else {
            $('#collection-sharing').hide();
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
    Called when the user clicks on the "save" button in the collection sharing dialog
 */
function updateCollectionSharing(dialog) {
    var collection = $('#simple-search-form input[name = collection]').val();
   
    var sharingCollectionWith = [];   
    $('input:checked[type="checkbox"][name="sharing-collection-with"]').each(function() {
        sharingCollectionWith.push($(this).val());
    });
    
    var groupList = $('#group-list').val();
    
    var groupMember = [];
    $('input:checked[type="checkbox"][name="group-member"]').each(function() {
        groupMember.push($(this).val());
    });
    
    var groupSharingPermissions = [];
    $('input:checked[type="checkbox"][name="group-sharing-permissions"]').each(function() {
        groupSharingPermissions.push($(this).val());
    });
    
    var otherSharingPermissions = [];
    $('input:checked[type="checkbox"][name="other-sharing-permissions"]').each(function() {
        otherSharingPermissions.push($(this).val());
    });
    
    var params = { "action":"update-collection-sharing", "collection":collection, "sharingCollectionWith":sharingCollectionWith, "groupList": groupList, "groupMember": groupMember, "groupSharingPermissions": groupSharingPermissions, "otherSharingPermissions": otherSharingPermissions };
    $.post("operations.xql", params, function (data) {
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

function updateSharingGroupMembers(groupId) {

    var params = { action: "get-sharing-group-members", groupId: groupId };
    $.get("operations.xql", params, function(data) {
        
        //remove any existing members
        $('#group-members-list').find('li').remove();
    
        var owner = false;
        if($(data).find('owner true').size() == 1)
        {
            owner = true;
            $('#add-new-member-to-group-button').show();
        } else if($('#group-list :selected').val() == $('#group-list :selected').text()) {
            owner = true;   //this is a new group i.e. we own it!
            $('#add-new-member-to-group-button').show();
        } else {
            $('#add-new-member-to-group-button').hide();
        }
    
        //add new members
        $(data).find('member').each(function(){
            addMemberToSharingGroupMembers($(this).text(), owner);
        });
    });
    
    updateSharingGroupCheckboxes(groupId);
}

function updateSharingGroupCheckboxes(groupId) {
    //set the read/write checkboxes
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: "get-group-permissions", groupId: groupId, collection: collection };
    $.get("operations.xql", params, function(data) {
    
        //set read checkbox
        var readPermissions = $(data).find('read');
        if(readPermissions.size() == 1){
         //$('#group-sharing-premissions-read').attr('checked','checked');
         $('#group-sharing-premissions-read').get(0).checked = true;
        } else {
         //$('#group-sharing-premissions-read').removeAttr('checked');
         $('#group-sharing-premissions-read').get(0).checked = false;
        }
        
        //set write checkbox
        var writePermissions = $(data).find('write');
         if(writePermissions.size() == 1){
         //$('#group-sharing-premissions-write').attr('checked','checked');
         $('#group-sharing-premissions-write').get(0).checked = true;
        } else {
         //$('#group-sharing-premissions-write').removeAttr('checked');
         $('#group-sharing-premissions-write').get(0).checked = false;
        }
        
        //set sharing checkbox
        if(readPermissions.size() + writePermissions.size() >= 1) {
            //$('#sharing-collection-with-group').attr('checked','checked');
            $('#sharing-collection-with-group').get(0).checked = true;
            $('#group-sharing-panel').show();
        } else {
            //$('#sharing-collection-with-group').removeAttr('checked');
            $('#sharing-collection-with-group').get(0).checked = false;
            $('#group-sharing-panel').hide();
        }
    });
}

function updateSharingOtherCheckboxes() {
     //set the read/write checkboxes
    var collection = $('#simple-search-form input[name = collection]').val();
    var params = { action: "get-other-permissions", collection: collection };
    $.get("operations.xql", params, function(data) {
    
        //set read checkbox
        var readPermissions = $(data).find('read');
        if(readPermissions.size() == 1){
         $('#other-sharing-premissions-read').get(0).checked = true;
        } else {
         $('#other-sharing-premissions-read').get(0).checked = false;
        }
        
        //set write checkbox
        var writePermissions = $(data).find('write');
         if(writePermissions.size() == 1){
         $('#other-sharing-premissions-write').get(0).checked = true;
        } else {
         $('#other-sharing-premissions-write').get(0).checked = false;
        }
        
        //set sharing checkbox
        if(readPermissions.size() + writePermissions.size() >= 1) {
            $('#sharing-collection-with-other').get(0).checked = true;
            $('#other-sharing-panel').show();
        } else {
            $('#sharing-collection-with-other').get(0).checked = false;
            $('#other-sharing-panel').hide();
        }
    });
}

function addMemberToSharingGroupMembers(member, isGroupOwner){

    //dont add the item if it already exists
    var currentMemberInputs = $('#group-members-list').find('input');
    for(var i = 0; i <  currentMemberInputs.size(); i++){
        if(currentMemberInputs[i].getAttribute("value") == member){
            return;
        }
    }

    //add the item
    var uuid = (new Date()).getTime();
    var li = document.createElement('li');
    
    var input = document.createElement('input');
    input.setAttribute('id', 'group-member-' + uuid);
    input.setAttribute('type', 'checkbox');
    input.setAttribute('name', 'group-member');
    input.setAttribute('value', member);
    input.setAttribute('checked', 'checked');
    if(!isGroupOwner){
        input.setAttribute('disabled', 'disabled');
    }
    
    var label = document.createElement('label');
    label.setAttribute('for', 'group-member-_' + uuid);
    label.setAttribute('class', 'labelWithCheckboxLeft');
    label.innerText = member;
    
    li.appendChild(input);
    li.appendChild(label);

   $('#group-members-list').append(li);
}

function addNewGroupToGroupList(groupName){
    
    //add the new group
    $('#group-list').append(
        $("<option></option>").attr("value", groupName).text(groupName)
    );
    
    //select the new group
    $('#group-list').val(groupName);
    
    //update the members etc
    updateSharingGroupMembers($('#group-list').val());
}
