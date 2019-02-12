xquery version "3.1";

module namespace fti="http://exist-db.org/xquery/ft-inline/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $fti:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="p">
                    <inline qname="em"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $fti:DATA :=
    <root>
        <p>trampeltier</p>
        <p><em>trampel</em>tier</p>
        <p><a name="1"></a><em>trampel</em>tier</p>
    </root>;

declare variable $fti:COLLECTION_NAME := "inlinetest";
declare variable $fti:COLLECTION := "/db/" || $fti:COLLECTION_NAME;

declare
%test:setUp
function fti:setup() {
    xmldb:create-collection("/db/system/config/db", $fti:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $fti:COLLECTION_NAME, "collection.xconf", $fti:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $fti:COLLECTION_NAME),
    xmldb:store($fti:COLLECTION, "test.xml", $fti:DATA)
};

declare
%test:tearDown
function fti:cleanup() {
    xmldb:remove($fti:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $fti:COLLECTION_NAME)
};

(:~
 : Check lucene inline configuration. As the <em> element is configured to be inlined.
 : this query should return 3 matching elements.
 :)
declare
%test:args("trampeltier")
%test:assertEquals(3)
function fti:test-inline($query as xs:string) {
count(collection($fti:COLLECTION)//p[ft:query(., $query)])
};