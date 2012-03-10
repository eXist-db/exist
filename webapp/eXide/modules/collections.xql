(:
 :  eXide - web-based XQuery IDE
 :  
 :  Copyright (C) 2011 Wolfgang Meier
 :
 :  This program is free software: you can redistribute it and/or modify
 :  it under the terms of the GNU General Public License as published by
 :  the Free Software Foundation, either version 3 of the License, or
 :  (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU General Public License for more details.
 :
 :  You should have received a copy of the GNU General Public License
 :  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 :)
xquery version "3.0";

declare namespace json="http://www.json.org";

declare option exist:serialize "method=json media-type=text/javascript";

import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "dates.xqm";

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
	if (xmldb:is-admin-user($user)) then
		true()
	else
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
	if (xmldb:is-admin-user($user)) then
		true()
	else
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
    if (xmldb:is-admin-user($user)) then
		true()
	else
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
    if (sm:has-access(xs:anyURI($root), "x")) then
        let $children := xmldb:get-child-collections($root)
        let $canWrite := 
            (: local:canWrite($root, $user) :)
            sm:has-access(xs:anyURI($root), "w")
        return
            if (sm:has-access(xs:anyURI($root), "r")) then (
                <title>{xmldb:decode-uri(xs:anyURI($child))}</title>,
                <isFolder json:literal="true">true</isFolder>,
                <key>{xmldb:decode-uri(xs:anyURI($root))}</key>,
                <writable json:literal="true">{if ($canWrite) then 'true' else 'false'}</writable>,
                <addClass>{if ($canWrite) then 'writable' else 'readable'}</addClass>,
            	if (exists($children)) then
                    local:sub-collections($root, $children, $user)
            	else
                ()
            ) else
                ()  
    else
        ()
};

declare function local:list-collection-contents($collection as xs:string, $user as xs:string) {
    let $subcollections := 
        for $child in xmldb:get-child-collections($collection)
        where sm:has-access(xs:anyURI(concat($collection, "/", $child)), "r")
        return
            concat("/", $child)
    let $resources :=
        for $r in xmldb:get-child-resources($collection)
        where sm:has-access(xs:anyURI(concat($collection, "/", $r)), "r")
        return
            $r
    for $resource in ($subcollections, $resources)
	order by $resource ascending
	return
		$resource
};

declare function local:resources($collection as xs:string, $user as xs:string) {
    let $start := number(request:get-parameter("start", 0)) + 1
    let $endParam := number(request:get-parameter("end", 1000000)) + 1
    let $resources := local:list-collection-contents($collection, $user)
    let $end := if ($endParam gt count($resources)) then count($resources) else $endParam
    let $subset := subsequence($resources, $start, $end - $start + 1)
    return
        <json:value>
            <total json:literal="true">{count($resources)}</total>
            <items>
        {
            for $resource in $subset
            let $isCollection := starts-with($resource, "/")
            let $path := 
                if ($isCollection) then
                    concat($collection, $resource)
                else
                    concat($collection, "/", $resource)
            where sm:has-access(xs:anyURI($path), "r")
            order by $resource ascending
            return
                let $permissions := 
                    if ($isCollection) then
                        xmldb:permissions-to-string(xmldb:get-permissions($path))
                    else
                        xmldb:permissions-to-string(xmldb:get-permissions($collection, $resource))
                let $owner := 
                    if ($isCollection) then
                        xmldb:get-owner($path)
                    else
                        xmldb:get-owner($collection, $resource)
                let $group :=
                    if ($isCollection) then
                        xmldb:get-group($path)
                    else
                        xmldb:get-group($collection, $resource)
                let $lastMod := 
                    if ($isCollection) then
                        date:format-dateTime(xmldb:created($path))
                    else
                        date:format-dateTime(xmldb:created($collection, $resource))
                let $canWrite :=
                    if ($isCollection) then
                        local:canWrite(concat($collection, "/", $resource), $user)
                    else
                        local:canWriteResource($collection, $resource, $user)
                return
                    <json:value json:array="true">
                        <name>{xmldb:decode-uri(if ($isCollection) then substring-after($resource, "/") else $resource)}</name>
                        <permissions>{$permissions}</permissions>
                        <owner>{$owner}</owner>
                        <group>{$group}</group>
                        <last-modified>{$lastMod}</last-modified>
                        <writable json:literal="true">{$canWrite}</writable>
                        <isCollection json:literal="true">{$isCollection}</isCollection>
                    </json:value>
        }
            </items>
        </json:value>
};

