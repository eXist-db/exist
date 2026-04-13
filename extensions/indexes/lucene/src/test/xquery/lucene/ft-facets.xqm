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

module namespace facet = "http://exist-db.org/xquery/lucene/test/facets";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $facet:XML :=
    <letters>
        <letter>
            <from>Hans</from>
            <to>Egon</to>
            <place>Berlin</place>
            <date>2019-03-14</date>
            <received>2019-03-14</received>
            <time>14:22:19.329+01:00</time>
            <dateTime>1972-06-08T10:00:00-05:00</dateTime>
            <likes>9</likes>
            <score>6.0</score>
            <subject>art</subject>
            <subject>math</subject>
        </letter>
        <letter>
            <from>Rudi</from>
            <to>Egon</to>
            <place>Berlin</place>
            <date>2017-03-13</date>
            <received>2017-03-20</received>
            <time>15:22:19.329+01:00</time>
            <dateTime>1970-07-03T00:00:00-05:00</dateTime>
            <likes>19</likes>
            <score>8.25</score>
            <subject>history</subject>
        </letter>
        <letter>
            <from>Susi</from>
            <to>Hans</to>
            <place>Hamburg</place>
            <date>2019-04-01</date>
            <received>2019-04-03</received>
            <likes>29</likes>
            <score>16.5</score>
            <subject>engineering</subject>
            <subject>history</subject>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Babsi Müller</to>
            <place></place>
            <date>2017-03-11</date>
            <received>2017-04-01</received>
            <likes>1</likes>
            <score>14.25</score>
            <subject>history</subject>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Basia Müller</to>
            <place>Wrocław</place>
            <date>2015-06-22</date>
            <received>2018-06-24</received>
            <likes>5</likes>
            <score>29.50</score>
            <subject>history</subject>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Basia Kowalska</to>
            <place>Wrocław</place>
            <date>2013-06-22</date>
            <received>2013-08-01</received>
            <likes>3</likes>
            <subject>history</subject>
            <score>16.0</score>
        </letter>
    </letters>;

declare variable $facet:TAXONOMY :=
    <places>
        <place name="Germany">
            <place name="Berlin"/>
            <place name="Hamburg"/>
        </place>
        <place name="Poland">
            <place name="Wrocław"/>
            <place name="Kraków"/>
        </place>
    </places>;

declare variable $facet:SUBJECT :=
    <subject>
        <subject name="science">
            <subject name="math"/>
            <subject name="engineering"/>
        </subject>
        <subject name="humanities">
            <subject name="art"/>
            <subject name="sociology"/>
            <subject name="history"/>
        </subject>
    </subject>;

declare variable $facet:DOCUMENTS :=
    <documents>
        <document id="D-37/2">
            <title>Ruhe im Wald</title>
            <abstract>Es zwitschern die Vögel im Walde</abstract>
            <abstract>Über dem Walde weht ein Wind</abstract>
            <category>nature</category>
        </document>
        <document id="Z-49/2">
            <title>Streiten und Hoffen</title>
            <abstract>Da nun einmal der Himmel zerrissen und die Götter sich streiten</abstract>
            <category>philosophy</category>
        </document>
    </documents>;

declare variable $facet:MULTI_LANGUAGE :=
    <text>
        <body xml:lang="de">
            <div>
                <p>Es zwitschern<note>singen</note> die Vögel</p>
            </div>
        </body>
        <body xml:lang="en">
            <span>
                <p>The sun was shining</p>
            </span>
            <div>
                <p>And the birds are singing</p>
            </div>
        </body>
    </text>;


declare variable $facet:RECORDS := 10;

(: Lucene facet issue consolidation: remaining standalone specs :)

