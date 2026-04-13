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
xquery version "3.0";

module namespace rtik="http://exist-db.org/xquery/range/test/index-keys";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei="http://www.tei-c.org/ns/1.0";

declare variable $rtik:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:tei="http://www.tei-c.org/ns/1.0">
            <range>
                <create qname="tei:speaker" type="xs:string"/>
                <!-- GitHub #4805: util:index-keys#5 uses range index on @s -->
                <create qname="@s" type="xs:string"/>
                <create match="//tei:div">
                    <field name="who" match="tei:sp/@who" type="xs:string"/>
                </create>
                <create qname="tei:test">
                    <field name="elem-field" match="tei:elem" type="xs:string" case="no"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $rtik:DATA :=
    <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="sha-mac">
        <text>
            <body>
                <div xml:id="sha-mac1">
                    <head>Act 1</head>
                    <div xml:id="sha-mac101">
                        <head>Act 1, Scene 1</head>
                        <stage>A desert place. Thunder and lightning.</stage>
                        <stage>Enter three Witches.</stage>
                        <sp who="mac-first-witch.">
                            <speaker>First Witch</speaker>
                            <l xml:id="sha-mac101001" n="1">When shall we three meet again</l>
                            <l xml:id="sha-mac101002" n="2">In thunder, lightning, or in rain?</l>
                        </sp>
                        <sp who="mac-sec.-witch.">
                            <speaker>Second Witch</speaker>
                            <l xml:id="sha-mac101003" n="3">When the hurlyburly's done,</l>
                            <l xml:id="sha-mac101004" n="4">When the battle's lost and won.</l>
                        </sp>
                        <sp who="mac-third-witch.">
                            <speaker>Third Witch</speaker>
                            <l xml:id="sha-mac101005" n="5">That will be ere the set of sun.</l>
                        </sp>
                    </div>
                </div>
                <test>
                    <elem>a</elem>
                    <elem>b</elem>
                    <elem>c</elem>
                </test>
                <test>
                    <elem>a</elem>
                    <elem>b</elem>
                    <elem>c</elem>
                    <elem>b</elem>
                    <elem>y</elem>
                </test>
            </body>
        </text>
    </TEI>;

