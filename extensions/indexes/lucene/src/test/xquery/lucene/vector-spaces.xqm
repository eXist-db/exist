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
 : A vector field is searched in the space of the query vector's dimension. The same field name may
 : be configured at different dimensions in different collections, and a query vector of a dimension
 : no collection has the field at is an error. https://github.com/eXist-db/exist/issues/6977
 :)
module namespace t="http://exist-db.org/xquery/test/vector-spaces";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $t:ROOT := "/db/test-vector-spaces";
declare variable $t:DOCS := 3;
declare variable $t:V4 := [1.0, 0.0, 0.0, 0.0];
declare variable $t:V3 := [1.0, 0.0, 0.0];
declare variable $t:V5 := [1.0, 0.0, 0.0, 0.0, 0.0];

declare function t:xconf($fields as element()*) as element() {
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="article">{ $fields }</text>
            </lucene>
        </index>
    </collection>
};

declare function t:field($name as xs:string, $expression as xs:string, $dimension as xs:integer) as element() {
    <vector-field xmlns="http://exist-db.org/collection-config/1.0" name="{$name}" expression="{$expression}"
        dimension="{$dimension}" similarity="cosine" encoding="text"/>
};

(: a collection under /db with its own configuration; $articles are stored $t:DOCS times :)
declare function t:collection($path as xs:string, $fields as element()*, $article as element()) {
    let $name := substring-after($path, "/db/")
    let $_ := xmldb:create-collection("/db", $name)
    let $_ := xmldb:create-collection("/db/system/config/db", $name)
    let $_ := xmldb:store("/db/system/config" || $path, "collection.xconf", t:xconf($fields))
    for $i in 1 to $t:DOCS
    return xmldb:store($path, "a" || $i || ".xml", $article)
};

declare
    %test:setUp
function t:setup() {
    let $_ := xmldb:create-collection("/db/system", "config")
    let $_ := xmldb:create-collection("/db/system/config", "db")
    (: one name, two dimensions, in two collections :)
    let $_ := (
        t:collection($t:ROOT || "-dims-four", t:field("embedding", "embedding", 4),
            <article src="four"><embedding>1.0 0.0 0.0 0.0</embedding></article>),
        t:collection($t:ROOT || "-dims-three", t:field("embedding", "embedding", 3),
            <article src="three"><embedding>1.0 0.0 0.0</embedding></article>)
    )
    for $layout in ("dims-four", "dims-three")
    where xmldb:collection-available($t:ROOT || "-" || $layout)
    return xmldb:reindex($t:ROOT || "-" || $layout)
};

declare
    %test:tearDown
function t:teardown() {
    for $layout in ("dims-four", "dims-three")
    let $path := $t:ROOT || "-" || $layout
    return (
        if (xmldb:collection-available($path)) then xmldb:remove($path) else (),
        if (xmldb:collection-available("/db/system/config" || $path)) then xmldb:remove("/db/system/config" || $path) else ()
    )
};

(: the collections that hold the field "embedding" at two dimensions, searched together :)
declare function t:both() as document-node()* {
    collection($t:ROOT || "-dims-four") | collection($t:ROOT || "-dims-three")
};

declare
    %test:assertEquals("four")
function t:a-vector-of-four-dimensions-finds-the-collection-at-four() {
    distinct-values(t:both()//article[ft:query-vector(., $t:V4, 10)]/@src/string())
};

declare
    %test:assertEquals("three")
function t:a-vector-of-three-dimensions-finds-the-collection-at-three() {
    distinct-values(t:both()//article[ft:query-vector(., $t:V3, 10)]/@src/string())
};

declare
    %test:assertEquals("four")
function t:the-field-function-picks-the-space-too-four() {
    distinct-values(t:both()//article[ft:query-field-vector("embedding", $t:V4, 10)]/@src/string())
};

declare
    %test:assertEquals("three")
function t:the-field-function-picks-the-space-too-three() {
    distinct-values(t:both()//article[ft:query-field-vector("embedding", $t:V3, 10)]/@src/string())
};

(: k counts the nodes of the space, not of the field name :)
declare
    %test:assertEquals(2)
function t:k-counts-the-nodes-of-the-space() {
    count(t:both()//article[ft:query-vector(., $t:V3, 2)])
};

declare
    %test:assertError("EXXQDYFT0006")
function t:a-dimension-no-collection-has-is-an-error() {
    t:both()//article[ft:query-vector(., $t:V5, 10)]
};

declare
    %test:assertError("EXXQDYFT0006")
function t:a-dimension-no-collection-has-is-an-error-for-the-field-function() {
    t:both()//article[ft:query-field-vector("embedding", $t:V5, 10)]
};

(: an unknown field stays empty, it is not a dimension error :)
declare
    %test:assertEquals(0)
function t:an-unknown-field-is-empty() {
    count(t:both()//article[ft:query-field-vector("no-such-field", $t:V4, 10)])
};

declare
    %test:assertEquals(3)
function t:each-collection-alone-at-its-dimension() {
    count(collection($t:ROOT || "-dims-three")//article[ft:query-vector(., $t:V3, 10)])
};
