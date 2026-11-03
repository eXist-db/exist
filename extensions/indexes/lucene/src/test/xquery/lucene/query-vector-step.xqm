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
 : ft:query-vector in a predicate finds the k nearest of the nodes its step selects, whatever the path's shape, and
 : gives the same hits as the argument form. See https://github.com/eXist-db/exist/issues/6936
 :
 : The vector of each item and h:body moves away from the query vector as n grows, so the 3 nearest are n = 1, 2, 3.
 :
 : A search also looks below the nodes it is given, and a hit there gives the node above it, as it does when asking
 : from an ancestor. So a selected sec or part is ranked by the nearest sec or part below it: those are nearest in the
 : order n = 1, 2, 3, while by their own vectors the outer ones are nearest in the order n = 10, 9, 8.
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
                <text qname="sec">
                    <vector-field name="qvs-emb" expression="embedding" dimension="3" similarity="cosine" encoding="text"/>
                </text>
                <text qname="part">
                    <vector-field name="qvs-emb" expression="embedding" dimension="3" similarity="cosine" encoding="text"/>
                </text>
                <text qname="ch">
                    <vector-field name="qvs-emb" expression="embedding" dimension="3" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare function qvs:vector($i) {
    "1 " || $i div 10 || " 0"
};

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
            <wrap><item n="{$i}"><embedding>{qvs:vector($i)}</embedding></item></wrap>),
        xmldb:store($qvs:COLLECTION, "h" || $i || ".xml",
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head/>
                <body n="{$i}"><embedding>{qvs:vector($i)}</embedding></body>
            </html>),
        xmldb:store($qvs:COLLECTION, "s" || $i || ".xml",
            <root><sec n="{$i}"><embedding>{qvs:vector(21 - $i)}</embedding><sec n="c{$i}"><embedding>{qvs:vector($i)}</embedding></sec></sec></root>),
        xmldb:store($qvs:COLLECTION, "p" || $i || ".xml",
            <part n="{$i}"><embedding>{qvs:vector(21 - $i)}</embedding><part n="c{$i}"><embedding>{qvs:vector($i)}</embedding></part></part>),
        xmldb:store($qvs:COLLECTION, "b" || $i || ".xml",
            <book><ch n="{$i}"><embedding>{qvs:vector($i + 10)}</embedding>{
                if ($i = 1) then
                    for $j in 1 to 3
                    return <ch n="c{$j}"><embedding>{qvs:vector($j)}</embedding></ch>
                else ()
            }</ch></book>)
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

(: a wildcard step's names are those of its nodes :)
declare
    %test:assertEquals("1", "2", "3")
function qvs:wildcard-on-the-step() {
    sort(collection($qvs:COLLECTION)/wrap/*[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:node-test-on-the-step() {
    sort(collection($qvs:COLLECTION)/wrap/node()[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:namespace-wildcard-on-the-step() {
    sort(collection($qvs:COLLECTION)/h:html/h:*[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:local-name-wildcard-on-the-step() {
    sort(collection($qvs:COLLECTION)/*:html/*:body[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:union-of-steps() {
    sort(collection($qvs:COLLECTION)/wrap/(item|embedding)[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

(: the hits are not in or below the nodes such a step starts from :)
declare
    %test:assertEquals("1", "2", "3")
function qvs:parent-step() {
    sort(collection($qvs:COLLECTION)//item/embedding/parent::item[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:ancestor-step() {
    sort(collection($qvs:COLLECTION)//item/embedding/ancestor::item[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:sibling-step() {
    sort(collection($qvs:COLLECTION)/h:html/h:head/following-sibling::h:body[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

(: a selected element is found through the nearest element of its name below it, not by its own vector :)
declare
    %test:assertEquals("1", "2", "3")
function qvs:child-step-over-nested-elements() {
    sort(collection($qvs:COLLECTION)/root/sec[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

declare
    %test:assertEquals("1", "2", "3")
function qvs:first-step-over-nested-elements() {
    sort(collection($qvs:COLLECTION)/part[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};

(: the 3 nearest are all below one selected element, so k hits give that one node :)
declare
    %test:assertEquals("1")
function qvs:hits-below-one-selected-element() {
    collection($qvs:COLLECTION)/book/ch[ft:query-vector(., $qvs:VEC, 3)]/@n/string()
};

declare
    %test:assertEquals("c1", "c2", "c3")
function qvs:descendant-step-over-nested-elements() {
    sort(collection($qvs:COLLECTION)/root//sec[ft:query-vector(., $qvs:VEC, 3)]/@n/string())
};
