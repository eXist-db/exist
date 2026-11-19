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
 : Two vector fields on one element: with one dimension the query vector cannot tell them apart and
 : ft:query-vector is ambiguous, with different dimensions it picks one by its length.
 : https://github.com/eXist-db/exist/issues/6977
 :)
module namespace t="http://exist-db.org/xquery/test/vector-field-pairs";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $t:ROOT := "/db/test-vector-field-pairs";
declare variable $t:DOCS := 3;
declare variable $t:V4 := [1.0, 0.0, 0.0, 0.0];
declare variable $t:V3 := [1.0, 0.0, 0.0];

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
    (: two names of one dimension on one element :)
    let $_ := t:collection($t:ROOT || "-pair",
        (t:field("title-embedding", "title-vec", 4), t:field("body-embedding", "body-vec", 4)),
        <article><title-vec>1.0 0.0 0.0 0.0</title-vec><body-vec>1.0 0.0 0.0 0.0</body-vec></article>)
    (: two names of different dimensions on one element :)
    let $_ := t:collection($t:ROOT || "-mixed",
        (t:field("headline-embedding", "title-vec", 4), t:field("abstract-embedding", "body-vec", 3)),
        <article><title-vec>1.0 0.0 0.0 0.0</title-vec><body-vec>1.0 0.0 0.0</body-vec></article>)
    for $layout in ("pair", "mixed")
    return xmldb:reindex($t:ROOT || "-" || $layout)
};

declare
    %test:tearDown
function t:teardown() {
    for $layout in ("pair", "mixed")
    let $path := $t:ROOT || "-" || $layout
    return (
        if (xmldb:collection-available($path)) then xmldb:remove($path) else (),
        if (xmldb:collection-available("/db/system/config" || $path)) then xmldb:remove("/db/system/config" || $path) else ()
    )
};

(: two fields of one dimension on one element cannot be told apart by the query vector :)
declare
    %test:assertError("EXXQDYFT0007")
function t:two-fields-of-one-dimension-are-ambiguous() {
    collection($t:ROOT || "-pair")//article[ft:query-vector(., $t:V4, 10)]
};

declare
    %test:assertEquals(3)
function t:the-field-function-names-the-field-of-a-pair-title() {
    count(collection($t:ROOT || "-pair")//article[ft:query-field-vector("title-embedding", $t:V4, 10)])
};

declare
    %test:assertEquals(3)
function t:the-field-function-names-the-field-of-a-pair-body() {
    count(collection($t:ROOT || "-pair")//article[ft:query-field-vector("body-embedding", $t:V4, 10)])
};

(: two fields of different dimensions: the query vector picks one, no error :)
declare
    %test:assertEquals(3, 3)
function t:the-query-vector-picks-one-of-two-fields-of-different-dimensions() {
    count(collection($t:ROOT || "-mixed")//article[ft:query-vector(., $t:V4, 10)]),
    count(collection($t:ROOT || "-mixed")//article[ft:query-vector(., $t:V3, 10)])
};
