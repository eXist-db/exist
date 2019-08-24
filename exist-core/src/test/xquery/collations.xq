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
function collations:non-empty-string-contains() {
    doc("/db/collations-test/test.xml")//a[contains(.,'x',"?lang=en-US")]
};

declare
    %test:assertEmpty
function collations:empty-string-contains() {
    doc("/db/collations-test/test.xml")//b[contains(.,'x',"?lang=en-US")]
};

declare
    %test:assertEquals("<a>xxx</a>")
function collations:non-empty-string-starts-with() {
    doc("/db/collations-test/test.xml")//a[starts-with(.,'x',"?lang=en-US")]
};

 declare
    %test:assertEmpty
function collations:empty-string-starts-with() {
    doc("/db/collations-test/test.xml")//b[starts-with(.,'x',"?lang=en-US")]
};

declare
    %test:assertEquals("<a>xxx</a>")
function collations:non-empty-string-ends-with() {
    doc("/db/collations-test/test.xml")//a[ends-with(.,'x',"?lang=en-US")]
};

 declare
    %test:assertEmpty
function collations:empty-string-ends-with() {
    doc("/db/collations-test/test.xml")//b[ends-with(.,'x',"?lang=en-US")]
};

declare
    %test:assertEquals("")
    function collations:substring-after-empty-string() {
        substring-after("", "test", "?lang=en-US")
};

declare
    %test:assertEquals("")
    function collations:substring-before-empty-string() {
        substring-before("", "test", "?lang=en-US")
};

declare
    %test:assertEquals("")
    function collations:substring-after-empty-sequence() {
        substring-after((), "test", "?lang=en-US")
};

declare
    %test:assertEquals("")
    function collations:substring-before-empty-sequence() {
        substring-before((), "test", "?lang=en-US")
};