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
 : A vector query looks for more neighbors than it returns, and keeps the best k: the vector index's search explores
 : only as many neighbors as it is asked for, so a search for k alone misses better matches when k is small. The
 : results are compared with the exact nearest neighbors, computed here by brute force over 5,000 vectors.
 :)
module namespace qvc="http://exist-db.org/xquery/test/query-vector-candidates";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace math="http://www.w3.org/2005/xpath-functions/math";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $qvc:COLL := "/db/test-query-vector-candidates";
declare variable $qvc:N := 5000;
declare variable $qvc:DIM := 64;

(: deterministic pseudo-random vectors, which make the vector index's approximate search miss neighbors :)
declare function qvc:vector($i as xs:double) as xs:double* {
    for $j in 1 to $qvc:DIM
    let $x := math:sin($i * 12.9898 + $j * 78.233) * 43758.5453
    return $x - floor($x) - 0.5
};

declare variable $qvc:QUERY := array { qvc:vector(0.5) };

declare
    %test:setUp
function qvc:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($qvc:COLL, "/db/"))
    let $_ := xmldb:store("/db/system/config" || $qvc:COLL, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index>
                <lucene>
                    <text qname="article">
                        <vector-field name="emb" expression="embedding" dimension="64" similarity="cosine" encoding="text"/>
                    </text>
                    <!-- a second qname with the same vector field: few, so the best candidates of all are articles -->
                    <text qname="note">
                        <vector-field name="emb" expression="embedding" dimension="64" similarity="cosine" encoding="text"/>
                    </text>
                </lucene>
            </index>
        </collection>)
    let $_ := xmldb:create-collection("/db", substring-after($qvc:COLL, "/db/"))
    let $_ := xmldb:store($qvc:COLL, "articles.xml",
        <articles>{
            for $i in 1 to $qvc:N
            return <article n="{$i}"><embedding>{string-join(qvc:vector($i) ! string(.), " ")}</embedding></article>
        }</articles>)
    let $_ := xmldb:store($qvc:COLL, "notes.xml",
        <notes>{
            for $i in 1 to 20
            return <note n="{$i}"><embedding>{string-join(qvc:vector($i + 0.25) ! string(.), " ")}</embedding></note>
        }</notes>)
    (: one segment, as in a merged index: each segment is searched for its own candidates :)
    return ft:optimize()
};

declare
    %test:tearDown
function qvc:teardown() {
    xmldb:remove($qvc:COLL),
    xmldb:remove("/db/system/config" || $qvc:COLL)
};

declare function qvc:cosine($a as xs:double*, $b as xs:double*) as xs:double {
    sum(for-each-pair($a, $b, function($x, $y) { $x * $y }))
        div (math:sqrt(sum($a ! (. * .))) * math:sqrt(sum($b ! (. * .))))
};

(: the n of the exact k nearest articles :)
declare function qvc:exact($k as xs:integer) as xs:string* {
    qvc:exact($k, 1 to $qvc:N)
};

(: the n of the exact k nearest of some of the articles :)
declare function qvc:exact($k as xs:integer, $articles as xs:integer*) as xs:string* {
    let $q := $qvc:QUERY?*
    let $ranked :=
        for $i in $articles
        order by qvc:cosine(qvc:vector($i), $q) descending
        return string($i)
    return subsequence($ranked, 1, $k)
};

declare function qvc:found($hits as element(article)*) as xs:string* {
    for $hit in $hits order by ft:score($hit) descending return string($hit/@n)
};

declare
    %test:assertEquals(5)
function qvc:query-vector-finds-the-nearest() {
    let $found := qvc:found(collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5)])
    return count($found[. = qvc:exact(5)])
};

declare
    %test:assertEquals(5)
function qvc:query-field-vector-finds-the-nearest() {
    let $found := qvc:found(collection($qvc:COLL)//article/ft:query-field-vector("emb", $qvc:QUERY, 5))
    return count($found[. = qvc:exact(5)])
};

declare
    %test:assertEquals(5)
function qvc:k-is-still-the-number-of-results() {
    count(collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5)])
};

declare
    %test:assertEquals(150)
function qvc:k-above-the-default-candidates() {
    count(collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 150)])
};

(: the option is used: with as few candidates as results, the search misses the nearest; with more, it finds them :)
declare
    %test:assertEquals("true", 5)
function qvc:candidates-option() {
    let $few := qvc:found(collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5, map { "candidates": 5 })])
    let $default := qvc:found(collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5)])
    let $many := qvc:found(collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5, map { "candidates": 500 })])
    let $exact := qvc:exact(5)
    return (string(count($few[. = $exact]) lt count($default[. = $exact])), count($many[. = $exact]))
};

declare
    %test:assertError("EXXQDYFT0004")
function qvc:candidates-must-be-positive() {
    collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5, map { "candidates": 0 })]
};

declare
    %test:assertError("EXXQDYFT0004")
function qvc:candidates-has-a-maximum() {
    collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5, map { "candidates": 10001 })]
};

(: a context narrower than the documents: k hits in it, though the best of all the candidates are outside it, and
 : with enough candidates the nearest in it :)
declare
    %test:assertEquals(5, 5)
function qvc:context-narrower-than-the-documents() {
    let $in-context := (1 to $qvc:N)[. mod 7 = 0]
    let $context := collection($qvc:COLL)//article[@n mod 7 = 0]
    let $found := qvc:found($context[ft:query-vector(., $qvc:QUERY, 5)])
    let $more := qvc:found($context[ft:query-vector(., $qvc:QUERY, 5, map { "candidates": 2000 })])
    return (count($found), count($more[. = qvc:exact(5, $in-context)]))
};

(: another qname with the same vector field: its own k, though the best candidates of all are articles :)
declare
    %test:assertEquals(5)
function qvc:another-qname-with-the-same-field() {
    count(collection($qvc:COLL)//note[ft:query-vector(., $qvc:QUERY, 5)])
};

declare
    %test:assertError("EXXQDYFT0004")
function qvc:candidates-must-be-an-integer() {
    collection($qvc:COLL)//article[ft:query-vector(., $qvc:QUERY, 5, map { "candidates": "many" })]
};
