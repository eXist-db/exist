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

(:~
 : Test query rewriting for range index.
 :
 : Expressions use the @test:stats annotation to retrieve execution statistics
 : for each test function. All comparisons should be fully optimized.
 :)
module namespace ot="http://exist-db.org/xquery/range/test/optimizer";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ot:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:tei="http://www.tei-c.org/ns/1.0">
            <lucene>
                <text field="lucene-name" qname="name"/>
            </lucene>
            <range>
                <create match="//tei:placeName">
                    <field name="name" type="xs:string" nested="no"/>
                    <field name="type" match="@type" type="xs:string"/>
                    <field name="subtype" match="@subtype" type="xs:string"/>
                </create>
                <create qname="tei:orth" type="xs:string" collation="?lang=sr&amp;strength=primary" case="no"/>
                <create match="//address">
                    <filter class="org.apache.lucene.analysis.core.LowerCaseFilter"/>
                    <field name="address-city" match="city" type="xs:string"/>
                    <field name="address-street" match="street" type="xs:string"/>
                    <field name="address-email" match="contact/email" type="xs:string"/>
                    <field name="address-name" match="name3" type="xs:string"/>
                </create>
                <create match="/test/address/name"/>
                <create match="/test/address/name2">
                    <filter class="org.apache.lucene.analysis.core.LowerCaseFilter"/>
                    <filter class="org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter"/>
                </create>
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
            <name2>Berta Muh</name2>
            <name3>Berta Muh</name3>
            <street>Wiesenweg 14</street>
            <city code="65463">Almweide</city>
            <contact>
                <email>berta@milchvieh.org</email>
            </contact>
        </address>
        <address id="rüssel">
            <name>Rudi Rüssel</name>
            <name2>Rudi Rüssel</name2>
            <name3>Rudi Rüssel</name3>
            <street>Elefantenweg 67</street>
            <city code="65428">Rüsselsheim</city>
            <contact>
                <email>rudi@trompeter.de</email>
            </contact>
        </address>
        <address id="amsel">
            <name>Albert Amsel</name>
            <name2>Albert Amsel</name2>
            <name3>Albert Amsel</name3>
            <street>Birkenstraße 77</street>
            <city code="76878">Waldstadt</city>
            <contact>
                <email>albert@zwitschern.de</email>
            </contact>
        </address>
        <address id="reh">
            <name>Pü Reh</name>
            <name2>Pü Reh</name2>
            <name3>Pü Reh</name3>
            <street>Am Waldrand 4</street>
            <city code="89283">Wiesental</city>
            <contact>
                <email>pü@wildsau.net</email>
            </contact>
        </address>
    </test>;

declare variable $ot:DATA_SR_WITH_DIACRITICS :=
    <TEI xmlns="http://www.tei-c.org/ns/1.0">
        <teiHeader>
            <fileDesc>
                <titleStmt><title>sr with diacritics</title></titleStmt>
                <publicationStmt><distributor>sr with diacritics</distributor></publicationStmt>
                <sourceDesc><listOrg><org><name>test</name></org></listOrg></sourceDesc>
            </fileDesc>
        </teiHeader>
        <text>
            <body>
                <div>
                    <entryFree>
                        <form type="lemma">
                            <orth>Мла̀тишума</orth>
                        </form>
                        <form type="lemma">
                            <orth>Млатишума</orth>
                        </form>
                    </entryFree>
                </div>
            </body>
        </text>
    </TEI>;
    
declare variable $ot:COLLECTION_NAME := "optimizertest";
declare variable $ot:COLLECTION := "/db/" || $ot:COLLECTION_NAME;

declare
    %test:setUp
function ot:setup() {
    xmldb:create-collection("/db/system/config/db", $ot:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ot:COLLECTION_NAME, "collection.xconf", $ot:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ot:COLLECTION_NAME),
    xmldb:store($ot:COLLECTION, "test.xml", $ot:DATA),
    xmldb:store($ot:COLLECTION, "nested.xml", $ot:DATA_NESTED),
    xmldb:store($ot:COLLECTION, "diacritics.xml", $ot:DATA_SR_WITH_DIACRITICS)
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-eq-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name = $name]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-ne-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name != $name]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-ne-string2($name as xs:string) {
    collection($ot:COLLECTION)//address[name ne $name]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-ne-string-self($name as xs:string) {
    collection($ot:COLLECTION)//address/name[. ne $name]
};

declare
    %test:stats
    %test:args("Rudi")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-starts-with-string($name as xs:string) {
    collection($ot:COLLECTION)//address[starts-with(name, $name)]
};