(: Issue #3977: facet counts should not inflate on doc-level reindex :)
declare variable $facet:ISSUE3977_TEST1_FILE := "issue-3977-test1.xml";
declare variable $facet:ISSUE3977_TEST2_FILE := "issue-3977-test2.xml";
declare variable $facet:ISSUE3977_TEST3_FILE := "issue-3977-test3.xml";
declare variable $facet:ISSUE3977_TEST1 as document-node() :=
    document { <root id="1">LuceneTest1</root> };
declare variable $facet:ISSUE3977_TEST2 as document-node() :=
    document { <root id="2"><child/>LuceneTest2</root> };
declare variable $facet:ISSUE3977_TEST3 as document-node() :=
    document { <root><foo>bar</foo></root> };

(: Issue #4190: ft:facets third argument (limit) correctness :)
declare variable $facet:ISSUE4190_DATA_FILE := "issue-4190-data.xml";
declare variable $facet:ISSUE4190_DATA as document-node() :=
    document {
        <items>{
            ( for $i in 1 to 5 return <item publisher="Alpha">text {$i}</item>,
              for $i in 1 to 3 return <item publisher="Beta">text {$i}</item>,
              for $i in 1 to 2 return <item publisher="Gamma">text {$i}</item>,
              for $p in ("Delta", "Epsilon", "Phi", "Rho", "Sigma", "Tau", "Psi", "Omega", "Xi", "Nu", "Mu", "Lambda", "Kappa", "Iota", "Theta", "Eta", "Zeta")
              return <item publisher="{$p}">text</item> )
        }</items>
    };

(: Issue #6012: ft:facets(count=0) should return empty map :)
declare variable $facet:ISSUE6012_DATA_FILE := "issue-6012-data.xml";
declare variable $facet:ISSUE6012_DATA as document-node() :=
    document {
        <items>
            <item publisher="Alpha">text 1</item>
            <item publisher="Beta">text 2</item>
        </items>
    };

(: Issue #4455: facet merging for consecutive ft:query calls :)
declare variable $facet:ISSUE4455_DOCS_FILE := "issue-4455-documents.xml";
declare variable $facet:ISSUE4455_IMGS_FILE := "issue-4455-images.xml";
declare variable $facet:ISSUE4455_DOCUMENTS as element(documents) :=
    <documents>
        <document id="D-37/2">
            <title>Ruhe im Wald</title>
            <abstract>Es zwitschern die Vögel im Walde</abstract>
            <abstract>Über dem Walde weht ein Wind</abstract>
            <category>nature</category>
        </document>
        <document id="D-37/2">
            <title>Birne im Wald</title>
            <abstract>Es zwitschern die Vögel im Walde</abstract>
            <abstract>Über dem Walde weht ein Wind</abstract>
            <category>nature</category>
        </document>
        <document id="Z-49/2">
            <title>Streiten und Hoffen</title>
            <abstract>Da nun einmal der Himmel zerrissen und die Götter sich streiten</abstract>
            <category>philosophy</category>
        </document>
    </documents>;
declare variable $facet:ISSUE4455_IMAGES as element(images) :=
    <images>
        <image>
            <title>Wanderer im Wald</title>
            <category>nature</category>
        </image>
        <image>
            <title>Stilleben mit Birne</title>
            <category>food</category>
        </image>
    </images>;

declare variable $facet:CITIES :=
    <cities>{
        for $id in 1 to $facet:RECORDS
        return
            <city>
                <id>{$id}</id>
                <label>City {$id}</label>
            </city>
    }</cities>;

declare variable $facet:PERSONS :=
    for $id in 1 to $facet:RECORDS
    return
        <person>
            <id>{$id}</id>
            <city-id>{ $facet:RECORDS - $id + 1 }</city-id>
        </person>;

declare variable $facet:MODULE :=
    ``[xquery version "3.1";

module namespace idx="http://exist-db.org/lucene/test/";

declare function idx:place-hierarchy($key as xs:string?) {
    if (exists($key)) then
        doc('/db/lucene-test-facets/places.xml')//place[@name=$key]/ancestor-or-self::place/@name
    else
        ()
};

declare function idx:subject-hierarchy($key as xs:string*) {
    if (exists($key)) then
         array:for-each(array {$key}, function($k) {
             doc('/db/lucene-test-facets/subjects.xml')//subject[@name=$k]/ancestor-or-self::subject/@name
         })
    else
        ()
};

declare function idx:city-id-to-label($id as xs:string) {
    let $city := doc('/db/lucene-test-facets/cities.xml')//city[id eq $id]
    return
        if (exists($city)) then
            $city/label/string()
        else
            "unknown"
};

declare function idx:people-from-city($city-id as xs:string) {
    let $people := collection('/db/lucene-test-facets')//person[city-id eq $city-id]/id/string()
    return
        if (exists($people)) then
            $people
        else
            "none"
};
]``;

declare variable $facet:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer" id="nodiacritics"/>
                <analyzer class="org.apache.lucene.analysis.core.KeywordAnalyzer" id="keyword"/>
                <analyzer class="org.apache.lucene.analysis.de.GermanAnalyzer" id="german"/>
                <analyzer class="org.apache.lucene.analysis.en.EnglishAnalyzer" id="english"/>
                <module uri="http://exist-db.org/lucene/test/" prefix="idx" at="module.xql"/>
                <text qname="letter" analyzer="nodiacritics">
                    <facet dimension="place" expression="place"/>
                    <facet dimension="location" expression="idx:place-hierarchy(place)" hierarchical="yes"/>
                    <facet dimension="subject" expression="idx:subject-hierarchy(subject)" hierarchical="yes"/>
                    <facet dimension="from" expression="from"/>
                    <facet dimension="to" expression="to"/>
                    <facet dimension="date" expression="tokenize(date, '-')" hierarchical="yes"/>
                    <field name="place" expression="place" analyzer="nodiacritics"/>
                    <field name="from" expression="from" store="no"/>
                    <field name="to" expression="to"/>
                    <field name="to-binary" expression="to" binary="true"/>
                    <field name="date" expression="date" type="xs:date"/>
                    <field name="received" expression="received" type="xs:date" binary="true"/>
                    <field name="likes" expression="likes" type="xs:int"/>
                    <field name="likes-binary" expression="likes" type="xs:int" binary="true"/>
                    <field name="score" expression="score" type="xs:double"/>
                    <field name="score-binary" expression="score" type="xs:double" binary="true"/>
                    <field name="time" expression="time" type="xs:time"/>
                    <field name="time-binary" expression="time" type="xs:time" binary="true"/>
                    <field name="date-binary" expression="date" type="xs:date" binary="true"/>
                    <field name="dateTime-binary" expression="dateTime" type="xs:dateTime" binary="true"/>
                </text>
                <text qname="document">
                    <field name="title" expression="title"/>
                    <field name="abstract" expression="abstract" analyzer="german"/>
                    <field name="ident" expression="@id/string()" analyzer="keyword"/>
                    <facet dimension="cat" expression="category"/>
                </text>
                <text qname="root">
                    <facet dimension="testFacet" expression="empty(./*)"/>
                    <facet dimension="test-facet" expression="string(./foo)"/>
                </text>
                <text qname="item">
                    <field name="publisher" expression="string(@publisher)"/>
                    <facet dimension="publisher" expression="string(@publisher)"/>
                </text>
                <text qname="image">
                    <facet dimension="cat" expression="category"/>
                    <field name="title" expression="title"/>
                </text>
                <text match="//document/abstract">
                    <field name="german" analyzer="german"/>
                    <facet dimension="cat" expression="../category"/>
                </text>
                <text match="/text/body/div" index="no">
                    <field name="german" if="ancestor::body[@xml:lang = 'de']" analyzer="german"/>
                    <field name="english" if="ancestor::body[@xml:lang = 'en']" analyzer="english"/>
                    <facet dimension="language" expression="ancestor::body/@xml:lang"/>
                    <ignore qname="note"/>
                </text>
                <text match="/text/body/span" index="no">
                    <field name="german2" if="ancestor::body[@xml:lang = 'de']" analyzer="german"/>
                    <field name="english2" if="ancestor::body[@xml:lang = 'en']" analyzer="english"/>
                    <facet dimension="language" expression="ancestor::body/@xml:lang"/>
                    <ignore qname="note"/>
                </text>
                <text qname="person">
                    <facet dimension="city" expression="idx:city-id-to-label(city-id)"/>
                </text>
                <text qname="city">
                    <facet dimension="person" expression="idx:people-from-city(id)"/>
                </text>
            </lucene>
            <range>
                <create qname="id" type="xs:string"/>
                <create qname="city-id" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $facet:COLLECTION_PATH := "/db/lucene-test-facets";
declare variable $facet:CONFIG_PATH := "/db/system/config/db/lucene-test-facets";

(: Dedicated collections for issue consolidation: avoid polluting the shared lucene-test-facets dataset. :)
declare variable $facet:ISSUE3977_COLLECTION_PATH := "/db/lucene-test-facets-issue-3977";
declare variable $facet:ISSUE3977_CONFIG_PATH := "/db/system/config/db/lucene-test-facets-issue-3977";
declare variable $facet:ISSUE4190_6012_COLLECTION_PATH := "/db/lucene-test-facets-issue-4190-6012";
declare variable $facet:ISSUE4190_6012_CONFIG_PATH := "/db/system/config/db/lucene-test-facets-issue-4190-6012";
declare variable $facet:ISSUE4455_COLLECTION_PATH := "/db/lucene-test-facets-issue-4455";
declare variable $facet:ISSUE4455_CONFIG_PATH := "/db/system/config/db/lucene-test-facets-issue-4455";

declare variable $facet:XCONF_ISSUE3977 as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="root">
                    <facet dimension="testFacet" expression="empty(./*)"/>
                    <facet dimension="test-facet" expression="string(./foo)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $facet:XCONF_ISSUE4190_6012 as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="item">
                    <field name="publisher" expression="string(@publisher)"/>
                    <facet dimension="publisher" expression="string(@publisher)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $facet:XCONF_ISSUE4455 as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <analyzer id="german" class="org.apache.lucene.analysis.de.GermanAnalyzer"/>
                <text qname="document">
                    <field name="title" expression="title"/>
                    <field name="abstract" expression="abstract" analyzer="german"/>
                    <field name="ident" expression="@id/string()" analyzer="keyword"/>
                    <facet dimension="cat" expression="category"/>
                </text>
                <text qname="image">
                    <facet dimension="cat" expression="category"/>
                    <field name="title" expression="title"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function facet:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $testCol := xmldb:create-collection("/db", "lucene-test-facets")
    let $personsCol := xmldb:create-collection($testCol, "persons")
    let $confCol := xmldb:create-collection("/db/system/config/db", "lucene-test-facets")
    let $issue3977Col := xmldb:create-collection("/db", "lucene-test-facets-issue-3977")
    let $issue3977ConfCol := xmldb:create-collection("/db/system/config/db", "lucene-test-facets-issue-3977")
    let $issue4190_6012Col := xmldb:create-collection("/db", "lucene-test-facets-issue-4190-6012")
    let $issue4190_6012ConfCol := xmldb:create-collection("/db/system/config/db", "lucene-test-facets-issue-4190-6012")
    let $issue4455Col := xmldb:create-collection("/db", "lucene-test-facets-issue-4455")
    let $issue4455ConfCol := xmldb:create-collection("/db/system/config/db", "lucene-test-facets-issue-4455")
    return (
        xmldb:store($testCol, "module.xql", $facet:MODULE, "application/xquery"),
        xmldb:store($confCol, "collection.xconf", $facet:XCONF1),
        xmldb:store($testCol, "places.xml", $facet:TAXONOMY),
        xmldb:store($testCol, "subjects.xml", $facet:SUBJECT),
        xmldb:store($testCol, "test.xml", $facet:XML),
        xmldb:store($testCol, "documents.xml", $facet:DOCUMENTS),
        xmldb:store($testCol, "multi-lang.xml", $facet:MULTI_LANGUAGE),
        $facet:PERSONS ! xmldb:store($personsCol, ./id || ".xml", .),
        xmldb:store($testCol, "cities.xml", $facet:CITIES),
        xmldb:reindex($testCol),
        xmldb:reindex($personsCol),

        xmldb:store($issue3977ConfCol, "collection.xconf", $facet:XCONF_ISSUE3977),
        xmldb:store($issue3977Col, $facet:ISSUE3977_TEST1_FILE, $facet:ISSUE3977_TEST1),
        xmldb:store($issue3977Col, $facet:ISSUE3977_TEST2_FILE, $facet:ISSUE3977_TEST2),
        xmldb:store($issue3977Col, $facet:ISSUE3977_TEST3_FILE, $facet:ISSUE3977_TEST3),
        xmldb:reindex($issue3977Col),

        xmldb:store($issue4190_6012ConfCol, "collection.xconf", $facet:XCONF_ISSUE4190_6012),
        xmldb:store($issue4190_6012Col, $facet:ISSUE4190_DATA_FILE, $facet:ISSUE4190_DATA),
        xmldb:store($issue4190_6012Col, $facet:ISSUE6012_DATA_FILE, $facet:ISSUE6012_DATA),
        xmldb:reindex($issue4190_6012Col),

        xmldb:store($issue4455ConfCol, "collection.xconf", $facet:XCONF_ISSUE4455),
        xmldb:store($issue4455Col, $facet:ISSUE4455_DOCS_FILE, $facet:ISSUE4455_DOCUMENTS),
        xmldb:store($issue4455Col, $facet:ISSUE4455_IMGS_FILE, $facet:ISSUE4455_IMAGES),
        xmldb:reindex($issue4455Col)
    )
};

(: Stores the main shared lucene-test-facets dataset without reindexing.
   This is useful for diagnosing whether failures originate at store-time or reindex-time. :)
declare function facet:store() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $testCol := xmldb:create-collection("/db", "lucene-test-facets")
    let $personsCol := xmldb:create-collection($testCol, "persons")
    let $confCol := xmldb:create-collection("/db/system/config/db", "lucene-test-facets")
    return (
        xmldb:store($testCol, "module.xql", $facet:MODULE, "application/xquery"),
        xmldb:store($confCol, "collection.xconf", $facet:XCONF1),
        xmldb:store($testCol, "places.xml", $facet:TAXONOMY),
        xmldb:store($testCol, "subjects.xml", $facet:SUBJECT),
        xmldb:store($testCol, "test.xml", $facet:XML),
        xmldb:store($testCol, "documents.xml", $facet:DOCUMENTS),
        xmldb:store($testCol, "multi-lang.xml", $facet:MULTI_LANGUAGE),
        $facet:PERSONS ! xmldb:store($personsCol, ./id || ".xml", .),
        xmldb:store($testCol, "cities.xml", $facet:CITIES)
    )
};

declare
    %test:tearDown
(:~
 : Shared teardown for both XQSuite module tests and external Java reproducers.
 : Some callers (e.g., store-only setups) do not create all issue-specific collections,
 : so removals are guarded with collection-available checks to keep teardown idempotent.
 :)
function facet:tearDown() {
    if (xmldb:collection-available($facet:COLLECTION_PATH)) then xmldb:remove($facet:COLLECTION_PATH) else (),
    if (xmldb:collection-available($facet:CONFIG_PATH)) then xmldb:remove($facet:CONFIG_PATH) else (),
    if (xmldb:collection-available($facet:ISSUE3977_COLLECTION_PATH)) then xmldb:remove($facet:ISSUE3977_COLLECTION_PATH) else (),
    if (xmldb:collection-available($facet:ISSUE3977_CONFIG_PATH)) then xmldb:remove($facet:ISSUE3977_CONFIG_PATH) else (),
    if (xmldb:collection-available($facet:ISSUE4190_6012_COLLECTION_PATH)) then xmldb:remove($facet:ISSUE4190_6012_COLLECTION_PATH) else (),
    if (xmldb:collection-available($facet:ISSUE4190_6012_CONFIG_PATH)) then xmldb:remove($facet:ISSUE4190_6012_CONFIG_PATH) else (),
    if (xmldb:collection-available($facet:ISSUE4455_COLLECTION_PATH)) then xmldb:remove($facet:ISSUE4455_COLLECTION_PATH) else (),
    if (xmldb:collection-available($facet:ISSUE4455_CONFIG_PATH)) then xmldb:remove($facet:ISSUE4455_CONFIG_PATH) else ()
};

declare
    %private
function facet:map-to-string($map) as xs:string* {
    map:for-each($map, function($k, $v) {
        $k || "=" || $v
    })
};

declare
    %test:assertEquals(2, 1, 1, 2)
function facet:query-all-and-facets() {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $where := ft:facets($result, "place", ())
    let $from := ft:facets($result, "from", ())
    let $to := ft:facets($result, "to", ())
    return (
        $where?Berlin, $where?Hamburg, $from?Susi, $to?Egon
    )
};

declare
    %test:assertEmpty
function facet:query-all-and-non-existing-facet() {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $facet := ft:facets($result, "does-not-exist", ())
    return
        $facet?*
};

declare
    %test:arg("from", "Rudi")
    %test:assertEquals(1)
    %test:arg("from", "Susi")
    %test:assertEquals(0)
    %test:arg("from", "Rudi", "Hans")
    %test:assertEquals(2)
function facet:query-and-drill-down($from as xs:string+) {
    let $options := map {
        "facets": map {
            "from": $from
        }
    }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., "Berlin", $options)])
};

