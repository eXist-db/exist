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

import module namespace config="http://exist-db.org/xquery/apps/config" at "config.xqm";

declare namespace t="http://exist-db.org/xquery/apps/transform";

declare option exist:serialize "method=html5 media-type=text/html";

declare function t:transform($node as node()) {
    typeswitch ($node)
    case element() return
        switch ($node/@id)
            case "app-info" return
                config:app-info($node)
            default return
                element { node-name($node) } {
                    $node/@*, for $child in $node/node() return t:transform($child)
                }
    default return
        $node
};

let $input := request:get-data()/element()
return
    t:transform($input)