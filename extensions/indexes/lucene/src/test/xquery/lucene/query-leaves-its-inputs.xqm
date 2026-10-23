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
 : A query's matches are carried by the nodes of its result, never by the nodes it was given: after
 : $c[ft:query(., ...)], the nodes of $c carry no matches of that query, a second query over $c is not mixed with
 : the first, and a result taken earlier does not change when a later query runs over the same nodes.
 :)
module namespace qli = "http://exist-db.org/xquery/lucene/test/query-leaves-its-inputs";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $qli:COLLECTION := "/db/test-query-leaves-its-inputs";

declare
    %test:setUp
function qli:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $config := xmldb:create-collection("/db/system/config/db", substring-after($qli:COLLECTION, "/db/"))
    let $_ := xmldb:store($config, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene>
                <text qname="p"/>
                <text qname="item"><facet dimension="cat" expression="string(@cat)"/></text>
            </lucene></index>
        </collection>)
    let $_ := xmldb:create-collection("/db", substring-after($qli:COLLECTION, "/db/"))
    return xmldb:store($qli:COLLECTION, "doc.xml",
        <body><div><p>alpha beta</p><p>gamma</p></div><div><p>delta</p></div>
            <items><item cat="x">alpha beta</item><item cat="y">alpha</item></items></body>)
};

declare
    %test:tearDown
function qli:tearDown() {
    xmldb:remove($qli:COLLECTION),
    xmldb:remove("/db/system/config" || $qli:COLLECTION)
};

declare %private function qli:matches($nodes as node()*) as xs:string {
    string-join(util:expand($nodes)//exist:match ! string(.), "|")
};

(: a predicate evaluated per item ($c[ft:query(., …)]): the hits are highlighted, the context they came from is not :)
declare
    %test:assertEquals("alpha", "")
function qli:per-item-predicate-leaves-the-context() {
    let $c := collection($qli:COLLECTION)//p
    let $hits := $c[ft:query(., "alpha")]
    return (qli:matches($hits), qli:matches($c))
};

(: a second query over the same context highlights its own term only :)
declare
    %test:assertEquals("beta")
function qli:a-second-query-is-not-mixed-with-the-first() {
    let $c := collection($qli:COLLECTION)//p
    let $first := $c[ft:query(., "alpha")]
    let $second := $c[ft:query(., "beta")]
    return qli:matches($second)
};

(: a result taken earlier keeps its matches when a later query runs over the same nodes :)
declare
    %test:assertEquals("alpha")
function qli:an-earlier-result-does-not-change() {
    let $c := collection($qli:COLLECTION)//p
    let $one := $c[ft:query(., "alpha")][1]
    let $later := $c[ft:query(., "beta")]
    return qli:matches($one)
};

(: predicates in a row: the result carries the matches of both; the first result keeps only its own :)
declare
    %test:assertEquals("alpha|beta", "alpha")
function qli:chained-queries-accumulate-on-the-result-only() {
    let $c := collection($qli:COLLECTION)//p
    let $first := $c[ft:query(., "alpha")]
    let $both := $first[ft:query(., "beta")]
    return (qli:matches($both), qli:matches($first))
};

(: a predicate selecting a node set (node-set mode): the result carries the matches found in it, the context does not :)
declare
    %test:assertEquals(1, "alpha", "")
function qli:node-set-predicate-leaves-the-context() {
    let $d := collection($qli:COLLECTION)//div
    let $r := $d[p[ft:query(., "alpha")]]
    return (count($r), qli:matches($r), qli:matches($d))
};

(: a where clause: the returned nodes carry the matches, the sequence iterated over does not :)
declare
    %test:assertEquals("alpha", "")
function qli:where-clause-leaves-the-sequence() {
    let $c := collection($qli:COLLECTION)//p
    let $r := for $n in $c where ft:query($n, "alpha") return $n
    return (qli:matches($r), qli:matches($c))
};

(: a path evaluated earlier is not changed by a query over the same path :)
declare
    %test:assertEquals("alpha", "")
function qli:a-path-query-leaves-an-earlier-path() {
    let $all := collection($qli:COLLECTION)//p
    let $hits := collection($qli:COLLECTION)//p[ft:query(., "alpha")]
    return (qli:matches($hits), qli:matches($all))
};

(: ft:score of a node that was only the context of a query is 0 :)
declare
    %test:assertTrue
function qli:the-context-has-no-score() {
    let $c := collection($qli:COLLECTION)//p
    let $hits := $c[ft:query(., "alpha")]
    return ft:score($hits[1]) gt 0 and ft:score($c[1]) eq 0
};

(: facets of a query over $c count that query's hits only, not those of an earlier query over $c :)
declare
    %test:assertEquals("x=1")
function qli:facets-of-a-second-query-are-its-own() {
    let $c := collection($qli:COLLECTION)//item
    let $first := $c[ft:query(., "alpha")]
    let $facets := ft:facets($c[ft:query(., "beta")], "cat")
    return string-join(for $k in sort(map:keys($facets)) return $k || "=" || $facets($k), ",")
};
