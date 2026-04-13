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

module namespace rt="http://exist-db.org/xquery/range/test/range";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace ft="http://exist-db.org/xquery/lucene";

(: IDE linter: ensure common prefixes are resolvable statically :)
declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace range="http://exist-db.org/xquery/range";

declare variable $rt:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:tei="http://www.tei-c.org/ns/1.0">
            <lucene>
                <text field="lucene-name" qname="name"/>
            </lucene>
            <range>
                <create match="//address">
                    <field name="address-name" match="name" type="xs:string" whitespace="normalize"/>
                    <field name="address-city" match="city" type="xs:string"/>
                    <field name="address-code" match="city/@code" type="xs:integer"/>
                </create>
                <create match="//tei:placeName">
                    <field name="name" type="xs:string" nested="no"/>
                    <field name="type" match="@type" type="xs:string"/>
                    <field name="subtype" match="@subtype" type="xs:string"/>
                </create>
                <create match="/test/address/name" whitespace="normalize"/>
                <create match="/test/address/city" type="xs:string" collation="?lang=de-DE&amp;strength=primary"/>
                <create match="/test/address/city/@code" type="xs:integer"/>
                <create qname="@id" type="xs:string"/>
                <!-- GitHub #4110: case-insensitive range index on @lemma -->
                <create qname="@lemma" type="xs:string" case="no"/>
                <create qname="@lemma-strict" type="xs:string" case="yes"/>
                <!-- GitHub #4016: range:field-eq with empty key arguments -->
                <create qname="@effectiveDate" type="xs:string"/>
                <create qname="dataset">
                    <field name="dataset-id" match="@id" type="xs:string"/>
                    <field name="dataset-effectiveDate" match="@effectiveDate" type="xs:string"/>
                </create>
                <!-- GitHub #1353: nested mixed content indexing on qname(def) -->
                <create qname="def">
                    <field name="textcontent" type="xs:string" case="no" nested="yes"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $rt:DATA_NESTED := 
    <place xmlns="http://www.tei-c.org/ns/1.0">
        <placeName xml:id="ODB_S00004004_NAM001" xml:lang="de-DE" type="ref" subtype="inofficial">Hofthiergarten<note type="source">
                <date ana="#notBefore">1750</date>
                <date ana="#notAfter">1820</date>
            </note>
        </placeName>
        <placeName xml:id="ODB_S00004004_NAM002" xml:lang="de-DE" type="main" subtype="official">Hofthiergarten<note type="source">
                <date ana="#when">2011-08-24</date>
            </note>
        </placeName>
        <placeName xml:id="ODB_A00000393_NAM001" xml:lang="de-DE" type="main" subtype="official">Dorfprozelten<note type="source">
                <date ana="#when">2001-04-07</date>
                <bibl>
                    <ptr target="#HAB/Laube"/>
                </bibl>
            </note>
        </placeName>
    </place>;

declare variable $rt:DATA :=
    <test>
        <address id="muh">
            <name>Berta  Muh
            </name>
            <street>Wiesenweg 14</street>
            <city code="65463">Almweide</city>
        </address>
        <address id="rüssel">
            <name>Rudi Rüssel</name>
            <street>Elefantenweg 67</street>
            <city code="65428">Rüsselsheim</city>
        </address>
        <address id="amsel">
            <name>Albert Amsel</name>
            <street>Birkenstraße 77</street>
            <city code="76878">Waldstadt</city>
        </address>
        <address id="reh">
            <name>Pü Reh</name>
            <street>Am Waldrand 4</street>
            <city code="89283">Wiesental</city>
        </address>
        <debug>
            <a lemma="Aaa" lemma-strict="OnlyThis"/>
            <a lemma="AAA" lemma-strict="OnlyThis"/>
            <a lemma="aaa" lemma-strict="OnlyThis"/>
        </debug>
        <decor>
            <datasets>
                <dataset id="2.16.840.1.113883.3.1937.99.62.3.1.1" effectiveDate="2012-05-30T11:32:36">
                    <name language="en-US">Demo dataset</name>
                </dataset>
            </datasets>
        </decor>
        <!-- GitHub #1353 fixture -->
        <root>
            <def> abc <x>def</x> ghi </def>
            <def> jklmnopqr </def>
        </root>
    </test>;

declare variable $rt:DATA2 :=
    <object>
        <parameter>
            <name>key1</name>
            <value>value1</value>
        </parameter>
    </object>;

