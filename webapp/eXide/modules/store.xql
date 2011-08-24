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

(:~ Called by the editor to store a document :)

let $path := request:get-parameter("path", ())
let $split := text:groups($path, "^(.*)/([^/]+)$")
let $collection := $split[2]
let $resource := $split[3]
let $mime := request:get-parameter("mime", ())
let $data := request:get-parameter("data", ())
return
    util:catch("*",
        let $null :=
            if ($mime) then
                xmldb:store(xmldb:encode-uri($collection), xmldb:encode-uri($resource), $data, $mime)
            else
                xmldb:store(xmldb:encode-uri($collection), xmldb:encode-uri($resource), $data)
        return
            <message status="ok"/>,
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
