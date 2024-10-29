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

(:~
 : Test conditions for complex range configuration elements.
 :
 :)
module namespace ct="http://exist-db.org/xquery/range/test/conditions";

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
                <create qname="tei:note">
                    <condition attribute="type" operator="starts-with" value="start" />
                    <field name="text_type_start" type="xs:string" case="no"></field>
                </create>
                <create qname="tei:note">
                    <condition attribute="type" operator="ends-with" value="end" />
                    <field name="text_type_end" type="xs:string" case="no"></field>
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

                <create qname="tei:term">
                    <condition attribute="n" operator="lt" value="b" />
                    <field name="termsBeforeB" type="xs:string" />
                </create>
                <create qname="tei:term">
                    <condition attribute="n" operator="gt" value="b" />
                    <field name="termsAfterB" type="xs:string" />
                </create>
                <create qname="tei:term">
                    <condition attribute="n" operator="le" value="b" />
                    <field name="termsBeforeOrEqualB" type="xs:string" />
                </create>
                <create qname="tei:term">
                    <condition attribute="n" operator="ge" value="b" />
                    <field name="termsAfterOrEqualB" type="xs:string" />
                </create>
                <create qname="tei:term">
                    <condition attribute="n" operator="ne" value="b" />
                    <field name="termsNotB" type="xs:string" />
                </create>

                <create qname="tei:entry">
                    <condition attribute="n" operator="contains" value="1234" />
                    <field name="entryNContains1234" type="xs:string" />
                </create>
                <create qname="tei:entry">
                    <condition attribute="n" operator="matches" value="some_\d+_thing" />
                    <field name="entryNMatches" type="xs:string" />
                </create>

                <create qname="tei:num">
                    <condition attribute="value" operator="lt" value="2" />
                    <field name="lessThanTwoString" type="xs:string" />
                </create>
                <create qname="tei:num">
                    <condition attribute="value" operator="lt" value="2" numeric="yes"/>
                    <field name="lessThanTwoNumeric" type="xs:string" />
                </create>
                <create qname="tei:num">
                    <condition attribute="value" operator="eq" value="1" numeric="yes"/>
                    <field name="exactlyOne" type="xs:string" />
                </create>

                <create qname="tei:figure">
                    <condition attribute="n" operator="lt" value="2" numeric="yes"/>
                    <field name="figNLT2" type="xs:string" />
                </create>

                <create qname="tei:p">
                    <condition attribute="type" value="aabbcc" case="no" />
                    <field name="pCase" type="xs:string" />
                </create>
                <create qname="tei:p">
                    <condition attribute="type" operator="matches" value="bb" case="no" />
                    <field name="pMatchCase" type="xs:string" />
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
                            <note type="start_end">startswithendswith</note>
                            <note>foo</note>
                            <note type="bar">foo</note>
                            <note type="something">literarisch</note>
                        </msItemStruct>
                    </msContents>
                </msDesc>
            </sourceDesc>
        </teiHeader>
        <text>
            <body>
                <p>
                    <term n="a">eins</term>
                    <term n="b">zwei</term>
                    <term n="c">drei</term>

                    <entry n="some_1234_thing">something</entry>

                    <num value="1">one</num>
                    <num value="110">onehundredandten</num>
                    <num value="2">two</num>
                    <num value="001.0">zerozeroonepointzero</num>

                    <figure n="1">one</figure>
                    <figure n="110">onehundredandten</figure>
                    <figure n="2">two</figure>
                </p>

                <p type="aaBBcc">CaseSensitivity</p>
            </body>
        </text>
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
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq() {
    collection($ct:COLLECTION)//tei:note[@type eq "availability"][.="publiziert"]
};

