(:
 :  eXist Open Source Native XML Database
 :  Copyright (C) 2011 The eXist Project
 :  http://exist-db.org
 :
 :  This program is free software; you can redistribute it and/or
 :  modify it under the terms of the GNU Lesser General Public License
 :  as published by the Free Software Foundation; either version 2
 :  of the License, or (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU Lesser General Public License for more details.
 :
 :  You should have received a copy of the GNU Lesser General Public
 :  License along with this library; if not, write to the Free Software
 :  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 :
 :  $Id$
 :)
xquery version "1.0";

declare namespace json="http://www.json.org";

declare option exist:serialize "method=json media-type=text/javascript";

import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "../admin/dates.xqm";

declare function local:sub-collections($root as xs:string, $children as xs:string*, $user as xs:string) {
        for $child in $children
		let $processChild := 
			local:collections(concat($root, '/', $child), $child, $user)
		where exists($processChild)
		order by $child ascending
        return
            <children json:array="true">
			{ $processChild }
			</children>
};

declare function local:canRead($collection as xs:string, $user as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 1, 1) eq 'r'
        else if ($group = $groups) then
            substring($permissions, 4, 1) eq 'r'
        else
            substring($permissions, 7, 1) eq 'r'
};

declare function local:canWrite($collection as xs:string, $user as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 2, 1) eq 'w'
        else if ($group = $groups) then
            substring($permissions, 5, 1) eq 'w'
        else
            substring($permissions, 8, 1) eq 'w'
};

declare function local:canWriteResource($collection as xs:string, $resource as xs:string, $user as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection, $resource))
    let $owner := xmldb:get-owner($collection, $resource)
    let $group := xmldb:get-group($collection, $resource)
    let $groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 2, 1) eq 'w'
        else if ($group = $groups) then
            substring($permissions, 5, 1) eq 'w'
        else
            substring($permissions, 8, 1) eq 'w'
};

declare function local:collections($root as xs:string, $child as xs:string, 
	$user as xs:string) {
    let $children := xmldb:get-child-collections($root)
    let $canWrite := local:canWrite($root, $user)
    return
        if (local:canRead($root, $user)) then (
            <title>{$child}</title>,
            <isFolder json:literal="true">true</isFolder>,
            <key>{$root}</key>,
            <writable json:literal="true">{if ($canWrite) then 'true' else 'false'}</writable>,
            <addClass>{if ($canWrite) then 'writable' else 'readable'}</addClass>,
            	if (exists($children)) then
                local:sub-collections($root, $children, $user)
            	else
                ()
        ) else
            ()
};

declare function local:resources($collection as xs:string, $user as xs:string) {
    <json:value>
    {
        let $resources := xmldb:get-child-resources($collection)
        for $resource in $resources
        let $path := concat($collection, "/", $resource)
        let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection, $resource))
        let $owner := xmldb:get-owner($collection, $resource)
        let $group := xmldb:get-group($collection, $resource)
        let $lastMod := date:format-dateTime(xmldb:created($collection, $resource))
        let $canWrite := local:canWriteResource($collection, $resource, $user)
        order by $resource ascending
        return
            <json:value json:array="true">
                <json:value>{$resource}</json:value>
                <json:value>{$permissions}</json:value>
                <json:value>{$owner}</json:value>
                <json:value>{$group}</json:value>
                <json:value>{$lastMod}</json:value>
                <json:value json:literal="true">{$canWrite}</json:value>
            </json:value>
    }
    </json:value>
};

declare function local:create-collection($collName as xs:string, $user as xs:string) {
    let $parent := request:get-parameter("collection", "/db")
    return
        if (local:canWrite($parent, $user)) then
            let $null := xmldb:create-collection($parent, $collName)
            return
                <response status="ok"/>
        else
            <response status="fail">
                <message>You are not allowed to write to collection {$parent}</message>
            </response>
};

let $createCollection := request:get-parameter("create", ())
let $view := request:get-parameter("view", "c")
let $collection := request:get-parameter("root", "/db")
let $collName := replace($collection, "^.*/([^/]+$)", "$1")
let $user := if (session:get-attribute('myapp.user')) then session:get-attribute('myapp.user') else "guest"
return
    if ($createCollection) then
        local:create-collection($createCollection, $user)
    else if ($view eq "c") then
        <collection json:array="true">
        {local:collections($collection, $collName, $user)}
        </collection>
    else
        local:resources($collection, $user)