declare
    %test:assertEquals(5)
function facet:query-and-drill-down-hierarchical-sequence() {
    let $options := map {
            "leading-wildcard": "yes",
            "filter-rewrite": "yes",
            "facets": map {
                "subject": ("humanities", "history")
            }
        }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)])
};

declare
    %test:assertEquals(5)
function facet:query-and-drill-down-hierarchical-array-single() {
    let $options := map {
            "leading-wildcard": "yes",
            "filter-rewrite": "yes",
            "facets": map {
                "subject": [("humanities", "history")]
            }
        }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)])
};

declare
    %test:assertEquals(6)
function facet:query-and-drill-down-hierarchical-array-multi() {
    let $options := map {
            "leading-wildcard": "yes",
            "filter-rewrite": "yes",
            "facets": map {
                "subject": [("humanities", "history"), ("humanities", "art")]
            }
        }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)])
};

declare
    %test:assertEquals(2)
function facet:query-and-drill-down-hierarchical-array-multi2() {
    let $options := map {
            "leading-wildcard": "yes",
            "filter-rewrite": "yes",
            "facets": map {
                "subject": [("humanities", "art"), ("science", "engineering")]
            }
        }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)])
};

declare
    %test:assertEquals(5)
function facet:query-and-drill-down-hierarchical-array-multi3() {
    let $options := map {
            "leading-wildcard": "yes",
            "filter-rewrite": "yes",
            "facets": map {
                "subject": [("humanities", "history"), ("science", "engineering")]
            }
        }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)])
};

