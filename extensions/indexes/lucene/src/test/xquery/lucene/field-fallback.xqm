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

(:~
 : ft:query-field in a predicate, with the optimizer on, in forms it pre-selects and in
 : forms it doesn't. Every form should find the same paragraph.
 :)
module namespace ffb="http://exist-db.org/xquery/lucene/test/field-fallback";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $ffb:coll := "test-field-fallback";

declare variable $ffb:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="p">
                    <field name="body" expression="."/>
                </text>
                <text qname="x">
                    <field name="xf" expression="."/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function ffb:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", $ffb:coll)
    let $col := xmldb:create-collection("/db", $ffb:coll)
    return (
        xmldb:store($conf, "collection.xconf", $ffb:XCONF),
        xmldb:store($col, "doc.xml", <div><p>alpha</p><p>beta</p></div>),
        xmldb:store($col, "nested.xml", <ctx><b><a><x>gamma</x></a></b><b><x>delta</x></b></ctx>)
    )
};

declare
    %test:tearDown
function ffb:tearDown() {
    xmldb:remove("/db/" || $ffb:coll),
    xmldb:remove("/db/system/config/db/" || $ffb:coll)
};

declare
    %test:assertEquals(1)
function ffb:path() {
    count(collection("/db/" || $ffb:coll)//p[ft:query-field("body", "alpha")])
};

declare
    %test:assertEquals(1)
function ffb:variable() {
    let $ps := collection("/db/" || $ffb:coll)//p
    return count($ps[ft:query-field("body", "alpha")])
};

declare
    %test:assertEquals(1)
function ffb:position-predicate-first() {
    count(collection("/db/" || $ffb:coll)//p[position() le 2][ft:query-field("body", "alpha")])
};

declare
    %test:assertEquals(1)
function ffb:function-argument() {
    count(ffb:filter(collection("/db/" || $ffb:coll)//p))
};

declare %private function ffb:filter($ps as element(p)*) as element(p)* {
    $ps[ft:query-field("body", "alpha")]
};

declare
    %test:assertEquals(1)
function ffb:query-path() {
    count(collection("/db/" || $ffb:coll)//p[ft:query(., "body:alpha")])
};

declare
    %test:assertEquals(1)
function ffb:query-variable() {
    let $ps := collection("/db/" || $ffb:coll)//p
    return count($ps[ft:query(., "body:alpha")])
};

(: a match two levels below the context node: the context is a path, a virtual node set :)
declare
    %test:args("gamma")
    %test:assertEquals(1)
    %test:args("delta")
    %test:assertEquals(1)
function ffb:nested-below-the-context($word as xs:string) {
    count(doc("/db/" || $ffb:coll || "/nested.xml")//b[ft:query-field("xf", $word)])
};

declare
    %test:args("gamma")
    %test:assertEquals(1)
function ffb:nested-below-a-variable($word as xs:string) {
    let $bs := doc("/db/" || $ffb:coll || "/nested.xml")//b
    return count($bs[ft:query-field("xf", $word)])
};
