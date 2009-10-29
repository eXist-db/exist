xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

if (starts-with($exist:path, "/backups")) then
    let $user := xdb:get-current-user()
    return
        if (not(xdb:is-admin-user($user))) then
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            	<redirect url="../admin.xql"/>
            </dispatch>
        else
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="/admin/retrieve-backup.xql">
                    <add-parameter name="archive" value="{$exist:resource}"/>
                </forward>
            </dispatch>
        else if ($exist:path = "/") then
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            	<redirect url="admin.xql"/>
            </dispatch>
        else
            <ignore xmlns="http://exist.sourceforge.net/NS/exist">
                <cache-control cache="yes"/>
            </ignore>