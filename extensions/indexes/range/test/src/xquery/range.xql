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
                <create match="/test/address/city" type="xs:string"/>
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
    //address[range:equals(name, $name)]/city/text()
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals("Rudi Rüssel")
    %test:args("Berta Muh")
    %test:assertEquals("Berta Muh")
function rt:equality-string-self($name as xs:string) {
    //address/name[range:equals(., $name)]/text()
};

declare
    %test:args(65428)
    %test:assertEquals("Rüsselsheim", "Rüsselsheim")
function rt:equality-int-attribute($code as xs:integer) {
    //address/city[range:equals(@code, $code)]/text(),
    //address[range:equals(city/@code, $code)]/city/text()
};

declare
    %test:args("muh")
    %test:assertEquals("Berta Muh")
function rt:equality-qname-string-attribute($id as xs:string) {
    //address[range:equals(@id, $id)]/name/text()
};

declare 
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh")
    %test:assertEquals("Wiesental")
function rt:equality-fields($name as xs:string) {
    range:field-equals("address-name", $name)/city/text()
};

declare 
    %test:args(65428)
    %test:assertEquals("Rudi Rüssel")
function rt:equality-field-integer($code as xs:integer) {
    range:field-equals("address-code", $code)/name/text()
};

declare 
    %test:args("Berta Muh")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh")
    %test:assertEquals("Wiesental")
function rt:equality-fields-with-context($name as xs:string) {
    doc("/db/rangetest/test.xml")/range:field-equals("address-name", $name)/city/text()
};

declare 
    %test:args("Berta Muh", "Almweide")
    %test:assertEquals("Almweide")
    %test:args("Pü Reh", "Wiesental")
    %test:assertEquals("Wiesental")
function rt:equality-fields-multi($name as xs:string, $city as xs:string) {
    range:field-equals(("address-name", "address-city"), $name, $city)/city/text()
};

(:declare :)
(:    %test:args("Berta Muh", "Almweide"):)
(:    %test:assertEquals(1):)
(:function rt:equality-fields-multi($name as xs:string, $city as xs:string) {:)
(:    count(/test[range:field-equals(("address-name", "address-city"), $name, $city)]):)
(:};:)

declare 
    %test:assertEquals("Almweide")
function rt:remove-document() {
    let $stored := xmldb:store("/db/rangetest", "test2.xml", $rt:DATA)
    return (
        doc("/db/rangetest/test2.xml")/range:field-equals("address-name", "Berta Muh")/city/string(),
        let $null := xmldb:remove("/db/rangetest", "test2.xml") return $null,
        doc("/db/rangetest/test2.xml")/range:field-equals("address-name", "Berta Muh")/city/string()
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
    range:field-equals("address-name", "Willi Wiesel")/street/text(),
    //address[range:equals(name, "Willi Wiesel")]/city/text()
};

declare 
    %test:assertEmpty
function rt:update-delete() {
    update delete /test/address[range:equals(name, "Berta Muh")],
    //address[range:equals(name, "Berta Muh")],
    range:field-equals("address-name", "Berta Muh")
};

declare
    %test:assertEquals("Am Staudamm 3", "Bach")
function rt:update-replace() {
    update replace /test/address[range:equals(name, "Albert Amsel")]
    with
        <address>
            <name>Berta Bieber</name>
            <street>Am Staudamm 3</street>
            <city code="77777">Bach</city>
        </address>,
    //address[range:equals(name, "Albert Amsel")],
    range:field-equals("address-name", "Albert Amsel"),
    //address[range:equals(name, "Berta Bieber")]/street/text(),
    range:field-equals("address-name", "Berta Bieber")/city/text()
};

declare
    %test:assertEquals("Am Waldrand 4", "Wiesental")
function rt:update-value() {
    update value /test/address/name[range:equals(., "Pü Reh")] with "Rita Rebhuhn",
    //address[range:equals(name, "Pü Reh")],
    range:field-equals("address-name", "Pü Reh"),
    //address[range:equals(name, "Rita Rebhuhn")]/street/text(),
    range:field-equals("address-name", "Rita Rebhuhn")/city/text()
};