xquery version "1.0";
(: $Id$ :)
(:
    Module: display and edit users.
:)

module namespace users="http://exist-db.org/xquery/admin-interface/users";

declare namespace util="http://exist-db.org/xquery/util";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace config="http://exist-db.org/Configuration";

declare function users:main() as element() {
    <div class="panel">
        <div class="panel-head">User Management</div>
        
        <p class="note">Note: This is work in progress.  Don't use this page to manage
        users in a production environment.  Use the Java admin client instead.</p>        
        {
        (: We do not need to check if the current user is an admin user, since admin.xql does this. :)
        (: if (not(xdb:is-admin-user(xdb:get-current-user()))) then
            <div class="error">
                Only users of the "dba" group can change user settings.
            </div>
        else :)
            users:process()
        }
    </div>
};

declare function users:process() as element()* {
    let $action := request:get-parameter("action", "") 
    return
        if ($action eq "Create User") then
            users:new-user()
        else if ($action eq "Change User") then
            users:update-user()
        else if ($action eq "Remove User" and exists(request:get-parameter("uid", ()))) then
            users:remove-user()
        else if ($action eq "Create Group") then
            users:new-group()
        else
            users:display()
};

declare variable $users:all-users {
    collection("/db/system/security/exist/accounts")/config:account
};

declare variable $users:current-users {
    $users:all-users[util:collection-name(.) ne '/db/system/security/exist/accounts/removed']
};

declare variable $users:all-groups {
    collection("/db/system/security/exist/groups")/config:group
};

declare variable $users:current-groups {
    $users:all-groups[util:collection-name(.) ne '/db/system/security/exist/groups/removed']
};

declare function users:remove-user() as element()* {
    let $uid := request:get-parameter("uid", ())
    let $name := $users:all-users[@id = $uid]/config:name/text()
    return
        if ($name eq xdb:get-current-user()) then
            <div class="error">You cannot remove the current user!</div>
        else if ($name eq 'guest') then
            <div class="error">You cannot remove guest!</div>
        else
            (
            xdb:delete-user($name),
            <div class="actions">User {$name} removed.</div>
            )
        ,
        users:display()
};

declare function users:new-user() as element()* {
    let $name := request:get-parameter("name", ())
    let $grp := string-join(request:get-parameter("groups", ()), ',')
    let $groups := 
        if ($grp) then
            if (contains($grp, ",")) then
                tokenize($grp, "\s*,\s*")
            else
                $grp
         else ()
    let $pass1 := request:get-parameter("pass1", "")
    let $pass2 := request:get-parameter("pass2", "")
    let $home := request:get-parameter("home", ()) 
    return
        if ($pass1 != $pass2) then
            (
            <div class="error">Passwords are not identical.</div>,
            users:correct-user(-1, $name, $grp, $home)
            )
        else if (empty($groups)) then
            (
            <div class="error">Please specify one group at least.</div>,
            users:correct-user(-1, $name, $grp, $home)
            )
        else
            (
            xdb:create-user($name, $pass1, $groups, $home),
            users:display()
            )
};

declare function users:update-user() as element()* {
    let $name := request:get-parameter("name", ())
    let $grp := string-join(request:get-parameter("groups", ()), ',')
    let $groups := 
        if ($grp) then
            if (contains($grp, ",")) then
                tokenize($grp, "\s*,\s*")
            else
                $grp
        else ()
    let $pass1 := request:get-parameter("pass1", "")
    let $pass2 := request:get-parameter("pass2", "")
    let $nopass := request:get-parameter("nopass", ())
    let $pass := if ($nopass) then () else $pass1
    let $home := request:get-parameter("home", ())
    let $uid := request:get-parameter("uid", "") 
    return
        if (not($nopass) and $pass1 ne $pass2) then
            (
            <div class="error">Passwords are not identical.</div>,
            users:correct-user($uid, $name, $grp, $home)
            )
        else if (empty($groups)) then
            (
            <div class="error">Please specify one group at least.</div>,
            users:correct-user($uid, $name, $grp, $home)
            )
        else
            (
            xdb:change-user($name, $pass, $groups, $home),
            if (xdb:get-current-user() eq $name) then
                session:set-attribute("password", $pass)
            else (),
            users:display()
            )
};

