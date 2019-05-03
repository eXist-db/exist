xquery version "3.0";

module namespace collations = "http://exist-db.org/xquery/test/collations";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare variable $collations:TEST-DOC-1 := document {
    <entry>
        <a>xxx</a>
        <b/>
    </entry>
};

declare
    %test:setUp
function collations:setup() {
    xmldb:create-collection("/db", "collations-test"),
    xmldb:store("/db/collations-test", "test.xml", $collations:TEST-DOC-1)
};

declare
    %test:tearDown
function collations:cleanup() {
    xmldb:remove("/db/collations-test")
};

declare
    %test:assertEquals("<a>xxx</a>")
function collations:non-empty-string() {
    doc("/db/collations-test/test.xml")//a[contains(.,'x',"?lang=en-US")]
};

declare
    %test:assertEmpty
function collations:empty-string() {
    doc("/db/collations-test/test.xml")//b[contains(.,'x',"?lang=en-US")]
};
