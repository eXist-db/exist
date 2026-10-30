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
 : The parts of a query are matched in one node's value. The attribute and the text of an element are two nodes, so
 : parts found in one and the other are no match, however their offsets relate; fn:matches agrees.
 :)
module namespace poon = "http://exist-db.org/xquery/ngram/test/parts-on-one-node";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ngram = "http://exist-db.org/xquery/ngram";

declare variable $poon:NAME := "test-ngram-parts-on-one-node";
declare variable $poon:COLLECTION := "/db/" || $poon:NAME;
declare variable $poon:CONFIG := "/db/system/config/db/" || $poon:NAME;

declare variable $poon:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <ngram qname="el"/>
            <ngram qname="@att"/>
        </index>
    </collection>;

declare
    %test:setUp
function poon:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $poon:NAME),
    xmldb:store($poon:CONFIG, "collection.xconf", $poon:XCONF),
    xmldb:create-collection("/db", $poon:NAME),
    (: "t" is in the attribute, "ee" in the text: past the attribute's value, and where the distance would fit :)
    xmldb:store($poon:COLLECTION, "past-the-value.xml", <doc><el att="tx">xxxxee</el></doc>),
    xmldb:store($poon:COLLECTION, "distance-fits.xml", <doc><el att="tx">xxee</el></doc>),
    xmldb:store($poon:COLLECTION, "own-query.xml", <doc><el att="ee">xxee</el></doc>)
};

declare
    %test:tearDown
function poon:tearDown() {
    xmldb:remove($poon:COLLECTION),
    xmldb:remove($poon:CONFIG)
};

declare
    %test:assertEquals(0, 0)
function poon:wildcard-parts-on-different-nodes-variable() {
    let $els := collection($poon:COLLECTION)//el
    return (
        count($els[matches(., "t.ee")]),
        count($els[ngram:wildcard-contains(., "t.ee")])
    )
};

declare
    %test:assertEquals(0)
function poon:wildcard-parts-on-different-nodes-path() {
    count(collection($poon:COLLECTION)//el[ngram:wildcard-contains(., "t.ee")])
};

(: the query is computed for each node, and only this node's text has its own attribute's value :)
declare
    %test:assertEquals(1)
function poon:query-computed-from-the-node() {
    count(collection($poon:COLLECTION)//el[ngram:contains(., string(@att))])
};