declare
    %test:assertEquals(1)
function facet:query-and-drill-down-hierarchical-array-multi4() {
    let $options := map {
            "leading-wildcard": "yes",
            "filter-rewrite": "yes",
            "facets": map {
                "subject": [("humanities", "art"), ("science", "math")]
            }
        }
    return
        count(collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)])
};

(:~
 : The facet 'cat' is defined on both: the index on 'document' and 'document/abstract'.
 : Querying 'document' should return 1 as facet count, while 'abstract' should return 2
 : as there are two abstracts being indexed and each has the facet 'nature'.
 :)
declare
    %test:assertEquals(1, 2)
function facet:multiple-indexes-with-same-facet() {
    let $result := doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., ())]
    return
        ft:facets($result, "cat")?nature,
    let $result := doc("/db/lucene-test-facets/documents.xml")//document/abstract[ft:query(., ())]
    return
        ft:facets($result, "cat")?nature
};

declare
    %test:assertEquals(4, 2)
function facet:store-and-remove() {
    let $stored := xmldb:store("/db/lucene-test-facets", "test2.xml", $facet:XML)
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $where := ft:facets($result, "place", 10)
    return
        $where?Berlin,
    xmldb:remove("/db/lucene-test-facets", "test2.xml"),
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $where := ft:facets($result, "place", 10)
    return
        $where?Berlin
};

declare
    %test:arg("paths", "2017")
    %test:assertEquals(2)
    %test:arg("paths", "2019")
    %test:assertEquals(2)
    %test:arg("paths", "2019", "03")
    %test:assertEquals(1)
    %test:arg("paths", "2019", "03", "14")
    %test:assertEquals(1)
    %test:arg("paths", "2019", "03", "08")
    %test:assertEquals(0)
function facet:hierarchical-facets-query($paths as xs:string+) {
    let $options := map {
            "facets": map {
                "date": $paths
            }
        }
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)]
    return
        count($result)
};

