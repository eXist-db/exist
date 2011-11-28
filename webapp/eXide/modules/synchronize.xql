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
return
    local:format-output($output)