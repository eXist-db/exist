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

module namespace facet="http://exist-db.org/xquery/lucene/test/facets";

declare namespace test="http://exist-db.org/xquery/xqsuite";

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
            <div>
                <p>And the birds are singing</p>
            </div>
        </body>
    </text>;


declare variable $facet:RECORDS := 10;

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
        doc('/db/lucenetest/places.xml')//place[@name=$key]/ancestor-or-self::place/@name
    else
        ()
};

declare function idx:subject-hierarchy($key as xs:string*) {
    if (exists($key)) then
         array:for-each(array {$key}, function($k) {
             doc('/db/lucenetest/subjects.xml')//subject[@name=$k]/ancestor-or-self::subject/@name
         })
    else
        ()
};

declare function idx:city-id-to-label($id as xs:string) {
    let $city := doc('/db/lucenetest/cities.xml')//city[id eq $id]
    return
        if (exists($city)) then
            $city/label/string()
        else
            "unknown"
};

declare function idx:people-from-city($city-id as xs:string) {
    let $people := collection('/db/lucenetest')//person[city-id eq $city-id]/id/string()
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

declare
    %test:setUp
function facet:setup() {
    let $testCol := xmldb:create-collection("/db", "lucenetest")
    let $personsCol := xmldb:create-collection("/db/lucenetest", "persons")
    let $confCol := xmldb:create-collection("/db/system/config/db", "lucenetest")
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
        xmldb:reindex($personsCol)
    )
};

declare
    %test:tearDown
function facet:tearDown() {
    xmldb:remove("/db/lucenetest"),
    xmldb:remove("/db/system/config/db/lucenetest")
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
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
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
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
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
        count(collection("/db/lucenetest")//letter[ft:query(., "Berlin", $options)])
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
        count(collection("/db/lucenetest")//letter[ft:query(., (), $options)])
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
        count(collection("/db/lucenetest")//letter[ft:query(., (), $options)])
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
        count(collection("/db/lucenetest")//letter[ft:query(., (), $options)])
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
        count(collection("/db/lucenetest")//letter[ft:query(., (), $options)])
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
        count(collection("/db/lucenetest")//letter[ft:query(., (), $options)])
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
        count(collection("/db/lucenetest")//letter[ft:query(., (), $options)])
};

(:~
 : The facet 'cat' is defined on both: the index on 'document' and 'document/abstract'.
 : Querying 'document' should return 1 as facet count, while 'abstract' should return 2
 : as there are two abstracts being indexed and each has the facet 'nature'.
 :)
declare
    %test:assertEquals(1, 2)
function facet:multiple-indexes-with-same-facet() {
    let $result := doc("/db/lucenetest/documents.xml")//document[ft:query(., ())]
    return
        ft:facets($result, "cat")?nature,
    let $result := doc("/db/lucenetest/documents.xml")//document/abstract[ft:query(., ())]
    return
        ft:facets($result, "cat")?nature
};

declare
    %test:assertEquals(4, 2)
function facet:store-and-remove() {
    let $stored := xmldb:store("/db/lucenetest", "test2.xml", $facet:XML)
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $where := ft:facets($result, "place", 10)
    return
        $where?Berlin,
    xmldb:remove("/db/lucenetest", "test2.xml"),
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
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
    let $result := collection("/db/lucenetest")//letter[ft:query(., (), $options)]
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
    let $result := collection("/db/lucenetest")//letter[ft:query(., (), $options)]
    return
        count($result)
};

declare
    %test:arg("paths", "2017")
    %test:assertEquals("03=2")
    %test:arg("paths", "2019")
    %test:assertEqualsPermutation("03=1", "04=1")
function facet:hierarchical-facets-retrieve-1($paths as xs:string*) {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "date", (), $paths)
    return
        facet:map-to-string($facets)
};

declare
    %test:arg("paths", "science")
    %test:assertEqualsPermutation("math=1", "engineering=1")
function facet:hierarchical-facets-query-and-sort-by-dateretrieve-2($paths as xs:string*) {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "subject", (), $paths)
    return
        facet:map-to-string($facets)
};

declare
    %test:assertEqualsPermutation("Berlin=2", "Hamburg=1", "Wrocław=2")
function facet:hierarchical-place() {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
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
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
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
    let $result := collection("/db/lucenetest")//letter[ft:query(., 'from:susi')]
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
    count(collection("/db/lucenetest")//letter[ft:query(., $query)])
};

declare
    %test:assertEquals("Babsi Müller", "Basia Kowalska", "Basia Müller")
function facet:query-and-sort() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:heinz")]
    order by ft:field($letter, "to")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("Basia Kowalska", "Basia Müller", "Babsi Müller")
function facet:query-and-sort-by-date() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:heinz")]
    order by ft:field($letter, "date", "xs:date")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("Hans", "Rudi")
function facet:query-and-sort-by-time() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "place:berlin")]
    order by ft:field($letter, "time", "xs:time")
    return
        $letter/from/text()
};

declare
    %test:args("likes", "xs:int")
    %test:assertEquals(1, 3, 5, 9, 19, 29)
    %test:args("score", "xs:float")
    %test:assertEquals(6, 8.25, 14.25, 16, 16.5, 29.5)
function facet:query-and-sort-by-numeric($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., ())]
    let $likes := ft:field($letter, $field, $type)
    order by $likes
    return
        $likes
};

declare
    %test:assertEmpty
function facet:retrieve-non-existant-field() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., ())]
    return
        ft:field($letter, "foo")
};

declare
    %test:assertEmpty
function facet:retrieve-not-stored() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., ())]
    return
        ft:field($letter, "from")
};

