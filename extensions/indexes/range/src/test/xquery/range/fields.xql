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

module namespace rt="http://exist-db.org/xquery/range/test/fields";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei="http://www.tei-c.org/ns/1.0";

declare variable $rt:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:tei="http://www.tei-c.org/ns/1.0">
            <range>
                <create match="//tei:div">
                    <field name="xml-id" match="@xml:id" type="xs:string"/>
                    <field name="line-id" match="tei:sp/tei:l/@xml:id" type="xs:string"/>
                    <field name="speaker" match="tei:sp/tei:speaker" type="xs:string"/>
                    <field name="stage" match="tei:stage" type="xs:string"/>
                    <field name="head" match="tei:head" type="xs:string"/>
                </create>
                <create match="//tei:sp">
                    <field name="sp-who" match="@who" type="xs:string"/>
                    <field name="sp-line-n" match="tei:l/@n" type="xs:int"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $rt:DATA :=
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
            </body>
        </text>
    </TEI>;
    
declare variable $rt:COLLECTION_NAME := "fieldstest";
declare variable $rt:COLLECTION := "/db/" || $rt:COLLECTION_NAME;

declare
    %test:setUp
function rt:setup() {
    xmldb:create-collection("/db/system/config/db", $rt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $rt:COLLECTION_NAME, "collection.xconf", $rt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $rt:COLLECTION_NAME),
    xmldb:store($rt:COLLECTION, "test.xml", $rt:DATA)
};

declare
    %test:tearDown
function rt:cleanup() {
    xmldb:remove($rt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $rt:COLLECTION_NAME)
};

declare
    %test:args("sha-mac101")
    %test:assertEquals(1)
    %test:args("sha-mac101005")
    %test:assertEquals(0)
function rt:field-id($id as xs:string) {
    count(collection($rt:COLLECTION)//range:field-eq("xml-id", $id))
};

declare
    %test:args("sha-mac101")
    %test:assertEquals(1)
    %test:args("sha-mac101005")
    %test:assertEquals(0)
function rt:field-div-eq($id as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[@xml:id = $id])
};

declare
    %test:stats
    %test:args("sha-mac101")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rt:field-div-eq-optimize($id as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[@xml:id = $id])
};

declare
    %test:stats
    %test:args("sha-mac101005")
    %test:assertXPath("not($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rt:field-l-eq-optimize-no($id as xs:string) {
    collection($rt:COLLECTION)//tei:sp/tei:l[@xml:id = $id]
};

declare
    %test:stats
    %test:args("sha-mac101005")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rt:field-l-eq-optimize($id as xs:string) {
    collection($rt:COLLECTION)//tei:div[tei:sp/tei:l/@xml:id = $id]
};

declare
    %test:args("sha-mac101")
    %test:assertEquals(0)
    %test:args("sha-mac101005")
    %test:assertEquals(1)
function rt:field-l-eq($id as xs:string) {
    count(collection($rt:COLLECTION)//tei:sp/tei:l[@xml:id = $id])
};

declare
    %test:args("First Witch")
    %test:assertEquals(1)
function rt:field-speaker-eq($id as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[tei:sp/tei:speaker = $id])
};

declare
    %test:stats
    %test:args("First Witch")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rt:field-speaker-eq-optimize($id as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[tei:sp/tei:speaker = $id])
};

declare
    %test:args("Enter three Witches.")
    %test:assertEquals(1)
function rt:field-stage-eq($stage as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[tei:stage = $stage])
};

declare
    %test:stats
    %test:args("Enter three Witches.")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rt:field-stage-eq-optimize($stage as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[tei:stage = $stage])
};

declare
    %test:args("Act 1, Scene 1")
    %test:assertEquals(1)
function rt:field-head-eq($head as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[tei:head = $head])
};

declare
    %test:args("Scene 1")
    %test:assertEquals(1)
function rt:field-head-ends-with($head as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[ends-with(tei:head, $head)])
};

declare
    %test:stats
    %test:args("Scene 1")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rt:field-head-ends-with-optimize($head as xs:string) {
    count(collection($rt:COLLECTION)//tei:div[ends-with(tei:head, $head)])
};

declare
    %test:assertEquals(2)
function rt:field-multi-values-lookup() {
    count(collection($rt:COLLECTION)//tei:sp[@who = ("mac-first-witch.", "mac-sec.-witch.")])
};

declare
    %test:assertEquals(2)
function rt:field-multi-values-lookup-int() {
    count(collection($rt:COLLECTION)//tei:sp[tei:l/@n = ("1", 3)])
};