declare
    %test:arg("paths", "science")
    %test:assertEquals(2)
    %test:arg("paths", "science", "engineering")
    %test:assertEquals(1)
    %test:arg("paths", "humanities", "history")
    %test:assertEquals(5)
    %test:arg("paths", "humanities", "art")
    %test:assertEquals(1)
    %test:arg("paths", "science", "math")
    %test:assertEquals(1)
function facet:hierarchical-facets-query-subjects($paths as xs:string+) {
    let $options := map {
            "facets": map {
                "subject": $paths
            }
        }
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., (), $options)]
    return
        count($result)
};

declare
    %test:arg("paths", "2017")
    %test:assertEquals("03=2")
    %test:arg("paths", "2019")
    %test:assertEqualsPermutation("03=1", "04=1")
function facet:hierarchical-facets-retrieve-1($paths as xs:string*) {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "date", (), $paths)
    return
        facet:map-to-string($facets)
};

declare
    %test:arg("paths", "science")
    %test:assertEqualsPermutation("math=1", "engineering=1")
function facet:hierarchical-facets-query-and-sort-by-dateretrieve-2($paths as xs:string*) {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "subject", (), $paths)
    return
        facet:map-to-string($facets)
};

declare
    %test:assertEqualsPermutation("Berlin=2", "Hamburg=1", "Wrocław=2")
function facet:hierarchical-place() {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "location", 10) (: Returns facet counts for "Germany" and "Poland" :)
    for $country in map:keys($facets)
    order by $country
    return
        facet:map-to-string(
            ft:facets($result, "location", 10, $country) (: Get facet counts for sub-categories :)
        )
};

declare
    %test:assertEqualsPermutation("history=5", "art=1", "math=1", "engineering=1")
function facet:hierarchical-subject() {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "subject", 10) (: Returns facet counts for "science" and "humanities" :)
    for $topic in map:keys($facets)
    order by $topic
    return
        facet:map-to-string(
            ft:facets($result, "subject", 10, $topic) (: Get facet counts for sub-categories :)
        )
};

declare
    %test:assertEqualsPermutation("history=1", "engineering=1")
function facet:hierarchical-multivalue-subject() {
    let $result := collection("/db/lucene-test-facets")//letter[ft:query(., 'from:susi')]
    let $facets := ft:facets($result, "subject", 10) (: Returns facet counts for "science" and "humanities" :)
    for $topic in map:keys($facets)
    order by $topic
    return
        facet:map-to-string(
            ft:facets($result, "subject", 10, $topic) (: Get facet counts for sub-categories :)
        )
};

declare
    %test:args("place:hamburg")
    %test:assertEquals(1)
    %test:args("place:berlin")
    %test:assertEquals(2)
    %test:args("from:rudi AND place:berlin")
    %test:assertEquals(1)
    %test:args("from:susi AND place:berlin")
    %test:assertEquals(0)
    %test:args("basia AND place:wroclaw")
    %test:assertEquals(2)
    %test:args("place:wroclaw")
    %test:assertEquals(2)
    %test:args('to:(ba* müller)')
    %test:assertEquals(2)
    %test:args('foo:berlin')
    %test:assertEquals(0)
function facet:query-field($query as xs:string) {
    count(collection("/db/lucene-test-facets")//letter[ft:query(., $query)])
};

(:~
 : Single-term field query on place. Place values are indexed with NoDiacriticsStandardAnalyzer;
 : "Wrocław" normalizes to "wroclaw", so place:wroclaw matches 2 letters.
 : New test for Lucene 10; counterpart: facet:query-field.
 :)
declare
    %test:args("place:wroclaw")
    %test:assertEquals(2)
function facet:query-field-place-term($query as xs:string) {
    count(collection("/db/lucene-test-facets")//letter[ft:query(., $query)])
};

(:~
 : Field query with prefix in to field. to:(ba* müller) matches letters to "Basia Müller",
 : "Basia Kowalska", "Babsi Müller" – prefix ba* matches Basia, Babsi.
 : New test for Lucene 10; counterpart: facet:query-field.
 :)
declare
    %test:args('to:(ba* müller)')
    %test:assertEquals(2)
function facet:query-field-to-prefix($query as xs:string) {
    count(collection("/db/lucene-test-facets")//letter[ft:query(., $query)])
};

declare
    %test:assertEquals("Babsi Müller", "Basia Kowalska", "Basia Müller")
function facet:query-and-sort() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "from:heinz")]
    order by ft:field($letter, "to")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("Basia Kowalska", "Basia Müller", "Babsi Müller")
function facet:query-and-sort-by-date() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "from:heinz")]
    order by ft:field($letter, "date", "xs:date")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("Hans", "Rudi")
function facet:query-and-sort-by-time() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "place:berlin")]
    order by ft:field($letter, "time", "xs:time")
    return
        $letter/from/text()
};

(:~ #3042 baseline: document[ft:query(.,...)] + order by ft:field — matched node = result node works. :)
declare
    %test:assertEquals("D-37/2", "Z-49/2")
function facet:query-and-sort-by-ident() {
    for $doc in doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., ())]
    order by ft:field($doc, "ident")
    return
        $doc/@id/string()
};

declare
    %test:args("likes", "xs:int")
    %test:assertEquals(1, 3, 5, 9, 19, 29)
    %test:args("score", "xs:float")
    %test:assertEquals(6, 8.25, 14.25, 16, 16.5, 29.5)
function facet:query-and-sort-by-numeric($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $likes := ft:field($letter, $field, $type)
    order by $likes
    return
        $likes
};

declare
    %test:assertEmpty
function facet:retrieve-non-existant-field() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    return
        ft:field($letter, "foo")
};

declare
    %test:assertEmpty
function facet:retrieve-not-stored() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    return
        ft:field($letter, "from")
};

declare
    %test:assertEquals("Egon", "Berlin")
function facet:retrieve-multiple-fields() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "from:rudi")]
    return
        (ft:field($letter, "to"), ft:field($letter, "place"))
};

declare
    %test:assertEquals("2017-03-13", 19, 8.25)
