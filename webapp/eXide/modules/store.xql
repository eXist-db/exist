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
xquery version "1.0";


declare option exist:serialize "method=json media-type=text/javascript";

declare function local:fix-permissions($collection as xs:string, $resource as xs:string) as empty() {
    let $path := concat($collection, "/", $resource)
    let $mime := xmldb:get-mime-type($path)
    return
        if ($mime eq "application/xquery") then
            let $mode := sm:get-permissions($path)/sm:permission/@mode
            let $permissions := xmldb:string-to-permissions(replace($mode, "(..).(..).(..).", "$1x$2x$3x"))
            let $user := xmldb:get-current-user()
            let $group := xmldb:get-user-groups($user)[1]
            return
                xmldb:set-resource-permissions($collection, $resource, $user, $group, $permissions)
        else
            ()
};

(:~ Called by the editor to store a document :)

let $path := request:get-parameter("path", ())
let $split := text:groups($path, "^(.*)/([^/]+)$")
let $collection := xmldb:encode-uri($split[2])
let $resource := xmldb:encode-uri($split[3])
let $mime := request:get-parameter("mime", ())
let $data := request:get-parameter("data", ())
return
    util:catch("*",
        let $path :=
            if ($mime) then
                xmldb:store($collection, $resource, $data, $mime)
            else
                xmldb:store($collection, $resource, $data)
        return (
            local:fix-permissions($collection, $resource),
            <message status="ok"/>
        ),
        let $message :=
            replace(
                replace($util:exception-message, "^.*XMLDBException:", ""),
                "\[at.*\]$", ""
            )
        return
            <error status="error">
                <message>{$message}</message>
            </error>
    )