(: --- GitHub #3114: Lucene+range interaction with ft:query predicate --- :)
declare variable $rt:I3114_DATA as document-node() := document {
    <root>
        <record id="1">
            <field name="name">Named Artist</field>
            <field name="desc">Artist #1</field>
        </record>
        <record id="2">
            <field name="name">Someone else</field>
            <field name="desc">Artist #2</field>
        </record>
    </root>
};

declare variable $rt:I3114_FTQUERY as element(phrase) := <phrase>Artist</phrase>;

declare variable $rt:I3114_XCONF_NO_RANGE as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="field"/>
            </lucene>
        </index>
    </collection>;

declare variable $rt:I3114_XCONF_WITH_RANGE as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="field"/>
            </lucene>
            <create qname="@name" type="xs:string"/>
        </index>
    </collection>;

declare variable $rt:I3114_COLL_NO_RANGE := "i3114-no-range";
declare variable $rt:I3114_COLL_WITH_RANGE := "i3114-with-range";

(:~ Test collection name (no path). :)
declare variable $rt:COLLECTION_NAME := "range-test-range";

(:~ Full path of the test collection. :)
declare variable $rt:COLLECTION := "/db/" || $rt:COLLECTION_NAME;

(:~
 : XQSuite setUp: create config parent chain, test collection, config subcollection,
 : store xconf and documents, reindex.
 :)
declare
    %test:setUp
function rt:setup() {
    (xmldb:create-collection("/db/system", "config"),
     xmldb:create-collection("/db/system/config", "db"),
     xmldb:create-collection("/db/system/config/db", $rt:COLLECTION_NAME),
     xmldb:create-collection("/db", $rt:COLLECTION_NAME),
     xmldb:store("/db/system/config/db/" || $rt:COLLECTION_NAME, "collection.xconf", $rt:COLLECTION_CONFIG),
     xmldb:store($rt:COLLECTION, "test.xml", $rt:DATA),
     xmldb:store($rt:COLLECTION, "nested.xml", $rt:DATA_NESTED),
     xmldb:reindex($rt:COLLECTION),

     (: GitHub #3114: dual collections (Lucene-only vs Lucene+range) :)
     xmldb:create-collection("/db", $rt:I3114_COLL_NO_RANGE),
     xmldb:create-collection("/db/system/config/db", $rt:I3114_COLL_NO_RANGE),
     xmldb:store("/db/system/config/db/" || $rt:I3114_COLL_NO_RANGE, "collection.xconf", $rt:I3114_XCONF_NO_RANGE),
     xmldb:store("/db/" || $rt:I3114_COLL_NO_RANGE, "data.xml", $rt:I3114_DATA),
     xmldb:reindex("/db/" || $rt:I3114_COLL_NO_RANGE),

     xmldb:create-collection("/db", $rt:I3114_COLL_WITH_RANGE),
     xmldb:create-collection("/db/system/config/db", $rt:I3114_COLL_WITH_RANGE),
     xmldb:store("/db/system/config/db/" || $rt:I3114_COLL_WITH_RANGE, "collection.xconf", $rt:I3114_XCONF_WITH_RANGE),
     xmldb:store("/db/" || $rt:I3114_COLL_WITH_RANGE, "data.xml", $rt:I3114_DATA),
     xmldb:reindex("/db/" || $rt:I3114_COLL_WITH_RANGE))
};

(:~
 : XQSuite tearDown: remove test collection and its config.
 :)
declare
    %test:tearDown
function rt:cleanup() {
    xmldb:remove($rt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $rt:COLLECTION_NAME),

    xmldb:remove("/db/" || $rt:I3114_COLL_NO_RANGE),
    xmldb:remove("/db/system/config/db/" || $rt:I3114_COLL_NO_RANGE),

    xmldb:remove("/db/" || $rt:I3114_COLL_WITH_RANGE),
    xmldb:remove("/db/system/config/db/" || $rt:I3114_COLL_WITH_RANGE)
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals("Rüsselsheim")
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
function rt:equality-string($name as xs:string) {
    collection($rt:COLLECTION)//address[range:eq(name, $name)]/city/text()
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals("Rudi Rüssel")
    %test:args("Berta Muh")
    %test:assertEquals("Berta Muh")
function rt:equality-string-self($name as xs:string) {
    normalize-space(collection($rt:COLLECTION)//address/name[range:eq(., $name)]/text())
};

declare
    %test:args(65428)
    %test:assertEquals("Rüsselsheim", "Rüsselsheim")
function rt:equality-int-attribute($code as xs:integer) {
    collection($rt:COLLECTION)//address/city[range:eq(@code, $code)]/text(),
    collection($rt:COLLECTION)//address[range:eq(city/@code, $code)]/city/text()
};

declare
    %test:args("muh")
    %test:assertEquals("Berta Muh")
function rt:equality-qname-string-attribute($id as xs:string) {
    normalize-space(collection($rt:COLLECTION)//address[range:eq(@id, $id)]/name/text())
};

declare
    %test:args("russelsheim")
    %test:assertEquals("Rüsselsheim")
    %test:args("almweide")
    %test:assertEquals("Almweide")
function rt:equality-string-collation($name as xs:string) {
    collection($rt:COLLECTION)//address[range:eq(city, $name)]/city/text()
};

declare 
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh")
    %test:assertEquals("Wiesental")
function rt:equality-fields($name as xs:string) {
    collection($rt:COLLECTION)//range:field-eq("address-name", $name)/city/text()
};

declare 
    %test:args(65428)
    %test:assertEquals("Rudi Rüssel")
function rt:equality-field-integer($code as xs:integer) {
    collection($rt:COLLECTION)//range:field-eq("address-code", $code)/name/text()
};

declare 
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh")
    %test:assertEquals("Wiesental")
function rt:equality-fields-with-context($name as xs:string) {
    doc($rt:COLLECTION || "/test.xml")/range:field-eq("address-name", $name)/city/text()
};

declare 
    %test:args("Berta Muh", "Almweide")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh", "Wiesental")
    %test:assertEquals("Wiesental")
function rt:equality-fields-multi($name as xs:string, $city as xs:string) {
    collection($rt:COLLECTION)//range:field-eq(("address-name", "address-city"), $name, $city)/city/text()
};

(:declare :)
(:    %test:args("Berta Muh", "Almweide"):)
(:    %test:assertEquals(1):)
(:function rt:equality-fields-multi($name as xs:string, $city as xs:string) {:)
(:    count(/test[range:field-eq(("address-name", "address-city"), $name, $city)]):)
(:};:)

declare
    %test:args("Berta Muh")
    %test:assertEquals(2)
    %test:args("Albert Amsel")
    %test:assertEquals(3)
    %test:args("Pü Reh")
    %test:assertEquals(1)
function rt:gt-string($name as xs:string) {
    count(collection($rt:COLLECTION)//address[range:gt(name, $name)])
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(3)
    %test:args("Albert Amsel")
    %test:assertEquals(4)
    %test:args("Pü Reh")
    %test:assertEquals(2)
function rt:ge-string($name as xs:string) {
    count(collection($rt:COLLECTION)//address[range:ge(name, $name)])
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(1)
    %test:args("Albert Amsel")
    %test:assertEquals(0)
    %test:args("Pü Reh")
    %test:assertEquals(2)
function rt:lt-string($name as xs:string) {
    count(collection($rt:COLLECTION)//address[range:lt(name, $name)])
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(2)
    %test:args("Albert Amsel")
    %test:assertEquals(1)
    %test:args("Pü Reh")
    %test:assertEquals(3)
function rt:le-string($name as xs:string) {
    count(collection($rt:COLLECTION)//address[range:le(name, $name)])
};

declare
    %test:args(76878)
    %test:assertEquals(1)
    %test:args(89283)
    %test:assertEquals(0)
    %test:args(65463)
    %test:assertEquals(2)
function rt:gt-integer($code as xs:integer) {
    count(collection($rt:COLLECTION)//address[range:gt(city/@code, $code)])
};

declare
    %test:args(76878)
    %test:assertEquals(2)
    %test:args(89283)
    %test:assertEquals(1)
    %test:args(65463)
    %test:assertEquals(3)
function rt:ge-integer($code as xs:integer) {
    count(collection($rt:COLLECTION)//address[range:ge(city/@code, $code)])
};

declare
    %test:args(76878)
    %test:assertEquals(2)
    %test:args(65463)
    %test:assertEquals(1)
function rt:lt-integer($code as xs:integer) {
    count(collection($rt:COLLECTION)//address[range:lt(city/@code, $code)])
};

declare
    %test:args(76878)
    %test:assertEquals(3)
    %test:args(65463)
    %test:assertEquals(2)
function rt:le-integer($code as xs:integer) {
    count(collection($rt:COLLECTION)//address[range:le(city/@code, $code)])
};

declare
    %test:args("Rudi")
    %test:assertEquals("Rüsselsheim")
    %test:args("Berta")
    %test:assertEquals("Almweide")
function rt:starts-with-string($name as xs:string) {
    collection($rt:COLLECTION)//address[range:starts-with(name, $name)]/city/text()
};

declare
    %test:args("Rüssel")
    %test:assertEquals("Rüsselsheim")
    %test:args("Muh")
    %test:assertEquals("Almweide")
function rt:ends-with-string($name as xs:string) {
    collection($rt:COLLECTION)//address[range:ends-with(name, $name)]/city/text()
};

declare
    %test:args("üss")
    %test:assertEquals("Rüsselsheim")
    %test:args("ta M")
    %test:assertEquals("Almweide")
function rt:contains-string($name as xs:string) {
    collection($rt:COLLECTION)//address[range:contains(name, $name)]/city/text()
};

declare
    %test:args(".*Rüssel")
    %test:assertEquals("Rüsselsheim")
function rt:matches-string($name as xs:string) {
    collection($rt:COLLECTION)//address[range:matches(name, $name)]/city/text()
};

declare
    %test:args("Rudi")
    %test:assertEquals("Rüsselsheim")
    %test:args("Berta")
    %test:assertEquals("Almweide")
function rt:field-starts-with-string($name as xs:string) {
    collection($rt:COLLECTION)//range:field-starts-with("address-name", $name)/city/text()
};

declare
    %test:args("Rüssel")
    %test:assertEquals("Rüsselsheim")
    %test:args("Muh")
    %test:assertEquals("Almweide")
function rt:field-ends-with-string($name as xs:string) {
    collection($rt:COLLECTION)//range:field-ends-with("address-name", $name)/city/text()
};

declare
    %test:args("üss")
    %test:assertEquals("Rüsselsheim")
    %test:args("ta M")
    %test:assertEquals("Almweide")
function rt:field-contains-string($name as xs:string) {
    collection($rt:COLLECTION)//range:field-contains("address-name", $name)/city/text()
};

declare
    %test:args(".*[rR]üss.*")
    %test:assertEquals("Rüsselsheim")
    %test:args(".*ta M.*")
    %test:assertEquals("Almweide")
function rt:field-matches-string($name as xs:string) {
    collection($rt:COLLECTION)//range:field-matches("address-name", $name)/city/text()
};

declare 
    %test:args("main", "official", "Hofthiergarten")
    %test:assertEquals("Hofthiergarten")
    %test:args("ref", "inofficial", "Hofthiergarten")
    %test:assertEquals("Hofthiergarten")
    %test:args("main", "official", "Dorfprozelten")
    %test:assertEquals("Dorfprozelten")
function rt:equality-field-nested($type as xs:string, $subtype as xs:string, $name as xs:string) {
    collection($rt:COLLECTION)//range:field-eq(("type", "subtype", "name"), $type, $subtype, $name)/text()
};

(: Test multi-value field lookups :)
declare
    %test:assertEquals("Hofthiergarten", "Dorfprozelten")
function rt:equality-field-nested-multi() {
    collection($rt:COLLECTION)
        //range:field-eq(
            ("type", "subtype", "name"),
            "main", "official", ("Hofthiergarten", "Dorfprozelten"))
        /text()
};

declare 
    %test:assertEquals("Almweide")
function rt:remove-document() {
    let $stored := xmldb:store($rt:COLLECTION, "test2.xml", $rt:DATA)
    return (
        doc($rt:COLLECTION || "/test2.xml")/range:field-eq("address-name", "Berta Muh")/city/string(),
        let $null := xmldb:remove($rt:COLLECTION, "test2.xml") return $null,
        doc($rt:COLLECTION || "/test2.xml")/range:field-eq("address-name", "Berta Muh")/city/string()
    )
};

declare 
    %test:assertEquals("Uferweg 67", "Bach")
function rt:update-insert() {
    update insert
        <address>
            <name>Willi Wiesel</name>
            <street>Uferweg 67</street>
            <city code="77777">Bach</city>
        </address>
    into doc($rt:COLLECTION || "/test.xml")/test,
    range:field-eq("address-name", "Willi Wiesel")/street/text(),
    collection($rt:COLLECTION)//address[range:eq(name, "Willi Wiesel")]/city/text()
};

declare 
    %test:assertEmpty
function rt:update-delete() {
    update delete collection($rt:COLLECTION)/test/address[range:eq(name, "Berta Muh")],
    collection($rt:COLLECTION)//address[range:eq(name, "Berta Muh")],
    range:field-eq("address-name", "Berta Muh")
};

declare
    %test:assertEquals("Am Staudamm 3", "Bach")
function rt:update-replace() {
    update replace collection($rt:COLLECTION)/test/address[range:eq(name, "Albert Amsel")]
    with
        <address>
            <name>Berta Bieber</name>
            <street>Am Staudamm 3</street>
            <city code="77777">Bach</city>
        </address>,
    collection($rt:COLLECTION)//address[range:eq(name, "Albert Amsel")],
    range:field-eq("address-name", "Albert Amsel"),
    collection($rt:COLLECTION)//address[range:eq(name, "Berta Bieber")]/street/text(),
    range:field-eq("address-name", "Berta Bieber")/city/text()
};

declare
    %test:assertEquals("Am Waldrand 4", "Wiesental")
function rt:update-value() {
    update value collection($rt:COLLECTION)/test/address/name[range:eq(., "Pü Reh")] with "Rita Rebhuhn",
    collection($rt:COLLECTION)//address[range:eq(name, "Pü Reh")],
    range:field-eq("address-name", "Pü Reh"),
    collection($rt:COLLECTION)//address[range:eq(name, "Rita Rebhuhn")]/street/text(),
    range:field-eq("address-name", "Rita Rebhuhn")/city/text()
};

(: --- GitHub #4110: case-insensitive range index on attributes --- :)

declare
    %test:assertEquals(3)
function rt:attr-case-insensitive-lower() {
    count(collection($rt:COLLECTION)//a[@lemma = "aaa"])
};

declare
    %test:assertEquals(3)
function rt:attr-case-insensitive-mixed() {
    count(collection($rt:COLLECTION)//a[@lemma = "AaA"])
};

declare
    %test:assertEquals(3)
function rt:attr-case-insensitive-upper() {
    count(collection($rt:COLLECTION)//a[@lemma = "AAA"])
};

declare
    %test:assertEquals(0)
function rt:attr-case-insensitive-no-match() {
    count(collection($rt:COLLECTION)//a[@lemma = "different"])
};

declare
    %test:assertEquals(3)
function rt:attr-case-sensitive-exact-match() {
    count(collection($rt:COLLECTION)//a[@lemma-strict = "OnlyThis"])
};

declare
    %test:assertEquals(0)
function rt:attr-case-sensitive-lower-no-match() {
    count(collection($rt:COLLECTION)//a[@lemma-strict = "onlythis"])
};

(: --- GitHub #4016: empty key arguments for range:field-eq --- :)

declare
    %test:assertEquals(0)
function rt:empty-keys-no-error() {
    let $id := ()
    let $effectiveDate := ()
    return
        count(collection($rt:COLLECTION)//dataset[@id = $id][@effectiveDate = $effectiveDate])
};

declare
    %test:assertEquals(1)
function rt:non-empty-keys-match() {
    let $id := "2.16.840.1.113883.3.1937.99.62.3.1.1"
    let $effectiveDate := "2012-05-30T11:32:36"
    return count(collection($rt:COLLECTION)//dataset[@id = $id][@effectiveDate = $effectiveDate])
};

declare
    %test:assertEquals(0)
function rt:explicit-empty-field-eq() {
    count(collection($rt:COLLECTION)//range:field-eq(("dataset-id", "dataset-effectiveDate"), (), ()))
};

(: --- GitHub #3114: range index + ft:query predicate interaction --- :)

declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function rt:issue3114-query-name-field($col as xs:string) {
    count(collection($col)/ft:query(.//field[@name = "name"], $rt:I3114_FTQUERY))
};

declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function rt:issue3114-query-name-field-alternate($col as xs:string) {
    count(collection($col)//record/field[@name = "name"][ft:query(., $rt:I3114_FTQUERY)])
};

declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function rt:issue3114-query-record-with-name-field($col as xs:string) {
    count(collection($col)//record[ft:query(.//field[@name = "name"], $rt:I3114_FTQUERY)])
};

declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function rt:issue3114-query-record-with-name-field-alternate($col as xs:string) {
    count(collection($col)//record[.//field[@name = "name"][ft:query(., $rt:I3114_FTQUERY)]])
};

(: --- GitHub #1353: range index on nested mixed content --- :)

declare
    %test:assertEquals(1)
function rt:issue1353-contains-abc-works() {
    count(collection($rt:COLLECTION)//def[contains(., 'abc')])
};

declare
    %test:assertEquals(1)
function rt:issue1353-contains-abc-def-works() {
    count(collection($rt:COLLECTION)//def[contains(., 'abc def')])
};

declare
    %test:assertEquals(1)
function rt:issue1353-contains-ghi() {
    count(collection($rt:COLLECTION)//def[contains(., 'ghi')])
};

declare
    %test:assertEquals(1)
function rt:issue1353-contains-def-ghi() {
    count(collection($rt:COLLECTION)//def[contains(., 'def ghi')])
};