function facet:test-field-type() {
    let $letter := collection("/db/lucene-test-facets")//letter[ft:query(., "from:rudi")]
    return (
        ft:field($letter, "date", "xs:date"),
        ft:field($letter, "likes", "xs:integer"),
        ft:field($letter, "score", "xs:double")
    )
};

(:~
 : ft:field($node, "field", ()) returns same raw values as ft:field($node, "field").
 : @see https://github.com/eXist-db/exist/issues/4539
 :)
declare
    %test:assertTrue
function facet:field-empty-type-same-as-two-arg() {
    let $letter := collection("/db/lucene-test-facets")//letter[ft:query(., "from:rudi")][1]
    return deep-equal(ft:field($letter, "to", ()), ft:field($letter, "to"))
};

(:~
 : Dynamic type $type := () in ft:field over multiple hits with order by.
 : Re-enabled after getFieldByExistDocId fix for stable docID resolution.
 : @see https://github.com/eXist-db/exist/issues/4539
 :)
declare
    %test:assertEquals("Babsi Müller", "Basia Kowalska", "Basia Müller")
function facet:field-dynamic-empty-type() {
    let $type := ()
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "from:heinz")]
    order by ft:field($letter, "to", $type)
    return ft:field($letter, "to", $type)
};

declare
    %test:assertEquals(4)
function facet:index-keys() {
    count(collection("/db/lucene-test-facets")/ft:index-keys-for-field("from", (), function($key, $count) { $key }, 10))
};

(:~
 : Check if option index="no" is applied properly: no default index, but the fields should be queryable.
 :)
declare
    %test:args("birds")
    %test:assertEmpty
    %test:args("english:birds")
    %test:assertEquals("<p>And the birds are singing</p>")
function facet:query-no-index($query as xs:string) {
    doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., $query)]/p
};

declare
    %test:args("müller")
    %test:assertEquals(2)
function facet:default-analyzer-no-diacritics($query as xs:string) {
    count(collection("/db/lucene-test-facets")//letter[ft:query(., $query)])
};

(:~
 : The configuration defines no default index on 'div', but two fields: 'english' and 'german'.
 : It also configures a facet containing the language. Querying the div with field 'english' set
 : should result in a facet count of 1 for language 'en'.
 :)
declare
    %test:assertEquals(1)
function facet:query-no-default-index-but-facet() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        ft:facets($result, "language")?en
};

declare
    (: German stemming :)
    %test:args("abstract:vogel")
    %test:assertEquals("<title>Ruhe im Wald</title>")
    %test:args("abstract:wald")
    %test:assertEquals("<title>Ruhe im Wald</title>")
    %test:args("abstract:streit")
    %test:assertEquals("<title>Streiten und Hoffen</title>")
    %test:args("title:streiten AND abstract:streit")
    %test:assertEquals("<title>Streiten und Hoffen</title>")
    (: No stemming on title :)
    %test:args("title:streit AND abstract:streit")
    %test:assertEmpty
function facet:query-field-with-analyzer($query as xs:string) {
    doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., $query)]/title
};

(: Index on 'abstract' uses default analyzer but has a field
 : with German analyzer indexing same content.
 :)
declare
    %test:args("german:vogel")
    %test:assertEquals("<title>Ruhe im Wald</title>")
    %test:args("vogel")
    %test:assertEmpty
    %test:args("vögel")
    %test:assertEquals("<title>Ruhe im Wald</title>")
    %test:args("german:streit")
    %test:assertEquals("<title>Streiten und Hoffen</title>")
    %test:args("german:streit AND götter")
    %test:assertEquals("<title>Streiten und Hoffen</title>")
    %test:args("german:streit AND gott")
    %test:assertEmpty
    %test:args("german:(streit AND gott)")
    %test:assertEquals("<title>Streiten und Hoffen</title>")
function facet:query-field-no-expression($query as xs:string) {
    doc("/db/lucene-test-facets/documents.xml")//abstract[ft:query(., $query)]/../title
};

declare
    %test:args('<query><term field="ident">Z-49/2</term></query>')
    %test:assertEquals("<title>Streiten und Hoffen</title>")
function facet:query-field-with-keyword-analyzer($query as element()) {
    doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., $query)]/title
};

declare
    %test:args('german:vogel')
    %test:assertEquals(1)
    %test:args('german:vögel')
    %test:assertEquals(1)
    %test:args('german:birds')
    %test:assertEquals(0)
    %test:args('english:bird')
    %test:assertEquals(1)
    %test:args('english:vögel')
    %test:assertEquals(0)
function facet:query-field-with-condition($query as xs:string) {
    count(doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., $query)])
};

declare
    %test:args('german:singen')
    %test:assertEmpty
function facet:field-respects-ignore($query as xs:string) {
    doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., $query)]
};

declare
    %test:args("vögel")
    %test:assertEquals(2)
    %test:args("walde")
    %test:assertEquals(3)
function facet:query-with-union-and-facets($query as xs:string) {
    let $results := doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., $query)] |
        doc("/db/lucene-test-facets/documents.xml")//document[ft:query(abstract, $query)]
    return
        ft:facets($results, "cat")?nature
};

declare
    %test:assertEmpty
function facet:avoid-range-index-conflict-person() {
    let $people := collection("/db/lucene-test-facets")//person[ft:query(., ())]
    let $city-facet := ft:facets($people, "city")
    return
        if ($people and $city-facet?unknown) then
            $city-facet?unknown || " person values were calculated without id range indexes present"
        else
            ()
};

declare
    %test:assertEmpty
function facet:avoid-range-index-conflict-city() {
    let $cities := collection("/db/lucene-test-facets")//city[ft:query(., ())]
    let $persons-facet := ft:facets($cities, "person")
    return
        if ($cities and $persons-facet?none) then
            $persons-facet?none || " city values were calculated without id range indexes present"
        else
            ()
};

(:~
 : Phrase in abstract field should highlight as single span. abstract:"Götter sich streiten"
 : matches one contiguous phrase; ft:highlight-field-matches should wrap the whole phrase
 : in one exist:match.
 : New test for Lucene 10; counterpart: facet:query-field-expand-matches.
 :)
declare
    %test:args('abstract:"Götter sich streiten"', "abstract")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'>Da nun einmal der Himmel zerrissen und die <exist:match>Götter sich streiten</exist:match></exist:field>")
