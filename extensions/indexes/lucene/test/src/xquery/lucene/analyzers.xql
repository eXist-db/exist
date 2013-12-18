xquery version "3.0";

module namespace analyze="http://exist-db.org/xquery/lucene/test/analyzers";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $analyze:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene diacritics="no">
                <parser class="org.apache.lucene.queryparser.analyzing.AnalyzingQueryParser"/>
                <text qname="p"/>
            </lucene>
        </index>
        <triggers>
            <trigger class="org.exist.extensions.exquery.restxq.impl.RestXqTrigger"/>
        </triggers>
    </collection>;

declare variable $analyze:XCONF2 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene diacritics="yes">
                <text qname="p"/>
            </lucene>
        </index>
        <triggers>
            <trigger class="org.exist.extensions.exquery.restxq.impl.RestXqTrigger"/>
        </triggers>
    </collection>;
    
declare
    %test:setUp
function analyze:setup() {
    let $testCol := xmldb:create-collection("/db", "lucenetest")
    let $testCol1 := xmldb:create-collection("/db/lucenetest", "test1")
    let $testCol2 := xmldb:create-collection("/db/lucenetest", "test2")
    let $confCol := xmldb:create-collection("/db/system/config/db", "lucenetest")
    let $confCol1 := xmldb:create-collection("/db/system/config/db/lucenetest", "test1")
    let $confCol2 := xmldb:create-collection("/db/system/config/db/lucenetest", "test2")
    return (
        xmldb:store($confCol1, "collection.xconf", $analyze:XCONF1),
        xmldb:store($testCol1, "test.xml",
            <test>
                <p>Rüsselsheim</p>
                <p>Russelsheim</p>
                <p>Māori</p>
                <p>Maori</p>
            </test>
        ),
        xmldb:store($confCol2, "collection.xconf", $analyze:XCONF2),
        xmldb:store($testCol2, "test.xml",
            <test>
                <p>Rüsselsheim</p>
                <p>Russelsheim</p>
                <p>Māori</p>
                <p>Maori</p>
            </test>
        )
    )
};

(:declare :)
(:    %test:tearDown:)
(:function analyze:tearDown() {:)
(:    xmldb:remove("/db/lucenetest"),:)
(:    xmldb:remove("/db/system/config/db/lucenetest"):)
(:};:)

declare 
    %test:args("russelsheim")
    %test:assertEquals(2)
    %test:args("rüsselsheim")
    %test:assertEquals(2)
    %test:args("maori")
    %test:assertEquals(2)
    %test:args("Māori")
    %test:assertEquals(2)
function analyze:no-diacrictics($term as xs:string) {
    count(collection("/db/lucenetest/test1")//p[ft:query(., $term)])
};

declare 
    %test:args("russelsheim")
    %test:assertEquals(1)
    %test:args("rüsselsheim")
    %test:assertEquals(1)
    %test:args("maori")
    %test:assertEquals(1)
    %test:args("Māori")
    %test:assertEquals(1)
function analyze:diacrictics($term as xs:string) {
    count(collection("/db/lucenetest/test2")//p[ft:query(., $term)])
};

declare 
    %test:args("rüssels*")
    %test:assertEquals(2)
    %test:args("russels*")
    %test:assertEquals(2)
    %test:args("maor*")
    %test:assertEquals(2)
    %test:args("Māor*")
    %test:assertEquals(2)
function analyze:query-parser($term as xs:string) {
    count(collection("/db/lucenetest/test1")//p[ft:query(., $term)])
};