declare
    %test:stats
    %test:args("Rüssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-ends-with-string($name as xs:string) {
    collection($ot:COLLECTION)//address[ends-with(name, $name)]
};

declare
    %test:stats
    %test:args("udi ")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-contains-string($name as xs:string) {
    collection($ot:COLLECTION)//address[contains(name, $name)]
};

declare
    %test:stats
    %test:args("[rR]udi .*")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-matches-string($name as xs:string) {
    collection($ot:COLLECTION)//address[range:matches(name, $name)]
};

declare
    %test:stats
    %test:args("[rR]udi .*")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'BASIC']")
function ot:optimize-matches-string-filtered($name as xs:string) {
    let $address := collection($ot:COLLECTION)//address
    return
        $address[range:matches(name, $name)]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-eq-string-self($name as xs:string) {
    collection($ot:COLLECTION)//address/name[. = $name]
};

declare
    %test:args("Rudi Rüssel")
function ot:eq-string-self-filtered($name as xs:string) {
    let $n := collection($ot:COLLECTION)//address/name
    return
        $n[. = $name]
};

declare
    %test:stats
    %test:args(65428)
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-eq-int-attribute($code as xs:integer) {
    collection($ot:COLLECTION)//address/city[@code = $code]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-lt-string($name as xs:string) {
    collection($ot:COLLECTION)//address[name < $name]
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals(3)
function ot:ne-string($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name != $name])
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals(3)
function ot:ne-string-self($name as xs:string) {
    count(collection($ot:COLLECTION)//address/name[. != $name])
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-ne-field($city as xs:string) {
    collection($ot:COLLECTION)//address[city != $city]
};

declare
    %test:args("Rüsselsheim")
    %test:assertEquals(3)
function ot:ne-field($city as xs:string) {
    count(collection($ot:COLLECTION)//address[city != $city])
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-starts-with-field($city as xs:string) {
    collection($ot:COLLECTION)//address[starts-with(city, $city)]
};

declare
    %test:stats
    %test:args("heim")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-ends-with-field($city as xs:string) {
    collection($ot:COLLECTION)//address[ends-with(city, $city)]
};

declare
    %test:stats
    %test:args("üssel")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-contains-field($city as xs:string) {
    collection($ot:COLLECTION)//address[contains(city, $city)]
};

declare
    %test:stats
    %test:args("[rR]üssel.*")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-matches-field($city as xs:string) {
    collection($ot:COLLECTION)//address[range:matches(city, $city)]
};

declare
    %test:stats
    %test:args("[rR]üssel.*")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'BASIC']")
function ot:matches-field-filtered($city as xs:string) {
    let $address := collection($ot:COLLECTION)//address
    return
        $address[range:matches(city, $city)]
};

declare
    %test:stats
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertEquals(1)
function ot:eq-field-multi-filtered($city as xs:string, $street as xs:string) {
    let $address := collection($ot:COLLECTION)//address
    return
        count($address[city = $city][street = $street])
};

declare
    %test:stats
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-mixed-op-field($city as xs:string, $street as xs:string) {
    collection($ot:COLLECTION)//address[city > $city][street = $street]
};

declare
    %test:args("rüsselsheim", "Elefantenweg 67")
    %test:assertEquals(0)
function ot:mixed-op-field1($city as xs:string, $street as xs:string) {
    count(collection($ot:COLLECTION)//address[city > $city][street = $street])
};

declare
    %test:args("rüsselsheim", "Elefantenweg 67")
    %test:assertEquals(1)
function ot:mixed-op-field2($city as xs:string, $street as xs:string) {
    count(collection($ot:COLLECTION)//address[city >= $city][street = $street])
};

declare
    %test:stats
    %test:args("Rüsselsheim", "Elefantenweg 67")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:no-optimize-field-multi($city as xs:string, $street as xs:string) {
    collection($ot:COLLECTION)//address[street = $street][1]
};

declare
    %test:stats
    %test:args("berta@milchview.org")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-eq-field-nested($email as xs:string) {
    collection($ot:COLLECTION)//address[contact/email = $email]
};

declare
    %test:stats
    %test:args("berta@milchview.org")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
    collection($ot:COLLECTION)//tei:placeName[@type = $type][@subtype = $subtype][. = $name]/text()
};

declare
    %test:stats
    %test:args("main", "official", "Hofthiergarten")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function ot:optimize-field-self($type as xs:string, $subtype as xs:string, $name as xs:string) {
    collection($ot:COLLECTION)//tei:placeName[@type = $type][@subtype = $subtype][. = $name]/text()
};

declare 
    %test:assertEquals("Rudi Rüssel")
function ot:parent-attr-equals() {
    collection($ot:COLLECTION)//name[parent::address/@id = "rüssel"]/text()
};

declare 
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'range'][@optimization-level eq 'NONE']")
function ot:optimize-parent-attr-equals() {
    collection($ot:COLLECTION)//name[parent::address/@id = "rüssel"]/text()
};

declare 
    %test:assertEquals("Rudi Rüssel")
function ot:parent-nested-attr-equals() {
    collection($ot:COLLECTION)//name[parent::address[@id = "rüssel"]]/text()
};

declare 
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'range'][@optimization-level eq 'NONE']")
function ot:optimize-parent-nested-attr-equals() {
    collection($ot:COLLECTION)//name[parent::address[@id = "rüssel"]]/text()
};

declare 
    %test:assertEquals("Rudi Rüssel")
function ot:self-parent-attr-equals() {
    collection($ot:COLLECTION)//name[./parent::address/@id = "rüssel"]/text()
};

declare 
    %test:assertEquals("Rudi Rüssel")
function ot:self-parent-nested-attr-equals() {
    collection($ot:COLLECTION)//name[./parent::address[@id = "rüssel"]]/text()
};

(: Filters :)

declare
    %test:args("rudi russel")
    %test:assertEquals("Rudi Rüssel")
    %test:args("rudi rüssel")
    %test:assertEquals("Rudi Rüssel")
function ot:eq-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[name2 = $name]/name2/text()
};

declare
    %test:args("Rudi Russel")
    %test:assertEquals(3)
function ot:ne-string-filtered($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name2 != $name])
};

