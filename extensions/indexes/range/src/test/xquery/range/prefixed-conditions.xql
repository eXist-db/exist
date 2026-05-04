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
 : Test prefixed attribute names in range index conditions.
 :
 : @see https://github.com/eXist-db/exist/issues/5189
 :)
module namespace pc="http://exist-db.org/xquery/range/test/prefixed-conditions";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace wwp="http://www.wwp.northeastern.edu/ns/textbase";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $pc:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
               xmlns:tei="http://www.tei-c.org/ns/1.0"
               xmlns:wwp="http://www.wwp.northeastern.edu/ns/textbase">
            <range>
                <!-- Only match tei:title when @wwp:field="title" -->
                <create qname="tei:title">
                    <condition attribute="wwp:field" value="title"/>
                    <field name="wwp-attr" type="xs:string"/>
                </create>
                <!-- Only match tei:title when unprefixed @field="title" -->
                <create qname="tei:title">
                    <condition attribute="field" value="title"/>
                    <field name="ncname-attr" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $pc:DATA :=
    <biblFull xmlns="http://www.tei-c.org/ns/1.0"
              xmlns:wwp="http://www.wwp.northeastern.edu/ns/textbase">
        <fileDesc>
            <titleStmt>
                <title type="main">A Peep at the Pilgrims, 1824</title>
                <title type="display" wwp:field="title" field="title">A Peep at the Pilgrims</title>
            </titleStmt>
            <publicationStmt>
                <publisher>Northeastern University Women Writers Project</publisher>
            </publicationStmt>
            <sourceDesc>
                <bibl>
                    <title wwp:field="title" field="title">A peep at the pilgrims in sixteen hundred thirty-six.</title>
                </bibl>
            </sourceDesc>
        </fileDesc>
    </biblFull>;

declare variable $pc:COLLECTION_NAME := "prefixed-conditions-test";
declare variable $pc:COLLECTION := "/db/" || $pc:COLLECTION_NAME;

declare
%test:setUp
function pc:setup() {
    (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db")),
    xmldb:create-collection("/db/system/config/db", $pc:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $pc:COLLECTION_NAME, "collection.xconf", $pc:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $pc:COLLECTION_NAME),
    xmldb:store($pc:COLLECTION, "data.xml", $pc:DATA)
};

declare
%test:tearDown
function pc:cleanup() {
    xmldb:remove($pc:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $pc:COLLECTION_NAME)
};

(: Prefixed attribute condition should index matching elements :)
declare
%test:assertEquals(2)
function pc:prefixed-attribute-indexed() {
    count(range:index-keys-for-field("wwp-attr", function($k, $n) { $k }, 10))
};

(: Unprefixed attribute condition should also index matching elements :)
declare
%test:assertEquals(2)
function pc:ncname-attribute-indexed() {
    count(range:index-keys-for-field("ncname-attr", function($k, $n) { $k }, 10))
};

(: Prefixed attribute condition should return correct values :)
declare
%test:assertXPath("$result = 'A Peep at the Pilgrims' and $result = 'A peep at the pilgrims in sixteen hundred thirty-six.'")
function pc:prefixed-attribute-values() {
    range:index-keys-for-field("wwp-attr", function($k, $n) { $k }, 10)
};

(: Unprefixed attribute condition should return correct values :)
declare
%test:assertXPath("$result = 'A Peep at the Pilgrims' and $result = 'A peep at the pilgrims in sixteen hundred thirty-six.'")
function pc:ncname-attribute-values() {
    range:index-keys-for-field("ncname-attr", function($k, $n) { $k }, 10)
};

(: Optimizer should rewrite predicate on prefixed attribute :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function pc:optimize-prefixed-attribute() {
    collection($pc:COLLECTION)//tei:title[@wwp:field eq "title"][. = "A Peep at the Pilgrims"]
};

(: Optimizer should rewrite predicate on unprefixed attribute :)
declare
%test:stats
%test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function pc:optimize-ncname-attribute() {
    collection($pc:COLLECTION)//tei:title[@field eq "title"][. = "A Peep at the Pilgrims"]
};
