(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

import module namespace inspect = "http://exist-db.org/xquery/inspection";
import module namespace util = "http://exist-db.org/xquery/util";

declare variable $test-module-uri as xs:anyURI external;
declare variable $test-namespace := "http://exist-db.org/xquery/xqsuite";

let $all-functions := inspect:module-functions($test-module-uri)
let $test-functions :=
    for $f in $all-functions
    let $meta := util:inspect-function($f)
    where exists($meta/annotation[@namespace = $test-namespace][matches(@name, ":assert")])
    return $f
return
    <discovery>
        {
            if (exists($test-functions)) then
                let $first := $test-functions[1]
                let $name := function-name($first)
                return (
                    attribute namespace { namespace-uri-from-QName($name) },
                    attribute prefix { prefix-from-QName($name) },
                    for $f in $test-functions
                    return element f {
                        attribute name { local-name-from-QName(function-name($f)) },
                        attribute arity { function-arity($f) }
                    }
                )
            else
                ()
        }
    </discovery>
