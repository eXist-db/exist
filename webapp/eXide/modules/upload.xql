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
declare namespace upload="http://exist-db.org/eXide/upload";

declare option exist:serialize "method=json media-type=application/json";

declare function upload:get-descriptors($zipPath) {
    let $binary := util:binary-doc($zipPath)
    return
        if (exists($binary)) then
            let $dataCb := function($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) { $data }
            let $entryCb := function($path as xs:anyURI, $type as xs:string, $param as item()*) { $path = "expath-pkg.xml" }
            return
                compression:unzip($binary, $entryCb, (), $dataCb, ())
        else
            error(xs:QName("upload:not-found"), "Could not deploy uploaded xar package: " || $zipPath || " not found.")
};

declare function upload:deploy($name) {
let $deploy := request:get-parameter("deploy", ())
return
    if ($deploy and ends-with($name, ".xar")) then 
        let $descriptors := upload:get-descriptors($name)
        let $port := request:get-server-port()
        let $url := concat('http://localhost:',$port,"/exist/rest/", $name)
        let $appName := $descriptors/expath:package/@name
        return (
            repo:remove($appName),
            repo:install($url),
            repo:deploy($appName)
        )
    else
        ()
};

declare function upload:mkcol-recursive($collection, $components) {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xmldb:create-collection($collection, $components[1]),
            upload:mkcol-recursive($newColl, subsequence($components, 2))
        )
    else
        ()
};

(: Helper function to recursively create a collection hierarchy. :)
declare function upload:mkcol($collection, $path) {
    upload:mkcol-recursive($collection, tokenize($path, "/"))[last()]
};

declare function upload:store($root as xs:string, $path as xs:string, $data) {
    if (matches($path, "/[^/]+$")) then
        let $split := text:groups($path, "^(.*)/([^/]+)$")
        let $newCol := upload:mkcol($root, $split[2])
        return
            xmldb:store($newCol, $split[3], $data)
    else
        xmldb:store($root, $path, $data)
};

declare function upload:upload($collection, $path, $data) {
    let $path := upload:store($collection, $path, $data)
    let $upload :=
        <result>
           <name>{$path}</name>
           <type>{xmldb:get-mime-type($path)}</type>
           <size>93928</size>
       </result>
    let $deploy := upload:deploy($path)
    return
        $upload
};

let $collection := request:get-parameter("collection", ())
let $name := request:get-uploaded-file-name("file[]")
let $data := request:get-uploaded-file-data("file[]")
return
    util:catch("*",
        upload:upload(xmldb:encode-uri($collection), $name, $data),
        <result>
           <name>{$name}</name>
           <error>{$util:exception-message}</error>
        </result>
   )
