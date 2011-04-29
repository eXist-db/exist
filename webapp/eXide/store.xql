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
                xmldb:store($collection, $resource, $data, $mime)
            else
                xmldb:store($collection, $resource, $data)
        return
            (),
        let $message :=
            replace(
                replace($util:exception-message, "^.*XMLDBException:", ""),
                "\[at.*\]$", ""
            )
        return
            <error>
                <message>{$message}</message>
            </error>
    )
