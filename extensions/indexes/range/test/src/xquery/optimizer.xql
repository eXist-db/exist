xquery version "3.0";

(:~
 : Test query rewriting for range index.
 : 
 : Expressions use the @test:stats annotation to retrieve execution statistics
 : for each test function. All comparisons should be fully optimized.
 :)
module namespace ot="http://exist-db.org/xquery/range/optimizer/test";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";


declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ot:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:tei="http://www.tei-c.org/ns/1.0">
            <fulltext default="none" attributes="false"/>
            <lucene>
                <text field="lucene-name" qname="name"/>
            </lucene>
            <range>
                <create match="//tei:placeName">
                    <field name="name" type="xs:string" nested="no"/>
                    <field name="type" match="@type" type="xs:string"/>
                    <field name="subtype" match="@subtype" type="xs:string"/>
                </create>
                <create match="//address">
                    <field name="address-city" match="city" type="xs:string"/>
                    <field name="address-street" match="street" type="xs:string"/>
                    <field name="address-email" match="contact/email" type="xs:string"/>
                </create>
                <create match="/test/address/name"/>
                <create match="/test/address/city/@code" type="xs:integer"/>
                <create qname="@id" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $ot:DATA_NESTED := 
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
    
declare variable $ot:DATA :=
    <test>
        <address id="muh">
            <name>Berta Muh</name>
            <street>Wiesenweg 14</street>
            <city code="65463">Almweide</city>
            <contact>
                <email>berta@milchvieh.org</email>
            </contact>
        </address>
        <address id="rüssel">
            <name>Rudi Rüssel</name>
            <street>Elefantenweg 67</street>
            <city code="65428">Rüsselsheim</city>
            <contact>
                <email>rudi@trompeter.de</email>
            </contact>
        </address>
        <address id="amsel">
            <name>Albert Amsel</name>
            <street>Birkenstraße 77</street>
            <city code="76878">Waldstadt</city>
            <contact>
                <email>albert@zwitschern.de</email>
            </contact>
        </address>
        <address id="reh">
            <name>Pü Reh</name>
            <street>Am Waldrand 4</street>
            <city code="89283">Wiesental</city>
            <contact>
                <email>pü@wildsau.net</email>
            </contact>
        </address>
    </test>;

declare variable $ot:COLLECTION_NAME := "optimizertest";
declare variable $ot:COLLECTION := "/db/" || $ot:COLLECTION_NAME;

declare
    %test:setUp
function ot:setup() {
    xmldb:create-collection("/db/system/config/db", $ot:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ot:COLLECTION_NAME, "collection.xconf", $ot:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ot:COLLECTION_NAME),
    xmldb:store($ot:COLLECTION, "test.xml", $ot:DATA),
    xmldb:store($ot:COLLECTION, "nested.xml", $ot:DATA_NESTED)
};

declare
    %test:tearDown
function ot:cleanup() {
    xmldb:remove($ot:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ot:COLLECTION_NAME)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-eq-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name = $name]
};

declare
    %test:stats
    %test:args("Rudi")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-starts-with-string($name as xs:string) {
    collection($ot:COLLECTION)//address[starts-with(name, $name)]
};

declare
    %test:stats
    %test:args("Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-ends-with-string($name as xs:string) {
    collection($ot:COLLECTION)//address[ends-with(name, $name)]
};

declare
    %test:stats
    %test:args("udi ")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-contains-string($name as xs:string) {
    collection($ot:COLLECTION)//address[contains(name, $name)]
};

declare
    %test:stats
    %test:args("[rR]udi .*")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-matches-string($name as xs:string) {
    collection($ot:COLLECTION)//address[range:matches(name, $name)]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-eq-string-self($name as xs:string) {
    collection($ot:COLLECTION)//address/name[. = $name]
};

declare
    %test:stats
    %test:args(65428)
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-eq-int-attribute($code as xs:integer) {
    collection($ot:COLLECTION)//address/city[@code = $code]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-lt-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name < $name]
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(1)
function ot:lt-string($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name < $name])
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-le-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name <= $name]
};

declare
    %test:args("Berta Muh")
    %test:assertEquals(2)
function ot:le-string($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name <= $name])
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-gt-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name > $name]
};

declare
    %test:args("Pü Reh")
    %test:assertEquals(1)
function ot:gt-string($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name > $name])
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-ge-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name > $name]
};

declare
    %test:args("Pü Reh")
    %test:assertEquals(2)
