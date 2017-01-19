xquery version "3.1";

(:~
 : Test conditions for complex range configuration elements.
 :
 :)
module namespace ct="http://exist-db.org/xquery/range/conditions/test";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ct:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:tei="http://www.tei-c.org/ns/1.0">
            <range>
                <create qname="tei:note">
                    <condition attribute="type" value="availability" />
                    <field name="availability" type="xs:string" case="no"></field>
                </create>
                <create qname="tei:note">
                    <condition attribute="type" value="text_type" />
                    <field name="text_type" type="xs:string" case="no"></field>
                </create>
                <create match="//tei:note">
                    <condition attribute="type" value="orig_place" />
                    <field name="orig_place" match="tei:place/tei:placeName" type="xs:string" case="no"></field>
                </create>
                <create qname="tei:note" type="xs:string" case="no" />
                <create qname="tei:placeName">
                    <condition attribute="type" value="someType" />
                    <condition attribute="cert" value="high" />
                    <field name="certainPlaceOfSomeType" type="xs:string" />
                </create>
                <create qname="tei:placeName">
                    <field name="allTypesOfPlaces" match="@type" type="xs:string" />
                </create>
                <create qname="tei:placeName">
                    <condition attribute="cert" value="low" />
                    <field name="typesOfUncertainPlaces" match="@type" type="xs:string" />
                </create>
            </range>
        </index>
    </collection>;


declare variable $ct:DATA :=
    <TEI xmlns="http://www.tei-c.org/ns/1.0">
        <teiHeader>
            <fileDesc>
                <titleStmt><title>conditional fields!</title></titleStmt>
            </fileDesc>
            <sourceDesc>
                <msDesc>
                    <msContents>
                        <msItemStruct>
                            <note type="availability">publiziert</note>
                            <note type="text_type">literarisch</note>
                            <note type="orig_place">
                                <place>
                                    <placeName cert="low" type="thirdType">Oxyrhynchos</placeName>
                                </place>
                            </note>
                            <note>
                                <place>
                                    <placeName cert="high" type="someType" n="1">Achmim</placeName>
                                </place>
                                <place>
                                    <placeName cert="low" type="someType">Bubastos</placeName>
                                </place>
                                <place>
                                    <placeName cert="high" type="someOtherType">Alexandria</placeName>
                                </place>
                            </note>
                            <note>foo</note>
                            <note type="bar">foo</note>
                            <note type="something">literarisch</note>
                        </msItemStruct>
                    </msContents>
                </msDesc>
            </sourceDesc>
        </teiHeader>
    </TEI>;

declare variable $ct:COLLECTION_NAME := "optimizertest";
declare variable $ct:COLLECTION := "/db/" || $ct:COLLECTION_NAME;

declare
%test:setUp
function ct:setup() {
    xmldb:create-collection("/db/system/config/db", $ct:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ct:COLLECTION_NAME, "collection.xconf", $ct:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ct:COLLECTION_NAME),
    xmldb:store($ct:COLLECTION, "data2.xml", $ct:DATA)

};

declare
%test:tearDown
function ct:cleanup() {
    xmldb:remove($ct:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ct:COLLECTION_NAME)
};

(: rewrite expression with predicate that matches a condition to a field  :)
(: the standard range lookup should not be used for the @type predicate :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2] and not($result//stats:index[@type='range'])")
function ct:optimize-with-condition() {
    collection($ct:COLLECTION)//tei:note[@type="availability"][.="publiziert"]
};

(: rewrite expression with predicate that matches a condition to a field  :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2] and not($result//stats:index[@type='range'])")
function ct:optimize-with-condition2() {
    collection($ct:COLLECTION)//tei:note[@type="orig_place"][tei:place/tei:placeName eq "Oxyrhynchos"]
};

(: do not use a conditional field for optimizing if condition does not match :)
declare
%test:stats
%test:assertXPath("not($result//stats:index[@type = 'new-range'][@optimization = 2])")
function ct:no-optimize-condition2() {
    collection($ct:COLLECTION)//tei:note[@type="something"][. = "literarisch"]
};

(: only the elements matching the condition should have been indexed :)
declare
%test:assertEquals(1)
function ct:conditional-config-restricts-results() {
count(collection($ct:COLLECTION)//range:field-eq("text_type", "literarisch"))
};

(: if there are multiple config elements for a qname and some have a condition and :)
(: some have not, pick the one without condition if no condition matches :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function ct:use-config-without-condition-optimize() {
collection($ct:COLLECTION)//tei:note[.="foo"]
};

declare
%test:assertEquals(2)
function ct:use-config-without-condition-result() {
count(collection($ct:COLLECTION)//tei:note[.="foo"])
};

(: rewrite expression with multiple conditions to the correct field :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2][@calls = 1] and not($result//stats:index[@type='range'])")
function ct:multiple-conditions() {
collection($ct:COLLECTION)//tei:placeName[@cert="high"][@type="someType"][.="Achmim"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2][@calls = 1] and $result//stats:index[@type='range'][@calls=1]")
function ct:multiple-conditions-inbetween() {
collection($ct:COLLECTION)//tei:placeName[@cert="high"][not(.="")][@type="someType"][.="Achmim"]
};