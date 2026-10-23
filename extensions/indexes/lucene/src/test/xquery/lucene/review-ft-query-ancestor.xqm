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
 : Review of PR 6914, finding 3: the ancestor change in LuceneHitCollector.processAncestorHit also reaches plain
 : ft:query when it is not pre-selected. With a single Lucene index, on p, ft:query(., ...) on a div finds the
 : div's paragraphs; these tests compare the forms that are pre-selected with those that are not, for a p that is
 : a child of the div and one that is a grandchild.
 :)
module namespace rfa = "http://exist-db.org/xquery/lucene/test/review-ft-query-ancestor";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $rfa:coll := "test-review-ft-query-ancestor";

declare
    %test:setUp
function rfa:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", $rfa:coll)
    let $col := xmldb:create-collection("/db", $rfa:coll)
    return (
        xmldb:store($conf, "collection.xconf",
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index><lucene><text qname="p"/></lucene></index>
            </collection>),
        xmldb:store($col, "doc.xml", <doc><div><section><p>alpha</p></section><p>beta</p></div></doc>)
    )
};

declare
    %test:tearDown
function rfa:tearDown() {
    xmldb:remove("/db/" || $rfa:coll),
    xmldb:remove("/db/system/config/db/" || $rfa:coll)
};

declare function rfa:divs() {
    collection("/db/" || $rfa:coll)//div
};

declare %private function rfa:filter($divs as element(div)*, $word as xs:string) as element(div)* {
    $divs[ft:query(., $word)]
};

(: the idiom: query the paragraphs :)
declare
    %test:args("alpha") %test:assertXPath("$result = 1")
    %test:args("beta") %test:assertXPath("$result = 1")
function rfa:descendant-paragraphs($word as xs:string) {
    count(collection("/db/" || $rfa:coll)//div[ft:query(.//p, $word)])
};

(: pre-selected: ft:query on the div itself :)
declare
    %test:args("alpha") %test:assertXPath("$result = 1")
    %test:args("beta") %test:assertXPath("$result = 1")
function rfa:path($word as xs:string) {
    count(collection("/db/" || $rfa:coll)//div[ft:query(., $word)])
};

(: filtering a variable :)
declare
    %test:args("alpha") %test:assertXPath("$result = 1")
    %test:args("beta") %test:assertXPath("$result = 1")
function rfa:variable($word as xs:string) {
    let $divs := rfa:divs()
    return count($divs[ft:query(., $word)])
};

(: not pre-selected: the divs passed to a function :)
declare
    %test:args("alpha") %test:assertXPath("$result = 1")
    %test:args("beta") %test:assertXPath("$result = 1")
function rfa:function-argument($word as xs:string) {
    count(rfa:filter(rfa:divs(), $word))
};

(: the result keeps its match: ft:score and util:expand of the div found through a grandchild :)
declare
    %test:args("beta") %test:assertEquals("true")
    %test:args("alpha") %test:assertEquals("true")
function rfa:function-argument-score($word as xs:string) {
    let $hit := rfa:filter(rfa:divs(), $word)
    return exists($hit) and ft:score($hit) gt 0
};