declare
    %test:args("russel")
    %test:assertEquals("Rudi Rüssel")
function ot:contains-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[contains(name2, $name)]/name2/text()
};

declare
    %test:args("Rudi Rus")
    %test:assertEquals("Rudi Rüssel")
function ot:starts-with-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[starts-with(name2, $name)]/name2/text()
};

declare
    %test:args("ussel")
    %test:assertEquals("Rudi Rüssel")
function ot:ends-with-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[ends-with(name2, $name)]/name2/text()
};

declare
    %test:args("Rudi rüssel")
    %test:assertEquals("Rudi Rüssel")
    %test:args("rudi rüssel")
    %test:assertEquals("Rudi Rüssel")
function ot:eq-field-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[name3 = $name]/name3/text()
};

declare
    %test:args("rudi rüssel")
    %test:assertEquals(3)
function ot:ne-field-string-filtered($name as xs:string) {
    count(collection($ot:COLLECTION)//address[name3 != $name])
};

declare
    %test:args("rüssel")
    %test:assertEquals("Rudi Rüssel")
function ot:contains-field-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[contains(name3, $name)]/name3/text()
};

declare
    %test:args("rudi rüs")
    %test:assertEquals("Rudi Rüssel")
function ot:starts-with-field-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[starts-with(name3, $name)]/name3/text()
};

declare
    %test:args("üssel")
    %test:assertEquals("Rudi Rüssel")
function ot:ends-with-field-string-filtered($name as xs:string) {
    collection($ot:COLLECTION)//address[ends-with(name3, $name)]/name3/text()
};

(: Collations :)

declare
    %test:args("Мла̀тишума")
    %test:assertEquals(2)
    %test:args("Млатишума")
    %test:assertEquals(2)
function ot:eq-string-collation-with-diacritics($name) {
    count(collection($ot:COLLECTION)//tei:form[tei:orth = $name])
};

declare
    %test:args("Мла̀тишума")
    %test:assertError("range:EXXQDYFT0001")
    %test:args("Млатишума")
    %test:assertError("range:EXXQDYFT0001")
function ot:contains-string-collation-with-diacritics($name) {
    count(collection($ot:COLLECTION)//tei:form[contains(tei:orth, $name)])
};

declare
    %test:args("Мла̀тишума")
    %test:assertEquals(0)
    %test:args("Млатишума")
    %test:assertEquals(0)
function ot:ne-string-collation-with-diacritics($name) {
    count(collection($ot:COLLECTION)//tei:form[tei:orth != $name])
};

(:~ See XPath general comparison optimisation bug #2786 :)
declare
    %test:assertEquals(1)
function ot:nested-element-rewrite-bug() {
    let $dita :=
        <dita>
            <topic>
                <prolog>
                    <metadata>
                        <data name="topicType" value="topicValue"/>
                    </metadata>
                </prolog>
                <topic>
                    <prolog>
                        <metadata>
                            <data name="topicType" value="topicValue"/>
                        </metadata>
                    </prolog>
                </topic>
            </topic>
        </dita>
    return
      count($dita/topic/prolog//data[@name eq 'topicType']/@value)
};