function facet:query-field-expand-phrase-span($query as xs:string, $field as xs:string) {
    let $result := doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., $query)]
    return
        ft:highlight-field-matches($result, $field)[.//exist:match]
};

(:~
 : Title phrase should highlight as single span. title:"Streiten und Hoffen" matches
 : the full title; each term wrapped separately indicates phrase span merge is needed.
 : New test for Lucene 10; counterpart: facet:query-field-expand-matches.
 :)
declare
    %test:args('title:"Streiten und Hoffen"', "title")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'><exist:match>Streiten und Hoffen</exist:match></exist:field>")
function facet:query-field-expand-title-phrase-span($query as xs:string, $field as xs:string) {
    let $result := doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., $query)]
    return
        ft:highlight-field-matches($result, $field)[.//exist:match]
};

(: FIXME: After Lucene 10 FIELD_INDEX_TYPE fix, some cases return 2 items when 1 expected (e.g. abstract:"Walde weht ein Wind" on docs with multiple abstracts). Review whether expectations need updating. :)
declare
    %test:args("abstract:vogel", "abstract")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'>Es zwitschern die <exist:match>Vögel</exist:match> im Walde</exist:field>")
    %test:args("abstract:(weht AND wind)", "abstract")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'>Über dem Walde <exist:match>weht</exist:match> ein <exist:match>Wind</exist:match></exist:field>")
    %test:args('abstract:"Walde weht ein Wind"', "abstract")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'>Über dem <exist:match>Walde weht ein Wind</exist:match></exist:field>")
    %test:args('abstract:"Götter sich streiten"', "abstract")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'>Da nun einmal der Himmel zerrissen und die <exist:match>Götter sich streiten</exist:match></exist:field>")
    %test:args('title:streiten', "title")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'><exist:match>Streiten</exist:match> und Hoffen</exist:field>")
    %test:args('title:"Streiten und Hoffen"', "title")
    %test:assertEquals("<exist:field xmlns:exist='http://exist.sourceforge.net/NS/exist'><exist:match>Streiten und Hoffen</exist:match></exist:field>")
function facet:query-field-expand-matches($query as xs:string, $field as xs:string) {
    let $result := doc("/db/lucene-test-facets/documents.xml")//document[ft:query(., $query)]
    return
        ft:highlight-field-matches($result, $field)[.//exist:match]
};

declare
    %test:assertEquals("Basia Kowalska", "Babsi Müller", "Basia Müller")
function facet:query-and-sort-by-binary-date() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "from:heinz")]
    order by ft:binary-field($letter, "received", "xs:date")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("2017", "20")
function facet:retrieve-binary-date-field() {
    let $letter := collection("/db/lucene-test-facets")//letter[ft:query(., "from:rudi")]
    let $date := ft:binary-field($letter, "received", "xs:date")
    return (
        year-from-date($date),
        day-from-date($date)
    )
};

declare
    %test:assertEquals("Babsi Müller", "Basia Kowalska", "Basia Müller")
function facet:query-and-sort-by-binary-string() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "from:heinz")]
    order by ft:binary-field($letter, "to-binary", "xs:string") empty least
    return
        $letter/to/text()
};

declare
    %test:args("likes-binary", "xs:int")
    %test:assertEquals(1, 3, 5, 9, 19, 29)
    %test:args("score-binary", "xs:double")
    %test:assertEquals(6, 8.25, 14.25, 16, 16.5, 29.5)
function facet:query-and-sort-by-binary-numeric($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $field-value := ft:binary-field($letter, $field, $type)
    order by $field-value
    return $field-value
};

declare
    %test:args("time-binary", "xs:time")
    %test:assertEquals("14:22:19.329+01:00", "15:22:19.329+01:00")
    %test:args("date-binary", "xs:date")
    %test:assertEquals("2013-06-22", "2015-06-22", "2017-03-11", "2017-03-13", "2019-03-14", "2019-04-01")
function facet:query-and-sort-by-binary-dates-and-times($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $field-value := ft:binary-field($letter, $field, $type)
    order by $field-value
    return $field-value
};

declare
    %test:args("dateTime-binary", "xs:dateTime")
    %test:assertEquals("1970-07-03T00:00:00-05:00", "1972-06-08T10:00:00-05:00")
function facet:query-and-sort-by-binary-dateTime($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., ())]
    let $field-value := ft:binary-field($letter, $field, $type)
    order by $field-value
    return $field-value
};

declare
    %test:assertEquals("Hans", "Rudi")
function facet:query-and-sort-by-binary-time() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "to:Egon")]
    order by ft:binary-field($letter, "time-binary", "xs:time")
    return
        $letter/from/text()
};

declare
    %test:assertEquals("Rudi", "Hans")
function facet:query-and-sort-by-binary-dateTime() {
    for $letter in collection("/db/lucene-test-facets")//letter[ft:query(., "place:berlin")]
    order by ft:binary-field($letter, "dateTime-binary", "xs:dateTime")
    return
        $letter/from/text()
};

declare
    %test:assertEquals(1)
function facet:query-no-default-index-count() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        count($result)
};

declare
    %test:assertEquals(1)
function facet:query-no-default-index-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        ft:facets($result, "language")?en
};

declare
    %test:assertEquals("1 1")
function facet:query-no-default-index-count-and-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//div[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        count($result) || " " || ft:facets($result, "language")?en
};

declare
    %test:assertEquals(1)
function facet:query-no-default-index-bracketed-element-count() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div)[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        count($result)
};

declare
    %test:assertEquals(1)
function facet:query-no-default-index-bracketed-element-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div)[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        ft:facets($result, "language")?en
};

declare
    %test:assertEquals("1 1")
function facet:query-no-default-index-bracketed-element-count-and-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div)[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        count($result) || " " || ft:facets($result, "language")?en
};

declare
    %test:assertEquals(1)
function facet:query-no-default-index-bracketed-element-one-nonexistent-element-count() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div|other)[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        count($result)
};

declare
    %test:assertEquals(1)
function facet:query-no-default-index-bracketed-element-one-nonexistent-element-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div|other)[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        ft:facets($result, "language")?en
};

declare
    %test:assertEquals("1 1")
