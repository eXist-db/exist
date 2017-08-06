xquery version "3.1";

module namespace ftt="http://exist-db.org/xquery/ft-match/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ftt:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="div">
                    <ignore qname="div"/>
                    <ignore qname="hi"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $ftt:DATA :=
    <body>
        <div>
            <p>Introduction text</p>
            <div>
                <p>text in nested div and more <hi>text</hi>.</p>
            </div>
        </div>
    </body>;
    
declare variable $ftt:COLLECTION_NAME := "matchestest";
declare variable $ftt:COLLECTION := "/db/" || $ftt:COLLECTION_NAME;

declare
    %test:setUp
function ftt:setup() {
    xmldb:create-collection("/db/system/config/db", $ftt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ftt:COLLECTION_NAME, "collection.xconf", $ftt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ftt:COLLECTION_NAME),
    xmldb:store($ftt:COLLECTION, "test.xml", $ftt:DATA)
};

declare
    %test:tearDown
function ftt:cleanup() {
    xmldb:remove($ftt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ftt:COLLECTION_NAME)
};

(:~
 : Check match highlighting: because the inner div is set to "ignore" in the Lucene index,
 : the matching string "text" should not be highlighted.
 : 
 : It should be highlighted though if we look at the second result, which is the inner div.
 : The nested <hi> should never be highlighted.
 :)
declare
    %test:args("text")
    %test:assertEquals(1, 1)
function ftt:highlight($query as xs:string) {
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)][1])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)][2])//exist:match)
};