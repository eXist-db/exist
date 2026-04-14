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

module namespace flwor="http://exist-db.org/xquery/test/flwor";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $flwor:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <range>
                <create qname="place">
                    <field name="place-id" type="xs:string" match="@xml:id"/>
                    <field name="place-name" type="xs:string" match="placeName"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $flwor:DATA :=
    <listPlace>
         <place xml:id="warsaw">
             <placeName>Warsaw</placeName>
         </place>
         <place xml:id="berlin">
             <placeName>Berlin</placeName>
         </place>
    </listPlace>;

declare variable $flwor:COLLECTION_NAME := "flwortest";
declare variable $flwor:COLLECTION := "/db/" || $flwor:COLLECTION_NAME;

declare
    %test:setUp
function flwor:setup() {
    xmldb:create-collection("/db/system/config/db", $flwor:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $flwor:COLLECTION_NAME, "collection.xconf", $flwor:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $flwor:COLLECTION_NAME),
    xmldb:store($flwor:COLLECTION, "test.xml", $flwor:DATA)
};

declare
    %test:tearDown
function flwor:cleanup() {
    xmldb:remove($flwor:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $flwor:COLLECTION_NAME)
};

declare function flwor:test($name) {
    collection($flwor:COLLECTION)//place[placeName = $name]/string()
};


declare
    %test:assertEquals("Berlin", "Berlin")
function flwor:order-by-with-range() {
    for $i in 1 to 2
    order by $i
    return
        flwor:test("Berlin")
};

(: https://github.com/eXist-db/exist/issues/739#issuecomment-130997865 :)
declare
    %test:assertEquals(1, 2, 1, 2, 3, 1, 2, 3, 4)
function flwor:orderby-for-multi() {
    for $x in (3, 2, 4)
    order by $x
    for $y in (1 to $x)
    return $y
};

declare
    %test:assertEquals("a1", "a2", "b1", "b2")
function flwor:orderby-multi() {
  for $a in ("b", "a")
  order by $a
  for $b in (2, 1)
  order by $b
  return
    $a || $b
};

declare
    %test:assertEquals("a1", "a2", "b1", "b2")
function flwor:orderby-where-multi() {
    for $a in ("b", "a", "c")
    order by $a
    where $a = ("a", "b")
    for $b in (2, 1, 5, 6, 9)
    where $b < 4
    order by $b
    return
        $a || $b
};

declare
    %test:assertEquals(2, 4)
function flwor:where-multi() {
    for $a in 1 to 10
    where $a mod 2 = 0
    where $a < 5
    return
        $a
};

declare
    %test:assertEquals(1, 1)
function flwor:where-multi-groupby() {
    let $xml :=
        <t>
            <n class="a">1</n>
            <n class="b">2</n>
            <n class="a">1</n>
            <n class="a">3</n>
        </t>
    for $a in $xml/n
    where $a != 3
    group by $c := $a/@class
    where count($a) > 1
    return
        $a/string()
};

declare
    %test:args(0)
    %test:assertEquals("[]")
    %test:args(1)
    %test:assertEquals("[1]")
    %test:args(6)
    %test:assertEquals("[1]", "[2]", "[3]", "[4]", "[6]")
function flwor:allowing-empty($n as xs:integer) {
    for $x allowing empty in 1 to $n
    where not($x = 5)
    return concat("[", $x, "]")
};

declare
    %test:args(4)
    %test:assertEquals(":0")
    %test:args(2)
    %test:assertEquals("b:1")
    %test:args(1)
    %test:assertEquals("a:1")
    %test:args(5)
    %test:assertEquals(":0")
function flwor:allowing-empty-fix($n as xs:integer) {
    let $sequence := ("a", "b", "c")[$n]
    for $x allowing empty at $y in $sequence
    return $x || ":" || $y
};

declare
    %test:args(4)
    %test:assertEquals("")
    %test:args(2)
    %test:assertEquals("b:1")
function flwor:no-allow-empty($n as xs:integer) {
    let $sequence := ("a", "b", "c")[$n]
    return
        if (empty($sequence)) then
            ""
        else
            for $x at $y in $sequence
            return $x || ":" || $y
};

(: https://github.com/eXist-db/exist/issues/4252 :)
(: When a leading order-by key is the empty sequence, subsequent keys must still be applied. :)
declare
    %test:assertEquals("a3", "a4", "b1", "c2")
function flwor:orderby-empty-ordering-spec-1st() {
    let $xml := document { <root><a n="4"/><b n="1"/><c n="2"/><a n="3"/></root> }
    for $elem in $xml/root/*
    order by
        (),
        $elem/name(),
        $elem/@n
    return
        $elem/name() || $elem/@n
};

(: When a middle order-by key is the empty sequence, subsequent keys must still be applied. :)
declare
    %test:assertEquals("a3", "a4", "b1", "c2")
function flwor:orderby-empty-ordering-spec-2nd() {
    let $xml := document { <root><a n="4"/><b n="1"/><c n="2"/><a n="3"/></root> }
    for $elem in $xml/root/*
    order by
        $elem/name(),
        (),
        $elem/@n
    return
        $elem/name() || $elem/@n
};

(: When the trailing order-by key is the empty sequence, earlier keys must still be applied. :)
declare
    %test:assertEquals("a3", "a4", "b1", "c2")
function flwor:orderby-empty-ordering-spec-last() {
    let $xml := document { <root><a n="4"/><b n="1"/><c n="2"/><a n="3"/></root> }
    for $elem in $xml/root/*
    order by
        $elem/name(),
        $elem/@n,
        ()
    return
        $elem/name() || $elem/@n
};


(:~
 : Type declaration in for-binding should constrain the iteration variable,
 : not the return type. See https://github.com/eXist-db/exist/issues/3553
 :)
declare
    %test:assertEquals("<p>a</p>", "<p>b</p>", "<p>c</p>")
function flwor:for-as-string-return-element() {
    for $i as xs:string in ("a", "b", "c")
    return <p>{$i}</p>
};

(:~
 : Type declaration with function type in for-binding.
 : Adapted from XQTS hof-046.
 :)
declare
    %test:assertTrue
function flwor:for-as-function() {
    for $f as function(*) in (string-join#1)
    return true()
};

(:~
 : Type declaration should still raise XPTY0004 when the binding
 : sequence items do not match the declared type.
 :)
declare
    %test:assertError
function flwor:for-as-type-mismatch() {
    for $i as xs:integer in ("a", "b", "c")
    return $i
};

(:~
 : Subtype promotion: xs:integer items should match xs:decimal declaration.
 :)
declare
    %test:assertEquals(1, 2, 3)
function flwor:for-as-decimal-with-integers() {
    for $n as xs:decimal in (xs:integer(1), xs:integer(2), xs:integer(3))
    return $n
};

(:~
 : Type declaration with xs:string should pass when binding string values.
 :)
declare
    %test:assertTrue
function flwor:for-as-string-binding() {
    for $x as xs:string in "foo"
    return true()
};