declare
    %test:assertEquals("Egon", "Berlin")
function facet:retrieve-multiple-fields() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:rudi")]
    return
        (ft:field($letter, "to"), ft:field($letter, "place"))
};

declare
    %test:assertEquals("2017-03-13", 19, 8.25)
function facet:test-field-type() {
    let $letter := collection("/db/lucenetest")//letter[ft:query(., "from:rudi")]
    return (
        ft:field($letter, "date", "xs:date"),
        ft:field($letter, "likes", "xs:integer"),
        ft:field($letter, "score", "xs:double")
    )
};

declare
    %test:assertEquals(4)
function facet:index-keys() {
    count(collection("/db/lucenetest")/ft:index-keys-for-field("from", (), function($key, $count) { $key }, 10))
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
    doc("/db/lucenetest/multi-lang.xml")//div[ft:query(., $query)]/p
};

declare
    %test:args("müller")
    %test:assertEquals(2)
function facet:default-analyzer-no-diacritics($query as xs:string) {
    count(collection("/db/lucenetest")//letter[ft:query(., $query)])
};

(:~
 : The configuration defines no default index on 'div', but two fields: 'english' and 'german'.
 : It also configures a facet containing the language. Querying the div with field 'english' set
 : should result in a facet count of 1 for language 'en'.
 :)
declare
    %test:assertEquals(1)
function facet:query-no-default-index-but-facet() {
    let $result := doc("/db/lucenetest/multi-lang.xml")//div[ft:query(., "english:*", map { "leading-wildcard": "yes" })]
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
    doc("/db/lucenetest/documents.xml")//document[ft:query(., $query)]/title
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
    doc("/db/lucenetest/documents.xml")//abstract[ft:query(., $query)]/../title
};

declare
    %test:args('<query><term field="ident">Z-49/2</term></query>')
    %test:assertEquals("<title>Streiten und Hoffen</title>")
function facet:query-field-with-keyword-analyzer($query as element()) {
    doc("/db/lucenetest/documents.xml")//document[ft:query(., $query)]/title
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
    count(doc("/db/lucenetest/multi-lang.xml")//div[ft:query(., $query)])
};

declare
    %test:args('german:singen')
    %test:assertEmpty
function facet:field-respects-ignore($query as xs:string) {
    doc("/db/lucenetest/multi-lang.xml")//div[ft:query(., $query)]
};

declare
    %test:args("vögel")
    %test:assertEquals(2)
    %test:args("walde")
    %test:assertEquals(3)
function facet:query-with-union-and-facets($query as xs:string) {
    let $results := doc("/db/lucenetest/documents.xml")//document[ft:query(., $query)] |
        doc("/db/lucenetest/documents.xml")//document[ft:query(abstract, $query)]
    return
        ft:facets($results, "cat")?nature
};

declare
    %test:assertEmpty
function facet:avoid-range-index-conflict-person() {
    let $people := collection("/db/lucenetest")//person[ft:query(., ())]
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
    let $cities := collection("/db/lucenetest")//city[ft:query(., ())]
    let $persons-facet := ft:facets($cities, "person")
    return
        if ($cities and $persons-facet?none) then
            $persons-facet?none || " city values were calculated without id range indexes present"
        else
            ()
};

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
    let $result := doc("/db/lucenetest/documents.xml")//document[ft:query(., $query)]
    return
        ft:highlight-field-matches($result, $field)[.//exist:match]
};

declare
    %test:assertEquals("Basia Kowalska", "Babsi Müller", "Basia Müller")
function facet:query-and-sort-by-binary-date() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:heinz")]
    order by ft:binary-field($letter, "received", "xs:date")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("2017", "20")
function facet:retrieve-binary-date-field() {
    let $letter := collection("/db/lucenetest")//letter[ft:query(., "from:rudi")]
    let $date := ft:binary-field($letter, "received", "xs:date")
    return (
        year-from-date($date),
        day-from-date($date)
    )
};

declare
    %test:assertEquals("Babsi Müller", "Basia Kowalska", "Basia Müller")
function facet:query-and-sort-by-binary-string() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:heinz")]
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
    for $letter in collection("/db/lucenetest")//letter[ft:query(., ())]
    let $field-value := ft:binary-field($letter, $field, $type)
    order by $field-value
    return $field-value
};

declare
    %test:args("time-binary", "xs:time")
    %test:assertEquals("13:22:19.329+01:00", "14:22:19.329+01:00")
    %test:args("date-binary", "xs:date")
    %test:assertEquals("2013-06-22", "2015-06-22", "2017-03-11", "2017-03-13", "2019-03-14", "2019-04-01")
function facet:query-and-sort-by-binary-dates-and-times($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., ())]
    let $field-value := ft:binary-field($letter, $field, $type)
    order by $field-value
    return $field-value
};

declare
    %test:args("dateTime-binary", "xs:dateTime")
    %test:assertEquals("1970-07-03T05:00:00", "1972-06-08T15:00:00")
function facet:query-and-sort-by-binary-dateTime($field as xs:string, $type as xs:string) {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., ())]
    let $field-value := ft:binary-field($letter, $field, $type)
    order by $field-value
    return $field-value
};

declare
    %test:assertEquals("Hans", "Rudi")
function facet:query-and-sort-by-binary-time() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "to:Egon")]
    order by ft:binary-field($letter, "time-binary", "xs:time")
    return
        $letter/from/text()
};

declare
    %test:assertEquals("Rudi", "Hans")
function facet:query-and-sort-by-binary-dateTime() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "place:berlin")]
    order by ft:binary-field($letter, "dateTime-binary", "xs:dateTime")
    return
        $letter/from/text()
};