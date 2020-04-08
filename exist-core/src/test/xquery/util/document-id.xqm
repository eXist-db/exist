xquery version "3.1";

module namespace docid = "http://exist-db.org/test/util/document-id";

import module namespace util = "http://exist-db.org/xquery/util";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:setUp
function docid:setup() {
    xmldb:store("/db", "docid.xml", <doc>/db</doc>),
    xmldb:create-collection("/db", "test-docid"),
    xmldb:store("/db/test-docid", "docid.xml", <doc>/db/test-docid</doc>)
};

declare
    %test:tearDown
function docid:teardown() {
    xmldb:remove("/db/test-docid"),
    xmldb:remove("/db", "docid.xml")
};

declare
    %test:assertEquals('<doc>/db</doc>')
function docid:by-id-root() {
    let $doc := doc("/db/docid.xml")
    let $id := util:absolute-resource-id($doc)
    return
    	util:get-resource-by-absolute-id($id)
};

declare
    %test:assertEquals('<doc>/db/test-docid</doc>')
function docid:by-id() {
    let $doc := doc("/db/test-docid/docid.xml")
    let $id := util:absolute-resource-id($doc)
    return
    	util:get-resource-by-absolute-id($id)
};
