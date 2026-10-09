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
 : A where clause directly following a "for" clause over persistent nodes may be
 : evaluated in a single step for all items, instead of once per item. That is only
 : correct if the where expression navigates from the "for" variable.
 :
 : @see https://github.com/eXist-db/exist/issues/2204
 :)
module namespace wss="http://exist-db.org/xquery/test/where-single-step";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $wss:COLLECTION_NAME := "where-single-step";
declare variable $wss:COLLECTION := "/db/" || $wss:COLLECTION_NAME;

(: a variable instead of collection(), so that the where expression does not depend on the context item :)
declare variable $wss:ITEMS := collection($wss:COLLECTION)/test;

declare
    %test:setUp
function wss:setup() {
    xmldb:create-collection("/db", $wss:COLLECTION_NAME),
    xmldb:store($wss:COLLECTION, "test.xml",
        <test>
            <item id="1"><name>Chair</name></item>
            <item id="2"><name>Table</name></item>
            <item id="3" label="Cabinet"><name>Cabinet</name></item>
        </test>),
    xmldb:store($wss:COLLECTION, "queries.xml",
        <queries>
            <q>Chair</q>
            <q>Table</q>
            <q>NoMatch</q>
        </queries>)
};

declare
    %test:tearDown
function wss:cleanup() {
    xmldb:remove($wss:COLLECTION)
};

declare
    %test:assertEquals("1")
function wss:navigating-comparison() {
    for $d in collection($wss:COLLECTION)//item
    where $d/name = "Chair"
    return string($d/@id)
};

declare
    %test:assertEquals("Chair", "Table")
function wss:variable-as-comparison-key() {
    for $q in doc($wss:COLLECTION || "/queries.xml")//q
    where $wss:ITEMS//item[name = $q]
    return string($q)
};

declare
    %test:assertEquals("3")
function wss:variable-on-both-sides() {
    for $d in collection($wss:COLLECTION)//item
    where $d/name = $d/@label
    return string($d/@id)
};

declare
    %test:assertEquals("1")
function wss:variable-filtered() {
    for $d in collection($wss:COLLECTION)//item
    where $d[name = "Chair"]
    return string($d/@id)
};

declare
    %test:assertEquals("1")
function wss:variable-as-function-argument() {
    for $d in collection($wss:COLLECTION)//item
    where matches($d/name, "^Ch")
    return string($d/@id)
};

declare
    %test:assertEquals("2")
function wss:let-variable() {
    for $d in collection($wss:COLLECTION)//item
    let $name := $d/name
    where $name = "Table"
    return string($d/@id)
};

declare
    %test:assertEquals("2")
function wss:inner-for-variable() {
    for $d in collection($wss:COLLECTION)//item
    for $name in $d/name
    where $name = "Table"
    return string($d/@id)
};
