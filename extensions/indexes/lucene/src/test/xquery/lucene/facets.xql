xquery version "3.0";

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
            <date>2019-03-01</date>
        </letter>
        <letter>
            <from>Heinz</from>
            <to>Babsi</to>
            <place></place>
            <date>2017-03-11</date>
        </letter>
    </letters>;

declare variable $facet:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="letter">
                    <facet dimension="place" expression="place"/>
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
    %test:assertEquals(2)
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
    %test:assertEquals('{"2017":2,"2019":2}')
function facet:hierarchical-facets-retrieve() {
    let $result := collection("/db/lucenetest")//letter[ft:query(., ())]
    let $facets := ft:facets(head($result), "date", 10)
    return
        serialize($facets, map { "method": "json" })
};