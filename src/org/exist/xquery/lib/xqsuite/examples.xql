xquery version "3.0";

module namespace t="http://exist-db.org/xquery/test/examples";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "test.xql";

declare variable $t:TEST_COLLECTION := "xqunit";

declare variable $t:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:mods="http://www.loc.gov/mods/v3">
            <lucene>
                <text qname="SPEECH"/>
            </lucene>
        </index>
    </collection>;
    
declare %test:setUp function t:setUp() {
    let $configColl := test:mkcol("/db/system/config/db/", $t:TEST_COLLECTION)
    let $collection := xmldb:create-collection("/db", $t:TEST_COLLECTION)
    return (
        xmldb:store("/db/system/config/db/" || $t:TEST_COLLECTION, "collection.xconf", $t:XCONF),
        xmldb:store-files-from-pattern("/db/" || $t:TEST_COLLECTION, "samples/shakespeare", "*.xml")
    )
};

declare %test:tearDown function t:shutdown() {
    xmldb:remove("/db/" || $t:TEST_COLLECTION)
};

declare 
    %test:assertEquals(1)
function t:lucene-query() {
    count(collection("/db/" || $t:TEST_COLLECTION)//SPEECH[ft:query(., "+fenny +snake")])
};

declare %test:assertEquals("<SPEAKER>MACBETH</SPEAKER><SPEAKER>Second Witch</SPEAKER>")
function t:speakers() {
    collection("/db/" || $t:TEST_COLLECTION)//SPEECH[ft:query(., "fenny snake")]/SPEAKER
};

declare %test:assertXPath("$result[. = 'MACBETH']")
function t:speakers2() {
    collection("/db/" || $t:TEST_COLLECTION)//SPEECH[ft:query(., "fenny snake")]/SPEAKER
};

declare %test:assertExists %test:assertEquals(500)
function t:integer() {
    500
};

declare %test:assertEmpty function t:empty() {
    ()
};

declare %test:assertEmpty function t:empty2() {
    2
};

declare %test:assertXPath("/name[. = 'Item2']")
function t:xpath() {
    <item>
        <name>Item1</name>
    </item>
};