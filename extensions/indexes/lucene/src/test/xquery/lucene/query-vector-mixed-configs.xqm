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
 : ft:query-vector over a parent collection whose sub-collections configure their vector fields
 : differently: on different elements, or under different names. Each sub-collection's vectors
 : must be found, in whichever order the sub-collections come.
 :)
module namespace t="http://exist-db.org/xquery/test/query-vector-mixed-configs";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $t:ROOT := "/db/test-query-vector-mixed";
declare variable $t:DOCS := 4;
declare variable $t:VEC := [1.0, 0.0, 0.0, 0.0];

declare function t:xconf($qname as xs:string, $field as xs:string) as element() {
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="{$qname}">
                    <vector-field name="{$field}" expression="embedding" dimension="4" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>
};

(: a sub-collection of $parent whose vector field $field is on the element $qname :)
declare function t:sub-collection($parent as xs:string, $name as xs:string, $qname as xs:string, $field as xs:string) {
    let $coll := xmldb:create-collection($parent, $name)
    let $conf := xmldb:create-collection("/db/system/config" || $parent, $name)
    let $_ := xmldb:store($conf, "collection.xconf", t:xconf($qname, $field))
    for $i in 1 to $t:DOCS
    return xmldb:store($coll, $name || $i || ".xml",
        if ($qname eq "div") then
            <doc><body><div><embedding>1.0 0.0 0.0 0.0</embedding></div></body></doc>
        else
            <doc><body><embedding>1.0 0.0 0.0 0.0</embedding></body></doc>)
};

declare
    %test:setUp
function t:setup() {
    let $_ := xmldb:create-collection("/db/system/config", "db")
    for $layout in (
        (: the element differs; the sub-collections come in either order :)
        ["divs-bodies", ["divs", "div", "embedding"], ["bodies", "body", "embedding"]],
        ["bodies-divs", ["z-divs", "div", "embedding"], ["a-bodies", "body", "embedding"]],
        (: the field name differs, on the same element :)
        ["two-names", ["first", "div", "embedding-a"], ["second", "div", "embedding-b"]]
    )
    let $parent := xmldb:create-collection("/db", substring-after($t:ROOT, "/db/") || "-" || $layout(1))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($parent, "/db/"))
    let $_ := (t:sub-collection($parent, $layout(2)(1), $layout(2)(2), $layout(2)(3)),
               t:sub-collection($parent, $layout(3)(1), $layout(3)(2), $layout(3)(3)))
    return xmldb:reindex($parent)
};

declare
    %test:tearDown
function t:teardown() {
    for $layout in ("divs-bodies", "bodies-divs", "two-names")
    let $parent := $t:ROOT || "-" || $layout
    return (
        xmldb:remove($parent),
        xmldb:remove("/db/system/config" || $parent)
    )
};

declare
    %test:assertEquals(4, 4)
function t:element-differs() {
    let $parent := $t:ROOT || "-divs-bodies"
    return (
        count(collection($parent)//div[ft:query-vector(., $t:VEC, 10)]),
        count(collection($parent)//body[ft:query-vector(., $t:VEC, 10)])
    )
};

declare
    %test:assertEquals(4, 4)
function t:element-differs-other-order() {
    let $parent := $t:ROOT || "-bodies-divs"
    return (
        count(collection($parent)//div[ft:query-vector(., $t:VEC, 10)]),
        count(collection($parent)//body[ft:query-vector(., $t:VEC, 10)])
    )
};

declare
    %test:assertEquals(8)
function t:name-differs() {
    count(collection($t:ROOT || "-two-names")//div[ft:query-vector(., $t:VEC, 10)])
};

declare
    %test:assertEquals(4, 4)
function t:each-sub-collection-alone() {
    let $parent := $t:ROOT || "-divs-bodies"
    return (
        count(collection($parent || "/divs")//div[ft:query-vector(., $t:VEC, 10)]),
        count(collection($parent || "/bodies")//body[ft:query-vector(., $t:VEC, 10)])
    )
};
