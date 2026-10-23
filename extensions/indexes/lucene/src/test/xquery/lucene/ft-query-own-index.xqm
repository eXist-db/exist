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
 : ft:query($nodes, $q) is a filter: the nodes of $nodes whose own index entry matches $q. A node without an index of
 : its own is not found through a match below it, in any form of the call, and the answer does not depend on what
 : else the database has indexed. Descendants are queried explicitly.
 :)
module namespace oi = "http://exist-db.org/xquery/lucene/test/ft-query-own-index";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $oi:COLLECTION := "/db/test-ft-query-own-index";
declare variable $oi:UNRELATED := "/db/test-ft-query-own-index-unrelated";

declare %private function oi:store($coll as xs:string, $qname as xs:string, $doc as element()) {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $config := xmldb:create-collection("/db/system/config/db", substring-after($coll, "/db/"))
    let $_ := xmldb:store($config, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="{$qname}"/></lucene></index>
        </collection>)
    let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
    return xmldb:store($coll, "doc.xml", $doc)
};

declare
    %test:setUp
function oi:setup() {
    oi:store($oi:COLLECTION, "p", <body><div><section><p>alpha</p></section><p>beta</p></div></body>)
};

declare
    %test:tearDown
function oi:tearDown() {
    for $coll in ($oi:COLLECTION, $oi:UNRELATED)
    return (
        if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (),
        if (xmldb:collection-available("/db/system/config" || $coll)) then xmldb:remove("/db/system/config" || $coll)
        else ()
    )
};

declare %private function oi:divs() as element(div)* {
    collection($oi:COLLECTION)//div
};

declare %private function oi:query($nodes as element()*, $q as xs:string) as element()* {
    $nodes[ft:query(., $q)]
};

declare
    %test:assertEquals(0, 0)
function oi:variable() {
    let $d := oi:divs()
    return (count($d[ft:query(., "beta")]), count($d[ft:query(., "alpha")]))
};

declare
    %test:assertEquals(0, 0)
function oi:function-argument() {
    (count(oi:query(oi:divs(), "beta")), count(oi:query(oi:divs(), "alpha")))
};

declare
    %test:assertEquals(0, 0)
function oi:path() {
    (count(collection($oi:COLLECTION)//div[ft:query(., "beta")]),
     count(collection($oi:COLLECTION)//div[ft:query(., "alpha")]))
};

declare
    %test:assertEquals(1, 1, 1)
function oi:descendants-explicitly() {
    let $d := oi:divs()
    return (count($d[ft:query(.//p, "alpha")]), count($d[.//p[ft:query(., "alpha")]]),
        count(ft:query($d//p, "beta")))
};

declare
    %test:assertEquals(1, 1)
function oi:the-indexed-node-itself() {
    let $p := collection($oi:COLLECTION)//p
    return (count($p[ft:query(., "alpha")]), count(ft:query($p, "beta")))
};

(: a result found through a function argument keeps its match :)
declare
    %test:assertEquals("true", "alpha")
function oi:function-argument-keeps-the-match() {
    let $hits := oi:query(collection($oi:COLLECTION)//p, "alpha")
    return (ft:score($hits) gt 0, string-join(util:expand($hits)//exist:match ! string(.), "|"))
};

(: the same calls give the same answers after a collection with an index on another name is stored :)
declare
    %test:assertEquals("0 0 1", "0 0 1")
function oi:another-index-in-the-database-does-not-change-the-answer() {
    let $answers := function() {
        let $d := oi:divs()
        return string-join((count($d[ft:query(., "beta")]), count(oi:query($d, "beta")),
            count(collection($oi:COLLECTION)//p[ft:query(., "beta")])), " ")
    }
    let $before := $answers()
    let $_ := oi:store($oi:UNRELATED, "title", <doc><title>beta words</title></doc>)
    return ($before, $answers())
};
