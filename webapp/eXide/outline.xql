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

declare option exist:serialize "method=json indent=yes";

(:~
 : Resolve imported modules and return the signature of all functions and
 : variables to be displayed in the outline view
 :)
<functions xmlns:json='http://json.org'>
{
    let $uris := request:get-parameter("uri", ())
    let $sources := request:get-parameter("source", ())
    let $prefixes := request:get-parameter("prefix", ())
    let $base := request:get-parameter("base", ())
    for $uri at $i in $uris
    let $source := if (starts-with($sources[$i], "/")) then $sources[$i] else concat($base, "/", $sources[$i])
    return
            util:catch("*",
				let $log := util:log("DEBUG", ("Importing module ", $source))
                let $import := util:import-module($uri, concat("pfx", $i), $source)
                let $prefix := $prefixes[$i]
                return
                    <modules json:array='true' source='{$source}'>
                    {
                        for $func in util:registered-functions($uri)
                        (: fix namespace prefix to match the one in the import :)
                        let $name := concat($prefix, ":", substring-after($func, ":"))
                        return
                            <functions json:array="true">{$name}</functions>
                    }
                    {
                        for $var in util:declared-variables($uri)
                        (: fix namespace prefix to match the one in the import :)
                        let $name := concat($prefix, ":", substring-after($var, ":"))
                        return
                            <variables json:array="true">{$name}</variables>
                    }
                    </modules>,
                ()
            )
}
</functions>
