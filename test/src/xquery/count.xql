xquery version "3.0";

(:~ Additional tests for the fn:count function :)
module namespace count="http://exist-db.org/xquery/test/count";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $count:COLLECTION_NAME1 := "test-count-1";
declare variable $count:COLLECTION_NAME2 := "test-count-2";

declare
    %test:setUp
function count:setup() {
    xmldb:create-collection("/db", $count:COLLECTION_NAME1),
    xmldb:store($count:COLLECTION_NAME1, "test1.xml", <test/>),
    xmldb:create-collection("/db", $count:COLLECTION_NAME2),
    xmldb:store($count:COLLECTION_NAME2, "test2xml", <test/>)
};

declare 
    %test:tearDown
function count:cleanup() {
    xmldb:remove($count:COLLECTION_NAME1),
    xmldb:remove($count:COLLECTION_NAME2)
};

declare 
    %test:assertEquals(1, 1)
function count:arg-self-on-stored() {
    (collection($count:COLLECTION_NAME1)/*, collection($count:COLLECTION_NAME2)/*)/count(.)
};

declare 
    %test:assertEquals(1, 1, 1)
function count:arg-self-on-constructed() {
    (<a/>, <b/>, <c/>)/count(.)
};
