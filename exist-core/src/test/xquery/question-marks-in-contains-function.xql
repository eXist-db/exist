xquery version "3.1";

(:~
 : these tests were created because of
 : https://github.com/eXist-db/exist/issues/1379
~:)

module namespace ct="http://exist-db.org/xquery/test/question-marks-in-contains-function";

declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $ct:test :=
    <root>
        <persName xmlns="http://www.tei-c.org/ns/1.0">
            <surname>Hering</surname>, <forename>S(amuel?)</forename>
        </persName>
        <persName xmlns="http://www.tei-c.org/ns/1.0">
            <surname>Hering</surname>, <forename>Samuel</forename>
        </persName>
    </root>;

declare variable $ct:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0"> 
        <index xmlns:tei="http://www.tei-c.org/ns/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range> 
                <create qname="tei:surname" type="xs:string"/> 
                <create qname="tei:forename" type="xs:string"/> 
                <create qname="tei:persName" type="xs:string"/> 
            </range> 
        </index> 
    </collection>;

declare
        %test:setUp
function ct:setup() {
    xmldb:create-collection("/db/system/config/db", "test"),
    xmldb:store("/db/system/config/db/test", "collection.xconf", $ct:xconf),
    xmldb:create-collection("/db", "test"),
    xmldb:store("/db/test", "test.xml", $ct:test)
};

declare
        %test:tearDown
function ct:teardown() {
    xmldb:remove("/db/test"),
    xmldb:remove("/db/system/config/db/test")
};

declare
        %test:pending("https://github.com/eXist-db/exist/issues/1379")
        %test:assertEquals(1)
function ct:element-contains-question-mark() {
    let $hits := doc("/db/test/test.xml")//tei:forename[fn:contains(., "?")]
    return
        count($hits)
};

declare
        %test:pending("https://github.com/eXist-db/exist/issues/1379")
        %test:assertEquals(0)
function ct:element-contains-escaped-question-mark() {
    let $hits := doc("/db/test/test.xml")//tei:forename[fn:contains(., "\?")]
    return
        count($hits)
};

declare
        %test:pending("https://github.com/eXist-db/exist/issues/1379")
        %test:assertEquals(1)
function ct:parent-element-contains-question-mark() {
    let $hits := doc("/db/test/test.xml")//tei:persName[fn:contains(., "?")]
    return
        count($hits)
};

declare
        %test:pending("https://github.com/eXist-db/exist/issues/1379")
        %test:assertEquals(0)
function ct:parent-element-contains-escaped-question-mark() {
    let $hits := doc("/db/test/test.xml")//tei:persName[fn:contains(., "\?")]
    return
        count($hits)
};

