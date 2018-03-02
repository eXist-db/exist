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
                <create match="//tei:div">
                    <field name="who" match="tei:sp/@who" type="xs:string"/>
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
            </body>
        </text>
    </TEI>;

declare variable $rtik:COLLECTION_NAME := "indexkeystest";
declare variable $rtik:COLLECTION := "/db/" || $rtik:COLLECTION_NAME;

declare
%test:setUp
function rtik:setup() {
    xmldb:create-collection("/db/system/config/db", $rtik:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $rtik:COLLECTION_NAME, "collection.xconf", $rtik:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $rtik:COLLECTION_NAME),
    xmldb:store($rtik:COLLECTION, "test.xml", $rtik:DATA),
    xmldb:store($rtik:COLLECTION, "test2.xml", $rtik:DATA)
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