declare function local:create-collection($collName as xs:string, $user as xs:string) {
    let $parent := xmldb:encode-uri(request:get-parameter("collection", "/db"))
    return
        if (local:canWrite($parent, $user)) then
            let $null := xmldb:create-collection($parent, $collName)
            return
                <response status="ok"/>
        else
            <response status="fail">
                <message>You are not allowed to write to collection {xmldb:decode-uri(xs:anyURI($parent))}</message>
            </response>
};

declare function local:delete-collection($collName as xs:string, $user as xs:string) {
    if (local:canWrite($collName, $user)) then
        let $null := xmldb:remove($collName)
        return
            <response status="ok"/>
    else
        <response status="fail">
            <message>You are not allowed to write to collection {xmldb:decode-uri(xs:anyURI($collName))}</message>
        </response>
};

declare function local:delete-resource($collection as xs:string, $resources as xs:string+, $user as xs:string) {
    let $canWrite :=
        every $resource in $resources
        satisfies
            local:canWriteResource($collection, $resource, $user)
    return
    if ($canWrite) then
        let $null :=
            for $resource in $resources
            return xmldb:remove($collection, $resource)
        return
            <response status="ok"/>
    else
        <response status="fail">
            <message>You are not allowed to delete the resources {string-join($resources, ", ")} in collection {xmldb:decode-uri(xs:anyURI($collection))}</message>
        </response>
};

declare function local:delete($collection as xs:string, $selection as xs:string+, $user as xs:string) {
    for $docOrColl in $selection
    let $path :=
        if (starts-with($docOrColl, "/")) then
            $docOrColl
        else
            $collection || "/" || $docOrColl
    let $isCollection := xmldb:collection-available($path)
    let $response :=
        if (exists($isCollection)) then
            local:delete-collection($path, $user)
        else
            local:delete-resource($collection, $docOrColl, $user)
    where $response/@status = "ok"
    return
        $response
};

declare function local:copyOrMove($operation as xs:string, $target as xs:string, $sources as xs:string+, 
    $user as xs:string) {
    if (local:canWrite($target, $user)) then
        for $source in $sources
        let $isCollection := xmldb:collection-available($source)
        return
            try {
                if ($isCollection) then
                    let $null := 
                        switch ($operation)
                            case "move" return
                                xmldb:move($source, $target)
                            default return
                                xmldb:copy($source, $target)
                    return
                        <response status="ok"/>
                else
                    let $split := text:groups($source, "^(.*)/([^/]+)$")
                    let $null := 
                        switch ($operation)
                            case "move" return
                                xmldb:move($split[2], $target, $split[3])
                            default return
                                xmldb:copy($split[2], $target, $split[3])
                    return
                        <response status="ok"/>
            } catch * {
                <response status="fail">
                    <message>{ $err:description }</message>
                </response>
            }
    else
        <response status="fail">
            <message>You are not allowed to write to collection {xmldb:decode-uri(xs:anyURI($target))}</message>
        </response>
};

let $deleteCollection := request:get-parameter("remove", ())
let $deleteResource := request:get-parameter("remove[]", ())
let $copy := request:get-parameter("copy[]", ())
let $move := request:get-parameter("move[]", ())
let $createCollection := request:get-parameter("create", ())
let $view := request:get-parameter("view", "c")
let $collection := request:get-parameter("root", "/db")
let $collName := replace($collection, "^.*/([^/]+$)", "$1")
let $user := if (session:get-attribute('myapp.user')) then session:get-attribute('myapp.user') else "guest"
return
    if (exists($copy)) then
        let $result := local:copyOrMove("copy", xmldb:encode-uri($collection), $copy, $user)
        return
            ($result[@status = "fail"], $result[1])[1]
    else if (exists($move)) then
        let $result := local:copyOrMove("move", xmldb:encode-uri($collection), $move, $user)
        return
            ($result[@status = "fail"], $result[1])[1]
    else if (exists($deleteResource)) then
        local:delete(xmldb:encode-uri($collection), $deleteResource, $user)
    else if ($createCollection) then
        local:create-collection(xmldb:encode-uri($createCollection), $user)
    else if ($view eq "c") then
        <collection json:array="true">
            {local:collections(xmldb:encode-uri($collection), xmldb:encode-uri($collName), $user)}
        </collection>
    else
        local:resources(xmldb:encode-uri($collection), $user)