function facet:query-no-default-index-bracketed-element-one-nonexistent-element-count-and-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div|other)[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
    return
        count($result) || " " || ft:facets($result, "language")?en
};


declare
    %test:assertEquals(2)
function facet:query-no-default-index-bracketed-two-elements-count() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div|span)[ft:query(., "english:* OR english2:*", map { "leading-wildcard": "yes" })]
    return
        count($result)
};

declare
    %test:assertEquals(2)
function facet:query-no-default-index-bracketed-two-elements-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div|span)[ft:query(., "english:* OR english2:*", map { "leading-wildcard": "yes" })]
    return
        ft:facets($result, "language")?en
};

declare
    %test:assertEquals("2 2")
function facet:query-no-default-index-bracketed-two-elements-count-and-facets() {
    let $result := doc("/db/lucene-test-facets/multi-lang.xml")//(div|span)[ft:query(., "english:* OR english2:*", map { "leading-wildcard": "yes" })]
    return
        count($result) || " " || ft:facets($result, "language")?en
};

(: Issue consolidation: remaining facet regression specs :)

(: #3977: reindex(collection, doc-uri) must update facets, not add :)
declare
    %test:assertEquals(1, 2)
function facet:issue3977-reindex-doc-updates-not-adds() {
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH)
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH, $facet:ISSUE3977_TEST1_FILE)
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH, $facet:ISSUE3977_TEST1_FILE)
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH, $facet:ISSUE3977_TEST2_FILE)
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH, $facet:ISSUE3977_TEST2_FILE)
    let $options := map { "facets": map { "testFacet": () } }
    let $results := collection($facet:ISSUE3977_COLLECTION_PATH)//root[ft:query(., (), $options)]
    let $testFacet := ft:facets($results, "testFacet", ())
    return (
        $testFacet?true,
        $testFacet?false
    )
};

declare
    %test:assertEquals(1, 1, 1, 1, 1)
function facet:issue3977-facets-after-reindex-arity-2-joewiz() {
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH)
    for $i in (1 to 5)
    let $hits := collection($facet:ISSUE3977_COLLECTION_PATH)//root[ft:query(., ())]
    let $facets := ft:facets($hits, "test-facet", ())
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH, $facet:ISSUE3977_TEST3_FILE)
    return $facets?bar
};

declare
    %test:assertEquals(1, 1, 1, 1, 1)
function facet:issue3977-facets-after-reindex-arity-1-joewiz() {
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH)
    for $i in (1 to 5)
    let $hits := collection($facet:ISSUE3977_COLLECTION_PATH)//root[ft:query(., ())]
    let $facets := ft:facets($hits, "test-facet", ())
    let $_ := xmldb:reindex($facet:ISSUE3977_COLLECTION_PATH)
    return $facets?bar
};

(: #4190: ft:facets(..., limit) must be correct for variable-provided hits :)
declare
    %test:assertEquals(20)
function facet:issue4190-facets-no-limit() {
    let $hits := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher")))
};

declare
    %test:assertEquals(10)
function facet:issue4190-facets-limit-10-via-variable() {
    let $articles := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item
    let $hits := $articles[ft:query(., (), map { "fields": "publisher" })]
    return count(map:keys(ft:facets($hits, "publisher", 10)))
};

declare
    %test:assertEquals(1)
function facet:issue4190-facets-limit-1-via-variable() {
    let $articles := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item
    let $hits := $articles[ft:query(., (), map { "fields": "publisher" })]
    return count(map:keys(ft:facets($hits, "publisher", 1)))
};

declare
    %test:assertEquals(10)
function facet:issue4190-facets-limit-10-direct-path() {
    let $hits := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher", 10)))
};

declare
    %test:assertEquals(5)
function facet:issue4190-facets-limit-5-direct-path() {
    let $hits := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher", 5)))
};

declare
    %test:assertTrue
function facet:issue4190-facets-limit-1-content-via-variable() {
    let $articles := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item
    let $hits := $articles[ft:query(., (), map { "fields": "publisher" })]
    let $facets := ft:facets($hits, "publisher", 1)
    return count(map:keys($facets)) eq 1 and $facets?Alpha eq 5
};

declare
    %test:assertTrue
function facet:issue4190-facets-limit-3-content-direct-path() {
    let $hits := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE4190_DATA_FILE)//item[ft:query(., ())]
    let $facets := ft:facets($hits, "publisher", 3)
    return count(map:keys($facets)) eq 3
        and $facets?Alpha eq 5 and $facets?Beta eq 3 and $facets?Gamma eq 2
};

(: #6012: ft:facets(..., 0) must return an empty map (not empty entries / not error) :)
declare
    %test:assertEquals(0)
function facet:issue6012-facets-zero-limit-returns-empty() {
    let $hits := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE6012_DATA_FILE)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher", 0)))
};

declare
    %test:assertTrue
function facet:issue6012-facets-zero-limit-returns-map() {
    let $hits := doc($facet:ISSUE4190_6012_COLLECTION_PATH || "/" || $facet:ISSUE6012_DATA_FILE)//item[ft:query(., ())]
    let $result := ft:facets($hits, "publisher", 0)
    return $result instance of map(*)
};

(: #4455: facet merging must not inflate counts for chained ft:query on same nodes :)
declare
    %test:pending("GitHub #4455 – facet merge for chained ft:query not yet implemented")
    %test:assertEquals(1)
function facet:issue4455-facets-same-nodes-chained-predicates() {
    let $results :=
        doc($facet:ISSUE4455_COLLECTION_PATH || "/" || $facet:ISSUE4455_DOCS_FILE)//document
        [ft:query(., "vögel")][ft:query(., "title:Ruhe")]
    return ft:facets($results, "cat")?nature
};

declare
    %test:assertEquals(3)
function facet:issue4455-facets-different-nodes-union() {
    let $results :=
        doc($facet:ISSUE4455_COLLECTION_PATH || "/" || $facet:ISSUE4455_DOCS_FILE)//document[ft:query(., "wald")]
        | doc($facet:ISSUE4455_COLLECTION_PATH || "/" || $facet:ISSUE4455_IMGS_FILE)//image[ft:query(., "wald")]
    return ft:facets($results, "cat")?nature
};