(: rewrite expression with predicate that matches a condition to a field  :)
(: the standard range lookup should not be used for the @type predicate :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq-numeric() {
    collection($ct:COLLECTION)//tei:num[@value = 1][.="publiziert"]
};

(: rewrite expression with predicate that matches a condition to a field  :)
(: the standard range lookup should not be used for the @type predicate :)
declare
%test:stats
%test:assertXPath("not($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:no-optimize-eq-numeric() {
    collection($ct:COLLECTION)//tei:num[@value = "1"][.="publiziert"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq-var() {
    let $var := "availability"
    return collection($ct:COLLECTION)//tei:note[@type=$var][.="publiziert"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq-func() {
    collection($ct:COLLECTION)//tei:note[@type=lower-case("AVAILABILITY")][.="publiziert"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq-reverse-var-nested() {
    let $var := "availability"
    let $var2 := $var
    return collection($ct:COLLECTION)//tei:note[$var2=@type][.="publiziert"]
};

declare variable $ct:var := "availability";
declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq-static-var() {
    collection($ct:COLLECTION)//tei:note[@type=$ct:var][.="publiziert"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq-reverse() {
    collection($ct:COLLECTION)//tei:note["availability" = @type][.="publiziert"]
};


(: rewrite expression with predicate that matches a condition to a field  :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-eq2() {
    collection($ct:COLLECTION)//tei:note[@type eq "orig_place"][tei:place/tei:placeName eq "Oxyrhynchos"]
};



declare
%test:assertEquals(1)
function ct:index-eq-no-case() {
count(range:index-keys-for-field("pCase", function($k, $n) { $k }, 10))
};

declare
%test:stats
%test:assertXPath("not($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:optimize-case() {
collection($ct:COLLECTION)//tei:note[@type eq "Availablity"][.="publiziert"]
};

declare
%test:assertXPath("count($result) eq 2 and contains($result, 'one') and contains($result, 'zerozeroonepointzero')")
function ct:index-eq-numeric() {
range:index-keys-for-field("exactlyOne", function($k, $n) { $k }, 10)
};

(: do not use a conditional field for optimizing if condition does not match :)
declare
%test:stats
%test:assertXPath("not($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:no-optimize-condition2() {
collection($ct:COLLECTION)//tei:note[@type eq "something"][. = "literarisch"]
};

(: only the elements matching the condition should have been indexed :)
declare
%test:assertEquals(1)
function ct:conditional-config-results-eq() {
count(collection($ct:COLLECTION)//range:field-eq("text_type", "literarisch"))
};

(: if there are multiple config elements for a qname and some have a condition and :)
(: some have not, pick the one without condition if no condition matches :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
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
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'][@calls = 1] and not($result//stats:index[@type eq 'range'])")
function ct:multiple-conditions() {
collection($ct:COLLECTION)//tei:placeName[@cert="high"][@type eq "someType"][.="Achmim"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'][@calls = 1] and $result//stats:index[@type eq 'range'][@calls=1]")
function ct:multiple-conditions-inbetween() {
collection($ct:COLLECTION)//tei:placeName[@cert="high"][not(.="")][@type eq "someType"][.="Achmim"]
};


declare
%test:assertEquals("startswithendswith")
function ct:index-ends-with() {
range:index-keys-for-field("text_type_end", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-ends-with() {
collection($ct:COLLECTION)//tei:note[ends-with(@type, "end")][. = "startswithendswith"]
};

declare
%test:assertEquals("startswithendswith")
function ct:index-starts-with() {
range:index-keys-for-field("text_type_start", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-starts-with() {
collection($ct:COLLECTION)//tei:note[starts-with(@type, "start")][. = "startswithendswith"]
};

declare
%test:assertEquals("eins")
function ct:index-lt() {
range:index-keys-for-field("termsBeforeB", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-lt() {
collection($ct:COLLECTION)//tei:term[@n < "b"][. = "eins"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-lt-numeric() {
collection($ct:COLLECTION)//tei:figure[@n < 2][. = "one"]
};

declare
%test:stats
%test:assertXPath("not($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:no-optimize-lt-numeric() {
collection($ct:COLLECTION)//tei:figure[@n < "2"][. = "one"]
};


declare
%test:assertXPath("count($result) eq 3 and contains($result, 'one') and contains($result, 'onehundredandten') and contains($result, 'zerozeroonepointzero')")
function ct:index-lt-non-numeric() {
range:index-keys-for-field("lessThanTwoString", function($k, $n) { $k }, 10)
};

declare
%test:assertXPath("count($result) eq 2 and contains($result, 'one') and contains($result, 'zerozeroonepointzero')")
function ct:index-lt-numeric() {
range:index-keys-for-field("lessThanTwoNumeric", function($k, $n) { $k }, 10)
};

declare
%test:assertEquals("drei")
function ct:index-gt() {
range:index-keys-for-field("termsAfterB", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-gt() {
collection($ct:COLLECTION)//tei:term[@n > "b"][. = "drei"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-gt-inverse() {
collection($ct:COLLECTION)//tei:term["b" < @n][. = "eins"]
};

declare
%test:assertEquals("drei")
function ct:result-gt() {
collection($ct:COLLECTION)//tei:term[@n > "b"][. = "drei"]/text()
};

declare
%test:assertEquals("eins")
function ct:result-gt-inverse() {
collection($ct:COLLECTION)//tei:term["b" > @n][. = "eins"]/text()
};

declare
%test:assertXPath("count($result) eq 2 and contains($result, 'eins') and contains($result, 'zwei')")
function ct:index-le() {
range:index-keys-for-field("termsBeforeOrEqualB", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-le() {
collection($ct:COLLECTION)//tei:term[@n le "b"][. = "eins"]
};

declare
%test:assertXPath("count($result) eq 2 and contains($result, 'zwei') and contains($result, 'drei')")
function ct:result-le-inverse() {
collection($ct:COLLECTION)//tei:term["b" le @n][true()]
};

declare
%test:assertXPath("count($result) eq 2 and contains($result, 'zwei') and contains($result, 'drei')")
function ct:index-ge() {
range:index-keys-for-field("termsAfterOrEqualB", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-ge() {
collection($ct:COLLECTION)//tei:term[@n ge "b"][. = "drei"]
};


declare
%test:assertXPath("count($result) eq 2 and contains($result, 'eins') and contains($result, 'drei')")
function ct:index-ne() {
range:index-keys-for-field("termsNotB", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-ne() {
collection($ct:COLLECTION)//tei:term[@n ne "b"][. = "drei"]
};

declare
%test:assertEquals("something")
function ct:index-contains() {
range:index-keys-for-field("entryNContains1234", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-contains() {
collection($ct:COLLECTION)//tei:entry[contains(@n, "1234")][. = "something"]
};

declare
%test:assertEquals("something")
function ct:index-matches() {
range:index-keys-for-field("entryNMatches", function($k, $n) { $k }, 10)
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-matches() {
collection($ct:COLLECTION)//tei:entry[matches(@n, "some_\d+_thing")][. = "something"]
};

declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'] and not($result//stats:index[@type eq 'range'])")
function ct:optimize-matches-no-case() {
collection($ct:COLLECTION)//tei:p[matches(@type, "bb")][. = "something"]
};

