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
            <time>14:22:19.329+01:00</time>
            <likes>9</likes>
            <score>6.0</score>
        </letter>
        <letter>
            <from>Rudi</from>
            <to>Egon</to>
            <place>Berlin</place>
            <date>2017-03-13</date>
            <time>15:22:19.329+01:00</time>
            <likes>19</likes>
            <score>8.25</score>
        </letter>
        <letter>
            <from>Susi</from>
            <to>Hans</to>
            <place>Hamburg</place>
            <date>2019-04-01</date>
            <likes>29</likes>
            <score>16.5</score>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Babsi Müller</to>
            <place></place>
            <date>2017-03-11</date>
            <likes>1</likes>
            <score>14.25</score>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Basia Müller</to>
            <place>Wrocław</place>
            <date>2015-06-22</date>
            <likes>5</likes>
            <score>29.50</score>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Basia Kowalska</to>
            <place>Wrocław</place>
            <date>2013-06-22</date>
            <likes>3</likes>
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

declare variable $facet:MODULE :=
    ``[
        xquery version "3.1";
        module namespace idx="http://exist-db.org/lucene/test/";

        declare function idx:place-hierarchy($key as xs:string?) {
            if (exists($key)) then
                doc('/db/lucenetest/places.xml')//place[@name=$key]/ancestor-or-self::place/@name
            else
                ()
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
                    <facet dimension="from" expression="from"/>
                    <facet dimension="to" expression="to"/>
                    <facet dimension="date" expression="tokenize(date, '-')" hierarchical="yes"/>
                    <field name="place" expression="place" analyzer="nodiacritics"/>
                    <field name="from" expression="from" store="no"/>
                    <field name="to" expression="to"/>
                    <field name="date" expression="date" type="xs:date"/>
                    <field name="likes" expression="likes" type="xs:int"/>
                    <field name="score" expression="score" type="xs:double"/>
                    <field name="time" expression="time" type="xs:time"/>
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
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function facet:setup() {
    let $testCol := xmldb:create-collection("/db", "lucenetest")
    let $confCol := xmldb:create-collection("/db/system/config/db", "lucenetest")
    return (
        xmldb:store($testCol, "module.xql", $facet:MODULE, "application/xquery"),
        xmldb:store($confCol, "collection.xconf", $facet:XCONF1),
        xmldb:store($testCol, "places.xml", $facet:TAXONOMY),
        xmldb:store($testCol, "test.xml", $facet:XML),
        xmldb:store($testCol, "documents.xml", $facet:DOCUMENTS),
        xmldb:store($testCol, "multi-lang.xml", $facet:MULTI_LANGUAGE)
    )
};

declare
    %test:tearDown
function facet:tearDown() {
    xmldb:remove("/db/lucenetest"),
    xmldb:remove("/db/system/config/db/lucenetest")
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
    %test:arg("paths", "2017")
    %test:assertEquals('{"03":2}')
    %test:arg("paths", "2019")
    %test:assertEquals('{"03":1,"04":1}')
function facet:hierarchical-facets-retrieve($paths as xs:string*) {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "date", (), $paths)
    return
        serialize($facets, map { "method": "json" })
};

declare
    %test:assertEquals('{"Berlin":2,"Hamburg":1}','{"Wrocław":2}')
function facet:hierarchical-place() {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $facets := ft:facets($result, "location", 10) (: Returns facet counts for "Germany" and "Poland" :)
    for $country in map:keys($facets)
    order by $country
    return
        serialize(
            ft:facets($result, "location", 10, $country), (: Get facet counts for sub-categories :)
            map { "method": "json" }
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
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:heinz", map { "fields": "to" })]
    order by ft:field($letter, "to")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("Basia Kowalska", "Basia Müller", "Babsi Müller")
function facet:query-and-sort-by-date() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:heinz", map { "fields": "date" })]
    order by ft:field($letter, $field, "xs:date")
    return
        $letter/to/text()
};

declare
    %test:assertEquals("Hans", "Rudi")
function facet:query-and-sort-by-date() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "place:berlin", map { "fields": "time" })]
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
    for $letter in collection("/db/lucenetest")//letter[ft:query(., (), map { "fields": $field })]
    let $likes := ft:field($letter, $field, $type)
    order by $likes
    return
        $likes
};

declare
    %test:assertEmpty
function facet:retrieve-non-existant-field() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., (), map { "fields": "foo" })]
    return
        ft:field($letter, "foo")
};

declare
    %test:assertEmpty
function facet:retrieve-not-stored() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., (), map { "fields": "from" })]
    return
        ft:field($letter, "from")
};

declare
    %test:assertEquals("Egon", "Berlin")
function facet:retrieve-multiple-fields() {
    for $letter in collection("/db/lucenetest")//letter[ft:query(., "from:rudi", map { "fields": ("to", "place") })]
    return
        (ft:field($letter, "to"), ft:field($letter, "place"))
};

declare
    %test:assertEquals("2017-03-13", 19, 8.25)
function facet:test-field-type() {
    let $letter := collection("/db/lucenetest")//letter[ft:query(., "from:rudi", map { "fields": ("date", "likes", "score") })]
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