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
 : A "where" clause that joins variables from several "for" clauses over
 : stored nodes is evaluated once all of them are bound.
 :)
module namespace wcj="http://exist-db.org/xquery/test/where-clause-joins";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $wcj:XML := document {
    <root>
        <tree><part name="car" partid="0"><part name="engine" partid="1"/></part></tree>
        <list><part partid="0"/><part partid="1"/><part partid="5"/></list>
    </root>
};

declare
    %test:setUp
function wcj:setup() {
    xmldb:create-collection("/db", "test-where-clause-joins"),
    xmldb:store("/db/test-where-clause-joins", "test.xml", $wcj:XML),
    xmldb:store("/db/test-where-clause-joins", "delete.xml", $wcj:XML)
};

declare
    %test:tearDown
function wcj:tearDown() {
    xmldb:remove("/db/test-where-clause-joins")
};

declare %private function wcj:db($name as xs:string) as document-node() {
    doc("/db/test-where-clause-joins/" || $name)
};

declare
    %test:assertEquals("0", "1")
function wcj:join-one-for-clause-mem() {
    for $pt in $wcj:XML//tree//part, $pl in $wcj:XML//list/part
    where $pt/@partid eq $pl/@partid
    return string($pl/@partid)
};

declare
    %test:assertEquals("0", "1")
function wcj:join-one-for-clause-db() {
    let $doc := wcj:db("test.xml")
    for $pt in $doc//tree//part, $pl in $doc//list/part
    where $pt/@partid eq $pl/@partid
    return string($pl/@partid)
};

declare
    %test:assertEquals("0", "1")
function wcj:join-two-for-clauses-db() {
    let $doc := wcj:db("test.xml")
    for $pt in $doc//tree//part
    for $pl in $doc//list/part
    where $pt/@partid eq $pl/@partid
    return string($pl/@partid)
};

declare
    %test:assertEquals("1")
function wcj:join-on-a-filtered-input-db() {
    let $doc := wcj:db("test.xml")
    for $pt in $doc//tree//part[@name = "car"]//part, $pl in $doc//list/part
    where $pt/@partid eq $pl/@partid
    return string($pl/@partid)
};

declare
    %test:assertEquals("engine-0", "engine-1", "engine-5")
function wcj:where-on-the-outer-variable-db() {
    let $doc := wcj:db("test.xml")
    for $pt in $doc//tree//part, $pl in $doc//list/part
    where $pt/@name eq "engine"
    return $pt/@name || "-" || $pl/@partid
};

declare
    %test:assertEquals("engine")
function wcj:where-after-let-db() {
    for $pt in wcj:db("test.xml")//tree//part
    let $id := $pt/@partid
    where $id eq "1"
    return string($pt/@name)
};

declare
    %test:assertEquals("5")
function wcj:where-directly-after-for-db() {
    for $pl in wcj:db("test.xml")//list/part
    where $pl/@partid eq "5"
    return string($pl/@partid)
};

declare
    %test:assertEquals("0", "5")
function wcj:update-driven-by-a-join-db() {
    let $doc := wcj:db("delete.xml")
    let $_ :=
        for $pt in $doc//tree//part[@name = "car"]//part, $pl in $doc//list/part
        where $pt/@partid eq $pl/@partid
        return update delete $pl
    return $doc//list/part ! string(@partid)
};
