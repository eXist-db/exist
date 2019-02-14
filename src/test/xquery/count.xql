xquery version "3.0";

(:~ Additional tests for the fn:count function :)
module namespace count="http://exist-db.org/xquery/test/count";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $count:TEST_COLLECTION_NAME := "test-count";
declare variable $count:TEST_COLLECTION := "/db/" || $count:TEST_COLLECTION_NAME;
declare variable $count:COLLECTION1_NAME := "test-count-1";
declare variable $count:COLLECTION2_NAME := "test-count-2";
declare variable $count:COLLECTION1 := $count:TEST_COLLECTION || "/" || $count:COLLECTION1_NAME;
declare variable $count:COLLECTION2 := $count:TEST_COLLECTION || "/" || $count:COLLECTION2_NAME;

declare
    %test:setUp
function count:setup() {
    xmldb:create-collection("/db", $count:TEST_COLLECTION_NAME),
    xmldb:create-collection($count:TEST_COLLECTION, $count:COLLECTION1_NAME),
    xmldb:store($count:COLLECTION1, "test1.xml", <test/>),
    xmldb:create-collection($count:TEST_COLLECTION, $count:COLLECTION2_NAME),
    xmldb:store($count:COLLECTION2, "test2xml", <test/>)
};

declare 
    %test:tearDown
function count:cleanup() {
    xmldb:remove($count:TEST_COLLECTION)
};

declare 
    %test:assertEquals(1, 1)
function count:arg-self-on-stored() {
    (collection($count:COLLECTION1)/*, collection($count:COLLECTION2)/*)/count(.)
};

declare 
    %test:assertEquals(1, 1, 1)
function count:arg-self-on-constructed() {
    (<a/>, <b/>, <c/>)/count(.)
};