function ot:ge-string($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name >= $name])
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-eq-field($city as xs:string) {
    collection($ot:COLLECTION)//address[city = $city]
};

declare
    %test:args("Rüsselsheim")
    %test:assertEquals(1)
function ot:eq-field($city as xs:string) {
    count(collection($ot:COLLECTION)//address[city = $city])
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-gt-field($city as xs:string) {
    collection($ot:COLLECTION)//address[city > $city]
};

declare
    %test:args("Rüsselsheim")
    %test:assertEquals(2)
function ot:gt-field($city as xs:string) {
    count(collection($ot:COLLECTION)//address[city > $city])
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-ge-field($city as xs:string) {
    collection($ot:COLLECTION)//address[city >= $city]
};

declare
    %test:args("Rüsselsheim")
    %test:assertEquals(3)
function ot:ge-field($city as xs:string) {
    count(collection($ot:COLLECTION)//address[city >= $city])
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-lt-field($city as xs:string) {
    collection($ot:COLLECTION)//address[city < $city]
};

declare
    %test:args("Rüsselsheim")
    %test:assertEquals(1)
function ot:lt-field($city as xs:string) {
    count(collection($ot:COLLECTION)//address[city < $city])
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-le-field($city as xs:string) {
    collection($ot:COLLECTION)//address[city <= $city]
};

declare
    %test:args("Rüsselsheim")
    %test:assertEquals(2)
function ot:le-field($city as xs:string) {
    count(collection($ot:COLLECTION)//address[city <= $city])
};

declare
    %test:stats
    %test:args("Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-starts-with-field($city as xs:string) {
    collection($ot:COLLECTION)//address[starts-with(city, $city)]
};

declare
    %test:stats
    %test:args("heim")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-ends-with-field($city as xs:string) {
    collection($ot:COLLECTION)//address[ends-with(city, $city)]
};

declare
    %test:stats
    %test:args("üssel")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-contains-field($city as xs:string) {
    collection($ot:COLLECTION)//address[contains(city, $city)]
};

declare
    %test:stats
    %test:args("[rR]üssel.*")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-matches-field($city as xs:string) {
    collection($ot:COLLECTION)//address[range:matches(city, $city)]
};

declare
    %test:stats
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-eq-field-multi($city as xs:string, $street as xs:string) {
    collection($ot:COLLECTION)//address[city = $city][street = $street]
};

declare
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertEquals(1)
function ot:eq-field-multi($city as xs:string, $street as xs:string) {
    count(collection($ot:COLLECTION)//address[city = $city][street = $street])
};

declare
    %test:stats
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-mixed-op-field($city as xs:string, $street as xs:string) {
    collection($ot:COLLECTION)//address[city > $city][street = $street]
};

declare
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertEquals(0)
function ot:mixed-op-field1($city as xs:string, $street as xs:string) {
    count(collection($ot:COLLECTION)//address[city > $city][street = $street])
};

declare
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertEquals(1)
function ot:mixed-op-field2($city as xs:string, $street as xs:string) {
    count(collection($ot:COLLECTION)//address[city >= $city][street = $street])
};

declare
    %test:stats
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:no-optimize-field-multi($city as xs:string, $street as xs:string) {
    collection($ot:COLLECTION)//address[street = $street][1]
};

declare
    %test:stats
    %test:args("berta@milchview.org")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-eq-field-nested($email as xs:string) {
    collection($ot:COLLECTION)//address[contact/email = $email]
};

declare
    %test:stats
    %test:args("berta@milchview.org")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-lt-field-nested($email as xs:string) {
    collection($ot:COLLECTION)//address[contact/email < $email]
};

declare 
    %test:args("main", "official", "Hofthiergarten")
    %test:assertEquals("Hofthiergarten")
    %test:args("ref", "inofficial", "Hofthiergarten")
    %test:assertEquals("Hofthiergarten")
    %test:args("main", "official", "Dorfprozelten")
    %test:assertEquals("Dorfprozelten")
function ot:equality-field-nested($type as xs:string, $subtype as xs:string, $name as xs:string) {
    //tei:placeName[@type = $type][@subtype = $subtype][. = $name]/text()
};

declare 
    %test:stats
    %test:args("main", "official", "Hofthiergarten")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ot:optimize-field-self($type as xs:string, $subtype as xs:string, $name as xs:string) {
    //tei:placeName[@type = $type][@subtype = $subtype][. = $name]/text()
};