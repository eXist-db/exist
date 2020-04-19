xquery version "3.1";

module namespace id = "http://exist-db.org/test/securitymanager/id";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace mod1 = "http://module1";
declare namespace sm = "http://exist-db.org/xquery/securitymanager";

declare variable $id:TEST_COLLECTION_NAME := "test-id";
declare variable $id:TEST_COLLECTION_PATH := "/db/test-id";
declare variable $id:TEST_MODULE_NAME := "mod1.xqm";

declare
    %test:setUp
function id:setup() {
    xmldb:create-collection("/db", $id:TEST_COLLECTION_NAME),
    xmldb:store($id:TEST_COLLECTION_PATH, $id:TEST_MODULE_NAME, 'xquery version "3.0";

module namespace mod1 = "http://module1";

declare function mod1:function1() {
    <mod1>{sm:id()}</mod1>
};
    ', "application/xquery")
};

declare
    %test:tearDown
function id:cleanup() {
    xmldb:remove($id:TEST_COLLECTION_PATH)
};

declare
    %test:assertEquals(1)
function id:from-load-module() {
    let $mod1-fn := fn:load-xquery-module("http://module1", map {
        "location-hints": "xmldb:exist://" || $id:TEST_COLLECTION_PATH || "/" || $id:TEST_MODULE_NAME
    })?functions(xs:QName("mod1:function1"))?0
    return
    	fn:count($mod1-fn()//sm:username)
};

declare
    %test:assertEquals(1)
function id:from-inspect-module-functions() {
    let $mod1-fn := inspect:module-functions(xs:anyURI("xmldb:exist://" || $id:TEST_COLLECTION_PATH || "/" || $id:TEST_MODULE_NAME))[1]
    return
        fn:count($mod1-fn()//sm:username)
};
