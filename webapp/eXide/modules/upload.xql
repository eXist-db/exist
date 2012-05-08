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

declare namespace expath="http://expath.org/ns/pkg";

declare option exist:serialize "method=json media-type=application/json";

declare function local:get-descriptors($collection, $name) {
    let $dataCb := function($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) { $data }
    let $entryCb := function($path as xs:anyURI, $type as xs:string, $param as item()*) { $path = "expath-pkg.xml" }
    return
        compression:unzip(util:binary-doc(concat($collection, "/", $name)), $entryCb, (), $dataCb, ())
};

declare function local:deploy($collection, $name) {
let $deploy := request:get-parameter("deploy", ())
return
    if ($deploy and ends-with($name, ".xar")) then 
        let $descriptors := local:get-descriptors($collection, $name)
        let $port := request:get-server-port()
        let $url := concat('http://localhost:',$port,'/exist/rest',$collection, "/", $name)
        let $appName := $descriptors/expath:package/@name
        return (
            repo:remove($appName),
            repo:install($url),
            repo:deploy($appName)
        )
    else
        ()
};

declare function local:mkcol-recursive($collection, $components) {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xmldb:create-collection($collection, $components[1]),
            local:mkcol-recursive($newColl, subsequence($components, 2))
        )
    else
        ()
};

(: Helper function to recursively create a collection hierarchy. :)
declare function local:mkcol($collection, $path) {
    local:mkcol-recursive($collection, tokenize($path, "/"))[last()]
};

declare function local:store($root as xs:string, $path as xs:string, $data) {
    if (matches($path, "/[^/]+$")) then
        let $split := text:groups($path, "^(.*)/([^/]+)$")
        let $newCol := local:mkcol($root, $split[2])
        return
            xmldb:store($newCol, $split[3], $data)
    else
        xmldb:store($root, $path, $data)
};

declare function local:upload($collection, $path, $data) {
    let $path := local:store($collection, $path, $data)
    let $upload :=
        <result>
           <name>{$path}</name>
           <type>{xmldb:get-mime-type($path)}</type>
           <size>93928</size>
       </result>
    let $deploy := local:deploy($collection, $path)
    return
        $upload
};

let $collection := request:get-parameter("collection", ())
let $name := request:get-uploaded-file-name("file[]")
let $data := request:get-uploaded-file-data("file[]")
return
    util:catch("*",
        local:upload(xmldb:encode-uri($collection), $name, $data),
        <result>
           <name>{$name}</name>
           <error>{$util:exception-message}</error>
        </result>
   )