(: GitHub #4805 fixture: only test.xml gets the @s attribute :)
declare variable $rtik:COLLECTION_NAME := "indexkeystest";
declare variable $rtik:COLLECTION := "/db/" || $rtik:COLLECTION_NAME;
declare variable $rtik:EXPECTED_ELEM_KEYS := ("a", "b", "c", "y");

declare
%test:setUp
function rtik:setup() {
    (xmldb:create-collection("/db/system", "config"),
     xmldb:create-collection("/db/system/config", "db"),
     xmldb:create-collection("/db/system/config/db", $rtik:COLLECTION_NAME),
     xmldb:create-collection("/db", $rtik:COLLECTION_NAME),
     xmldb:store("/db/system/config/db/" || $rtik:COLLECTION_NAME, "collection.xconf", $rtik:COLLECTION_CONFIG),
     xmldb:store($rtik:COLLECTION, "test.xml", $rtik:DATA),
     (: Add the minimal #4805 fixture into test.xml before reindexing :)
     update insert <root><child s="1"/></root> into doc($rtik:COLLECTION || "/test.xml")/tei:TEI,
     xmldb:store($rtik:COLLECTION, "test2.xml", $rtik:DATA),
     xmldb:reindex($rtik:COLLECTION))
};

declare
%test:tearDown
function rtik:cleanup() {
    xmldb:remove($rtik:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $rtik:COLLECTION_NAME)
};


declare
%test:assertEquals(3)
function rtik:index-keys() {
 let $keys := util:index-keys(collection($rtik:COLLECTION)//tei:speaker, (), function($key, $nums) { $key }, (), "range-index")
 return count($keys)
};

declare
%test:assertEquals(3)
function rtik:index-keys-for-field() {
  let $keys := range:index-keys-for-field("who", function($key, $nums) { $key }, 100)
  return count($keys)
};

declare
%test:assertEquals(3)
%test:pending
function rtik:index-keys-for-field-with-context() {
  let $keys := collection($rtik:COLLECTION)/range:index-keys-for-field("who", function($key, $nums) { $key }, 100)
  return count($keys)
};

declare
%test:assertEquals(3)
function rtik:index-keys-for-field-with-context-in-dynamic-function() {
  let $func := function-lookup(xs:QName("range:index-keys-for-field"), 3)
  let $keys := collection($rtik:COLLECTION)/$func("who", function($key, $nums) { $key }, 100)
  return count($keys)
};

(: --- GitHub #4074: range:index-keys-for-field called from servlet context --- :)

declare
    %test:assertTrue
function rtik:issue4074-no-context-returns-keys() {
    let $result := range:index-keys-for-field("elem-field", function($key, $nums) { $key }, 100)
    return deep-equal(for $x in $result order by $x return $x, $rtik:EXPECTED_ELEM_KEYS)
};

declare
    %test:assertTrue
function rtik:issue4074-with-explicit-context() {
    let $result := distinct-values(
        collection($rtik:COLLECTION)/range:index-keys-for-field("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(for $x in $result order by $x return $x, $rtik:EXPECTED_ELEM_KEYS)
};

declare function rtik:issue4074-wrap($a as xs:string, $b as function(*), $c as xs:integer) {
    range:index-keys-for-field($a, $b, $c)
};

declare
    %test:assertTrue
function rtik:issue4074-with-named-function-context() {
    let $result := distinct-values(
        collection($rtik:COLLECTION)/rtik:issue4074-wrap("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(for $x in $result order by $x return $x, $rtik:EXPECTED_ELEM_KEYS)
};

declare
    %test:assertTrue
function rtik:issue4074-with-inline-function-context() {
    let $inlineFunc := function($a as xs:string, $b as function(*), $c as xs:integer) {
        range:index-keys-for-field($a, $b, $c)
    }
    let $result := distinct-values(
        collection($rtik:COLLECTION)/$inlineFunc("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(for $x in $result order by $x return $x, $rtik:EXPECTED_ELEM_KEYS)
};

declare
    %test:assertTrue
function rtik:issue4074-with-dynamic-function-context() {
    let $func := function-lookup(xs:QName("range:index-keys-for-field"), 3)
    let $result := distinct-values(
        collection($rtik:COLLECTION)/$func("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(for $x in $result order by $x return $x, $rtik:EXPECTED_ELEM_KEYS)
};

(: GitHub #4805: util:index-keys#5 returns stale data after update insert :)

declare function rtik:issue4805-term($term as xs:string, $data as xs:int+) as item()+ {
    $term, $data
};

declare function rtik:issue4805-get-nodeset() {
    collection($rtik:COLLECTION)//@s
};

declare function rtik:issue4805-list-terms($nodes as node()*, $number-of-results as xs:integer) as item()* {
    util:index-keys($nodes, (), rtik:issue4805-term#2, $number-of-results, "range-index")
};

declare
    %test:assertEquals('1', 1, 1, 1)
function rtik:issue4805-test-initial() {
    (: Stateful sequence: this establishes baseline frequency before the update test below. :)
    rtik:issue4805-list-terms(rtik:issue4805-get-nodeset(), 1)
};

declare
    %test:assertEquals('1', 2, 1, 1)
function rtik:issue4805-test-list-after-update() {
    update insert <child s="1"/> into doc($rtik:COLLECTION || "/test.xml")//root,
    rtik:issue4805-list-terms(rtik:issue4805-get-nodeset(), 1)
};

declare
    %test:assertEquals('1', 2, 1, 1)
function rtik:issue4805-test-list-with-different-page-size() {
    rtik:issue4805-list-terms(rtik:issue4805-get-nodeset(), 2)
};

declare
    %test:assertEquals('1', 2, 1, 1)
function rtik:issue4805-test-updated-after-reindex() {
    let $_ := xmldb:reindex($rtik:COLLECTION)
    return rtik:issue4805-list-terms(rtik:issue4805-get-nodeset(), 1)
};