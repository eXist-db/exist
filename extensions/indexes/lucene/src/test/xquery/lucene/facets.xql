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
        </letter>
        <letter>
            <from>Rudi</from>
            <to>Egon</to>
            <place>Berlin</place>
            <date>2017-03-13</date>
        </letter>
        <letter>
            <from>Susi</from>
            <to>Hans</to>
            <place>Hamburg</place>
            <date>2019-04-01</date>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Babsi</to>
            <place></place>
            <date>2017-03-11</date>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Basia</to>
            <place>Wrocław</place>
            <date>2015-06-22</date>
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

declare variable $facet:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="letter">
                    <facet dimension="place" expression="place"/>
                    <facet dimension="location" expression="let $key := place return doc('/db/lucenetest/places.xml')//place[@name=$key]/ancestor-or-self::place/@name" hierarchical="yes"/>
                    <facet dimension="from" expression="from"/>
                    <facet dimension="to" expression="to"/>
                    <facet dimension="date" expression="tokenize(date, '-')" hierarchical="yes"/>
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
        xmldb:store($confCol, "collection.xconf", $facet:XCONF1),
        xmldb:store($testCol, "places.xml", $facet:TAXONOMY),
        xmldb:store($testCol, "test.xml", $facet:XML)
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
    let $where := ft:facets(head($result), "place", 10)
    let $from := ft:facets(head($result), "from", 10)
    let $to := ft:facets(head($result), "to", 10)
    return (
        $where?Berlin, $where?Hamburg, $from?Susi, $to?Egon
    )
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
    %test:assertEquals(4, 2)
function facet:store-and-remove() {
    let $stored := xmldb:store("/db/lucenetest", "test2.xml", $facet:XML)
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $where := ft:facets(head($result), "place", 10)
    return
        $where?Berlin,
    xmldb:remove("/db/lucenetest", "test2.xml"),
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $where := ft:facets(head($result), "place", 10)
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
    let $facets := ft:facets(head($result), "date", 10, $paths)
    return
        serialize($facets, map { "method": "json" })
};

declare
    %test:assertEquals('{"Berlin":2,"Hamburg":1}','{"Wrocław":1}')
function facet:hierarchical-place() {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $facets := ft:facets(head($result), "location", 10) (: Returns facet counts for "Germany" and "Poland" :)
    for $country in map:keys($facets)
    order by $country
    return
        serialize(
            ft:facets(head($result), "location", 10, $country), (: Get facet counts for sub-categories :)
            map { "method": "json" }
        )
};