declare function users:new-group() as element()* {
    let $group := request:get-parameter("group", ())
    let $manager := request:get-parameter("manager", ())
    return
        if (empty($manager)) then
            (
            <div class="error">Please specify a manager.</div>,
            users:correct-group(-1, $group, $manager)
            )
        else
            (
            xdb:create-group($group, $manager),
            users:display()
            )
};

declare function users:display() as element() {
    let $action := request:get-parameter("action", "")
    return
    <form action="{session:encode-url(request:get-uri())}" method="GET">
        {
        if ($action = ("New User", "Edit User", "New Group")) then ()
        else
            (
            <table cellpadding="5" id="browse">
                <tr>
                    <th colspan="4" align="left">{$action}</th>
                </tr>
                <tr>
                    <th/>
                    <th>Name</th>
                    <th>Groups</th>
                    <th>Home</th>
                </tr>
                {
                for $user in $users:current-users
                let $name := $user/config:name/text()
                let $groups := string-join($user/config:group/@name, ", ") (: alternatively, use xmldb:get-user-groups() :)
                let $home := if ($user/config:home ne "") then $user/config:home/text() else <em>not set</em>
                order by $name 
                return
                    <tr>
                        <td><input type="radio" name="uid" value="{$user/@id}"/></td>
                        <td>{$name}</td>
                        <td>{$groups}</td>
                        <td>{$home}</td>
                    </tr>
                }
            </table>
            ,
            <table class="actions">
                <tr>
                    <td>
                        <input type="submit" name="action" value="Edit User"/>
                        <input type="submit" name="action" value="Remove User"/>
                        <input type="submit" name="action" value="New User"/>
                    </td>
                </tr>
            </table>
            ,
            <table cellpadding="5" id="browse">
                <tr>
                    <th/>
                    <th>Group</th>
                    <th>Manager</th>
                </tr>
                {
                for $group in $users:current-groups
                let $name := $group/config:name/text()
                let $manager := $group/config:manager/@name/string()
                order by $name 
                return
                    <tr>
                        <td>{(:{if ($name = ('dba', 'guest')) then () else <input type="radio" name="gid" value="{$group/@id}"/>}:)''}</td>
                        <td>{$name}</td>
                        <td>{if ($name = 'dba') then 'admin' else if ($name = 'guest') then 'guest' else $manager}</td>
                    </tr>
                }
            </table>
            ,
            <table class="actions">
                <tr>
                    <td>
                        <input type="submit" name="action" value="New Group"/>
                    </td>
                </tr>
            </table>

            )
        ,

        let $uid := request:get-parameter("uid", "")
        let $gid := request:get-parameter("gid", "")
        return
            (
            if ($action eq "Edit User" and (: prevent error if no user selected :) $uid ne "") then
                let $user := $users:all-users[@id = $uid] 
                return
                    users:edit-user(xs:integer($uid), xs:string($user/config:name), string-join($user/config:group/@name, ", "), xs:string($user/config:home))
            else if ($action eq "New User") then
                users:edit-user(-1, "", "", ())
            else if ($action eq "New Group") then
                users:edit-group(-1, "", "")
            else if ($action = ("", "Remove User")) then 
                ()
            else
                response:redirect-to(xs:anyURI('admin.xql?panel=users'))
            )
        }
        <input type="hidden" name="panel" value="users"/>
    </form>
};

declare function users:correct-user($uid as xs:integer, $name as xs:string, $groups as xs:string, $home as xs:string?) as element() {
    <form action="{session:encode-url(request:get-uri())}" method="GET">
        { users:edit-user($uid, $name, $groups, $home) }
        <input type="hidden" name="panel" value="users"/>
    </form>
};

