xquery version "3.0";

(:~ Additional tests for the fn:parse-xml and fn:parse-xml-fragment functions :)
module namespace px="http://exist-db.org/xquery/test/parse-xml";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertTrue
function px:fragment-type() {
    fn:parse-xml-fragment("He was <i>so</i> kind") instance of document-node()
};


declare
    %test:assertEquals(3)
function px:fragment-count() {
    count(parse-xml-fragment("He was <i>so</i> kind")/node())
};
