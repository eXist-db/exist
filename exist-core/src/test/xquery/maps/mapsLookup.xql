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

module namespace mlt="http://exist-db.org/xquery/test/maps_lookup";

declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:assertEquals("Jenna")
function mlt:postfix_lookup_on_map() {
    map { "first" : "Jenna", "last" : "Scott" }?first
};

declare
    %test:assertEqualsPermutation("Jenna", "Scott")
function mlt:wildcard_lookup_on_map() {
    map { "first" : "Jenna", "last" : "Scott" }?*
};

declare
    %test:assertEquals("Tom", "Dick", "Harry")
function mlt:postfix_lookup_on_maps() {
    (
        map {"first": "Tom"},
        map {"first": "Dick"},
        map {"first": "Harry"}
    )?first
};

declare
    %test:assertEquals("null", "null")
function mlt:null_lookup() {
    let $serializationParams :=
        <output:serialization-parameters>
            <output:method>json</output:method>
        </output:serialization-parameters>

    let $json1 := parse-json('{"total":[{"data":null}]}')
    let $test1 := $json1?total?*?foobar

    let $json2 := parse-json('{"total":[{"data":null}]}')
    let $test2 :=  $json2?total?*?foobar?aaa

    return (
        serialize($test1, $serializationParams),
        serialize($test2, $serializationParams)
    )
};

declare
    %test:assertEquals("err:XPDY0002", "1", "1")
function mlt:wildcard-lookup-without-context () {
    try {
        util:eval("?noctx", false(), (), true())
    } catch * {
        xs:string($err:code), $err:line-number, $err:column-number
    }
};
