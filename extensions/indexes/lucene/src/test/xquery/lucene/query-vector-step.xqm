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
 : ft:query-vector in a predicate searches the elements its step selects, whichever step of the path it is on,
 : and gives the same hits as the argument form. See https://github.com/eXist-db/exist/issues/6936
 :
 : The vector of each item and h:body moves away from the query vector as n grows, so the 3 nearest are n = 1, 2, 3.
 :)
module namespace qvs="http://exist-db.org/xquery/lucene/query-vector-step/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace h="http://www.w3.org/1999/xhtml";

declare variable $qvs:COLLECTION_NAME := "test-query-vector-step";
declare variable $qvs:COLLECTION := "/db/" || $qvs:COLLECTION_NAME;
declare variable $qvs:CONF := "/db/system/config/db/" || $qvs:COLLECTION_NAME;
declare variable $qvs:VEC := [1, 0, 0];

declare variable $qvs:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:h="http://www.w3.org/1999/xhtml">
        <index>
            <lucene>
                <text qname="item">
                    <vector-field name="qvs-emb" expression="embedding" dimension="3" similarity="cosine" encoding="text"/>
                </text>
                <text qname="h:body">
                    <vector-field name="qvs-emb" expression="h:embedding" dimension="3" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function qvs:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $qvs:COLLECTION_NAME),
    xmldb:store($qvs:CONF, "collection.xconf", $qvs:XCONF),
    xmldb:create-collection("/db", $qvs:COLLECTION_NAME),
    for $i in 1 to 10
    return (
        xmldb:store($qvs:COLLECTION, "d" || $i || ".xml",
            <wrap><item n="{$i}"><embedding>{"1 " || $i div 10 || " 0"}</embedding></item></wrap>),
        xmldb:store($qvs:COLLECTION, "h" || $i || ".xml",
            <html xmlns="http://www.w3.org/1999/xhtml">
                <body n="{$i}"><embedding>{"1 " || $i div 10 || " 0"}</embedding></body>
            </html>)
    )
};

declare
    %test:tearDown
function qvs:tearDown() {
    xmldb:remove($qvs:COLLECTION),
    xmldb:remove($qvs:CONF)
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:descendant-step() {
    sort(collection($qvs:COLLECTION)//item[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:child-step() {
    sort(collection($qvs:COLLECTION)/wrap/item[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:descendant-after-child-step() {
    sort(collection($qvs:COLLECTION)/wrap//item[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:child-step-after-wildcard() {
    sort(collection($qvs:COLLECTION)/*/item[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:namespaced-child-step() {
    sort(collection($qvs:COLLECTION)/h:html/h:body[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:namespaced-descendant-after-child-step() {
    sort(collection($qvs:COLLECTION)/h:html//h:body[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:argument-form() {
    sort(ft:query-vector(collection($qvs:COLLECTION)/wrap/item, $qvs:VEC, 3)/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:namespaced-argument-form() {
    sort(ft:query-vector(collection($qvs:COLLECTION)/h:html/h:body, $qvs:VEC, 3)/@n/string())
};
