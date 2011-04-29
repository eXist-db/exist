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

import module namespace file="http://exist-db.org/xquery/file" at "java:org.exist.xquery.modules.file.FileModule";

declare function local:format-output($output) {
    if ($output//file:update) then
        <table>
            <tr>
                <th>Collection</th>
                <th>Resource</th>
            </tr>
        {
            for $update in $output//file:update
            return
               <tr>
                    <td>{$update/@collection/string()}</td>
                    <td>{$update/@name/string()}</td>
                    <td>{$update/file:error/string()}</td>
                </tr>
        }
        </table>
    else
        <p>All resources are up to date.</p>
};

let $startParam := request:get-parameter("start", ())
let $startTime := if (empty($startParam) or $startParam eq "") then () else $startParam 
let $collection := request:get-parameter("collection", ())
let $dir := request:get-parameter("dir", ())
let $output := file:sync($collection, $dir, $startTime)
let $log := util:log("DEBUG", ("OUT: ", $output))
return
    local:format-output($output)