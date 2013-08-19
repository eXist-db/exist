xquery version "3.0";

module namespace rt="http://exist-db.org/xquery/range/test";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $rt:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene>
                <text field="lucene-name" qname="name"/>
            </lucene>
            <range>
                <create match="//address">
                    <field name="address-name" match="name" type="xs:string"/>
                    <field name="address-city" match="city" type="xs:string"/>
                    <field name="address-code" match="city/@code" type="xs:integer"/>
                </create>
                <create match="/test/address/name"/>
                <create match="/test/address/city" type="xs:string" collation="?lang=de-DE&amp;strength=primary"/>
                <create match="/test/address/city/@code" type="xs:integer"/>
                <create qname="@id" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $rt:DATA :=
    <test>
        <address id="muh">
            <name>Berta Muh</name>
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
    </test>;

declare variable $rt:DATA2 :=
    <object>
        <parameter>
            <name>key1</name>
            <value>value1</value>
        </parameter>
    </object>;
    
declare
    %test:setUp
function rt:setup() {
    xmldb:create-collection("/db/system/config/db", "rangetest"),
    xmldb:store("/db/system/config/db/rangetest", "collection.xconf", $rt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", "rangetest"),
    xmldb:store("/db/rangetest", "test.xml", $rt:DATA)
};

declare
    %test:tearDown
function rt:cleanup() {
    xmldb:remove("/db/rangetest"),
    xmldb:remove("/db/system/config/db/rangetest")
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals("Rüsselsheim")
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
function rt:equality-string($name as xs:string) {
    //address[range:eq(name, $name)]/city/text()
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals("Rudi Rüssel")
    %test:args("Berta Muh")
    %test:assertEquals("Berta Muh")
function rt:equality-string-self($name as xs:string) {
    //address/name[range:eq(., $name)]/text()
};

declare
    %test:args(65428)
    %test:assertEquals("Rüsselsheim", "Rüsselsheim")
function rt:equality-int-attribute($code as xs:integer) {
    //address/city[range:eq(@code, $code)]/text(),
    //address[range:eq(city/@code, $code)]/city/text()
};

declare
    %test:args("muh")
    %test:assertEquals("Berta Muh")
function rt:equality-qname-string-attribute($id as xs:string) {
    //address[range:eq(@id, $id)]/name/text()
};

declare
    %test:args("russelsheim")
    %test:assertEquals("Rüsselsheim")
    %test:args("almweide")
    %test:assertEquals("Almweide")
function rt:equality-string-collation($name as xs:string) {
    //address[range:eq(city, $name)]/city/text()
};

declare 
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh")
    %test:assertEquals("Wiesental")
function rt:equality-fields($name as xs:string) {
    range:field-eq("address-name", $name)/city/text()
};

declare 
    %test:args(65428)
    %test:assertEquals("Rudi Rüssel")
function rt:equality-field-integer($code as xs:integer) {
    range:field-eq("address-code", $code)/name/text()
};

declare 
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh")
    %test:assertEquals("Wiesental")
function rt:equality-fields-with-context($name as xs:string) {
    doc("/db/rangetest/test.xml")/range:field-eq("address-name", $name)/city/text()
};

declare 
    %test:args("Berta Muh", "Almweide")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh", "Wiesental")
    %test:assertEquals("Wiesental")
function rt:equality-fields-multi($name as xs:string, $city as xs:string) {
    range:field-eq(("address-name", "address-city"), $name, $city)/city/text()
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
    count(//address[range:gt(name, $name)])
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(3)
    %test:args("Albert Amsel")
    %test:assertEquals(4)
    %test:args("Pü Reh")
    %test:assertEquals(2)
function rt:ge-string($name as xs:string) {
    count(//address[range:ge(name, $name)])
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(1)
    %test:args("Albert Amsel")
    %test:assertEquals(0)
    %test:args("Pü Reh")
    %test:assertEquals(2)
function rt:lt-string($name as xs:string) {
    count(//address[range:lt(name, $name)])
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(2)
    %test:args("Albert Amsel")
    %test:assertEquals(1)
    %test:args("Pü Reh")
    %test:assertEquals(3)
function rt:le-string($name as xs:string) {
    count(//address[range:le(name, $name)])
};

declare
    %test:args(76878)
    %test:assertEquals(1)
    %test:args(89283)
    %test:assertEquals(0)
    %test:args(65463)
    %test:assertEquals(2)
function rt:gt-integer($code as xs:integer) {
    count(//address[range:gt(city/@code, $code)])
};

declare
    %test:args(76878)
    %test:assertEquals(2)
    %test:args(89283)
    %test:assertEquals(1)
    %test:args(65463)
    %test:assertEquals(3)
function rt:ge-integer($code as xs:integer) {
    count(//address[range:ge(city/@code, $code)])
};

declare
    %test:args(76878)
    %test:assertEquals(2)
    %test:args(65463)
    %test:assertEquals(1)
function rt:lt-integer($code as xs:integer) {
    count(//address[range:lt(city/@code, $code)])
};

declare
    %test:args(76878)
    %test:assertEquals(3)
    %test:args(65463)
    %test:assertEquals(2)
function rt:le-integer($code as xs:integer) {
    count(//address[range:le(city/@code, $code)])
};

declare
    %test:args("Rudi")
    %test:assertEquals("Rüsselsheim")
    %test:args("Berta")
    %test:assertEquals("Almweide")
function rt:starts-with-string($name as xs:string) {
    //address[range:starts-with(name, $name)]/city/text()
};

declare
    %test:args("Rüssel")
    %test:assertEquals("Rüsselsheim")
    %test:args("Muh")
    %test:assertEquals("Almweide")
function rt:ends-with-string($name as xs:string) {
    //address[range:ends-with(name, $name)]/city/text()
};

declare
    %test:args("üss")
    %test:assertEquals("Rüsselsheim")
    %test:args("ta M")
    %test:assertEquals("Almweide")
function rt:contains-string($name as xs:string) {
    //address[range:contains(name, $name)]/city/text()
};

declare
    %test:args("Rudi")
    %test:assertEquals("Rüsselsheim")
    %test:args("Berta")
    %test:assertEquals("Almweide")
function rt:field-starts-with-string($name as xs:string) {
    range:field-starts-with("address-name", $name)/city/text()
};

declare
    %test:args("Rüssel")
    %test:assertEquals("Rüsselsheim")
    %test:args("Muh")
    %test:assertEquals("Almweide")
function rt:field-ends-with-string($name as xs:string) {
    range:field-ends-with("address-name", $name)/city/text()
};

declare
    %test:args("üss")
    %test:assertEquals("Rüsselsheim")
    %test:args("ta M")
    %test:assertEquals("Almweide")
function rt:field-contains-string($name as xs:string) {
    range:field-contains("address-name", $name)/city/text()
};

declare 
    %test:assertEquals("Almweide")
function rt:remove-document() {
    let $stored := xmldb:store("/db/rangetest", "test2.xml", $rt:DATA)
    return (
        doc("/db/rangetest/test2.xml")/range:field-eq("address-name", "Berta Muh")/city/string(),
        let $null := xmldb:remove("/db/rangetest", "test2.xml") return $null,
        doc("/db/rangetest/test2.xml")/range:field-eq("address-name", "Berta Muh")/city/string()
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
    into doc("/db/rangetest/test.xml")/test,
    range:field-eq("address-name", "Willi Wiesel")/street/text(),
    //address[range:eq(name, "Willi Wiesel")]/city/text()
};

declare 
    %test:assertEmpty
function rt:update-delete() {
    update delete /test/address[range:eq(name, "Berta Muh")],
    //address[range:eq(name, "Berta Muh")],
    range:field-eq("address-name", "Berta Muh")
};

declare
    %test:assertEquals("Am Staudamm 3", "Bach")
function rt:update-replace() {
    update replace /test/address[range:eq(name, "Albert Amsel")]
    with
        <address>
            <name>Berta Bieber</name>
            <street>Am Staudamm 3</street>
            <city code="77777">Bach</city>
        </address>,
    //address[range:eq(name, "Albert Amsel")],
    range:field-eq("address-name", "Albert Amsel"),
    //address[range:eq(name, "Berta Bieber")]/street/text(),
    range:field-eq("address-name", "Berta Bieber")/city/text()
};

declare
    %test:assertEquals("Am Waldrand 4", "Wiesental")
function rt:update-value() {
    update value /test/address/name[range:eq(., "Pü Reh")] with "Rita Rebhuhn",
    //address[range:eq(name, "Pü Reh")],
    range:field-eq("address-name", "Pü Reh"),
    //address[range:eq(name, "Rita Rebhuhn")]/street/text(),
    range:field-eq("address-name", "Rita Rebhuhn")/city/text()
};