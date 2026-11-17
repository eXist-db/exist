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
 : The score of a node in a vector query is the best similarity among the entries it owns, whatever k is: its own entry,
 : or else the entries below it. It is never above 1, and k counts nodes.
 :)
module namespace qvo="http://exist-db.org/xquery/lucene/query-vector-ownership/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $qvo:COLLECTION_NAME := "test-query-vector-ownership";
declare variable $qvo:COLLECTION := "/db/" || $qvo:COLLECTION_NAME;
declare variable $qvo:CONF := "/db/system/config/db/" || $qvo:COLLECTION_NAME;
declare variable $qvo:VEC := [1, 0, 0];

declare variable $qvo:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="div">
                    <vector-field name="qvo-emb" expression="embedding" dimension="3" similarity="cosine" encoding="text"/>
                </text>
                <!-- no expression: every leaf has an entry of its own, the search can look among the documents -->
                <text qname="leaf">
                    <vector-field name="qvo-leaf" dimension="3" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

(: a part with two chapters, each with an entry of its own, and a part holding a chapter without one :)
declare
    %test:setUp
function qvo:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $qvo:COLLECTION_NAME),
    xmldb:store($qvo:CONF, "collection.xconf", $qvo:XCONF),
    xmldb:create-collection("/db", $qvo:COLLECTION_NAME),
    xmldb:store($qvo:COLLECTION, "book.xml",
        <book>
            <div n="a"><embedding>1 0.1 0</embedding>
                <div n="a1"><embedding>1 0.5 0</embedding></div>
                <div n="a2"><embedding>1 0.9 0</embedding></div>
            </div>
            <leaf n="l1">1 0.2 0</leaf><leaf n="l2">1 0.6 0</leaf><leaf n="l3">1 0.8 0</leaf>
        </book>)
};

declare
    %test:tearDown
function qvo:tearDown() {
    xmldb:remove($qvo:COLLECTION),
    xmldb:remove($qvo:CONF)
};

declare function qvo:scores($k as xs:integer) as xs:string* {
    for $n in collection($qvo:COLLECTION)//div[ft:query-vector(., $qvo:VEC, $k)]
    order by $n/@n
    return $n/@n || "=" || round(ft:score($n) * 1000) div 1000
};

(: (1 + cosine) div 2 of the nodes' own vectors :)
declare
    %test:assertEquals("a=0.998", "a1=0.947", "a2=0.872")
function qvo:score-is-the-own-entry-at-large-k() {
    qvo:scores(10)
};

declare
    %test:assertEquals("a=0.998", "a1=0.947")
function qvo:score-is-the-own-entry-at-small-k() {
    qvo:scores(2)
};

declare
    %test:assertEquals("a,a1")
function qvo:k-counts-nodes-descendant-step() {
    string-join(collection($qvo:COLLECTION)//div[ft:query-vector(., $qvo:VEC, 2)]/@n, ",")
};

declare
    %test:assertEquals("a,a1")
function qvo:k-counts-nodes-variable() {
    let $all := collection($qvo:COLLECTION)//div
    return string-join($all[ft:query-vector(., $qvo:VEC, 2)]/@n, ",")
};

declare
    %test:assertEquals("a,a1")
function qvo:k-counts-nodes-function-call() {
    string-join(ft:query-vector(collection($qvo:COLLECTION)//div, $qvo:VEC, 2)/@n, ",")
};

declare
    %test:assertEquals("a")
function qvo:one-node-for-k-1() {
    string-join(collection($qvo:COLLECTION)//div[ft:query-vector(., $qvo:VEC, 1)]/@n, ",")
};

declare
    %test:assertEquals("a,a1,a2")
function qvo:three-nodes-for-k-3() {
    string-join(collection($qvo:COLLECTION)//div[ft:query-vector(., $qvo:VEC, 3)]/@n, ",")
};

(: a field without an expression gives every element an entry: the search looks among the documents, and the plain
   search is as exact as ranking the nodes :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED']")
function qvo:a-field-without-an-expression-is-pre-selected() {
    collection($qvo:COLLECTION)//leaf[ft:query-vector(., $qvo:VEC, 2)]
};

declare
    %test:assertEquals("l1", "l2")
function qvo:a-field-without-an-expression-finds-the-nearest() {
    sort(collection($qvo:COLLECTION)//leaf[ft:query-vector(., $qvo:VEC, 2)]/@n/string())
};
