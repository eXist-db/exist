xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

let $uri := request:get-uri()
let $context := request:get-context-path()
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
return
    if (starts-with($path, "/admin/backups")) then
        let $user := xdb:get-current-user()
        return
            if (not(xdb:is-admin-user($user))) then
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            		<redirect url="../admin.xql"/>
            	</dispatch>
            else
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                    <forward url="/admin/retrieve-backup.xql">
                        <add-parameter name="archive" value="{$name}"/>
                    </forward>
                </dispatch>
    else if (matches($path, "admin/?$")) then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<redirect url="admin.xql"/>
    	</dispatch>
    else if ($name eq "admin.xql") then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            <forward url="/admin/admin.xql"/>
        </dispatch>
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
    	</ignore>