declare function users:correct-group($uid as xs:integer, $group as xs:string, $manager) as element() {
    <form action="{session:encode-url(request:get-uri())}" method="GET">
        { users:edit-group($uid, $group, $manager) }
        <input type="hidden" name="panel" value="users"/>
    </form>
};

declare function users:edit-user($uid as xs:integer, $name as xs:string, $groups as xs:string, $home as xs:string?) as element() {
    <table border="0" cellspacing="5">
        <tr>
            <th colspan="3" align="left">{request:get-parameter('action', '')}</th>
        </tr>
        <tr>
            <td>User:</td>
            {
            if ($name eq "") then
                <td colspan="2"><input type="text" name="name"/></td>
            else
                <td colspan="2"><input type="hidden" name="name" value="{$name}"/>{$name}</td>
            }
        </tr>
        <tr>
            <td>Groups:</td>
            <td><ul style="list-style: none; padding:0px">{
                for $group in $users:current-groups/config:name
                let $selected := $group = tokenize($groups, '\s?,\s?')
                return
                    <li>
                        <input type="checkbox" name="groups" value="{$group}" id="{$group}"/> {if ($selected) then attribute checked {'checked'} else ()}
                        <label for="{$group}" style="display: inline; float: none">{$group/text()}</label>
                    </li>
            }</ul></td>
            <td class="help">Warning: Do not modify admin or guest group settings.</td>
        </tr>
        <tr>
            <td>Password:</td>
            <td colspan="2"><input type="password" name="pass1"/></td>
        </tr>
        <tr>
            <td>Password (again):</td>
            <td colspan="2"><input type="password" name="pass2"/></td>
        </tr>
        {
        if ($uid ge 0) then
            <td colspan="3"><input type="checkbox" name="nopass"/> Leave password unchanged.</td>
        else ()
        }
        <tr>
            <td>Home Collection:</td>
            <td><input type="text" name="home" value="{$home}"/></td>
            <td class="help">Optional: assign a home collection, e.g. /db/home/me.
                The user will be the owner of this collection.
                Note: non-existing collections will be created automatically.</td>
        </tr>
        <tr>
        {
        if ($uid lt 0) then
            <td colspan="3"><input type="submit" name="action" value="Create User"/></td>
        else
            <td><input type="submit" name="action" value="Change User"/></td>
        }
        </tr>
        <tr>
            <td colspan="3">
                <input type="button" value="Cancel" onClick="parent.location='admin.xql?panel=users'"/>
                <input type="hidden" name="uid" value="{$uid}"/>
            </td>
        </tr>
    </table>
};

declare function users:edit-group($uid as xs:integer, $group as xs:string, $manager as xs:string?) as element() {
    <table border="0" cellspacing="5">
        <tr>
            <th colspan="2" align="left">{request:get-parameter('action', '')}</th>
        </tr>
        <tr>
            <td>Group:</td>
            <td colspan="2"><input type="text" name="group"/></td>
        </tr>
        <tr>
            <td>Manager:</td>
            <td><ul style="list-style: none; padding:0px">{
                for $name in $users:current-users/config:name
                let $selected := $name = $manager
                return
                    <li>
                        <input type="radio" name="manager" value="{$name}" id="{$name}"/> {if ($selected) then attribute checked {'checked'} else ()}
                        <label for="{$name}" style="display: inline; float: none">{$name/text()}</label>
                    </li>
            }</ul></td>
            <td class="help">Every group needs a manager.</td>
        </tr>
        <tr>
            <td colspan="3"><input type="submit" name="action" value="Create Group"/></td>
        </tr>
        <tr>
            <td colspan="3"><input type="button" value="Cancel" onClick="parent.location='admin.xql?panel=users'"/></td>
        </tr>
    </table>
};