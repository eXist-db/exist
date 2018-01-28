xquery version "3.1";

(:~
 : Test fn:unparsed-text and friends
 :)
module namespace upt="http://exist-db.org/xquery/test/unparsed-text";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:setUp
function upt:store() {
    let $col := xmldb:create-collection("/db", "test-unparsed-text")
    return
        xmldb:store($col, "test.txt", "Hello&#10;world!", "application/octet-stream")
};

declare
    %test:tearDown
function upt:cleanup() {
    xmldb:remove("/db/test-unparsed-text")
};

declare 
    %test:assertXPath("contains($result, 'eXist')")
function upt:unparsed-text-from-url() {
    unparsed-text("https://raw.githubusercontent.com/eXist-db/exist/develop/test/src/xquery/README")
};

declare 
    %test:assertXPath("contains($result, '----')")
function upt:unparsed-text-lines-from-url() {
    unparsed-text-lines("https://raw.githubusercontent.com/eXist-db/exist/develop/test/src/xquery/README")[2]
};

declare 
    %test:assertEquals(6)
function upt:unparsed-text-lines-from-url-count() {
    count(unparsed-text-lines("https://raw.githubusercontent.com/eXist-db/exist/develop/test/src/xquery/README"))
};

declare 
    %test:assertXPath("contains($result, 'eXist')")
function upt:unparsed-text-from-url-encoding() {
    unparsed-text("https://raw.githubusercontent.com/eXist-db/exist/develop/test/src/xquery/README", "UTF-8")
};

declare 
    %test:assertEquals("Hello&#10;world!")
function upt:unparsed-text-from-db() {
    unparsed-text("/db/test-unparsed-text/test.txt")
};

declare 
    %test:assertEquals("Hello&#10;world!")
function upt:unparsed-text-from-db-full() {
    unparsed-text("xmldb:exist:///db/test-unparsed-text/test.txt")
};

declare 
    %test:assertEquals("Hello", "world!")
function upt:unparsed-text-lines-from-db() {
    unparsed-text-lines("/db/test-unparsed-text/test.txt")
};

declare 
    %test:assertError("FOUT1170")
function upt:fragment-identifier() {
    unparsed-text-lines("http://www.example.org/#fragment")
};

declare 
    %test:assertError("FOUT1170")
function upt:fragment-identifier-encoding() {
    unparsed-text("http://www.example.org/#fragment", "UTF-8")
};

declare 
    %test:assertError("FOUT1170")
function upt:invalid-uri() {
    unparsed-text-lines("http://www.example.org/%gg")
};

declare 
    %test:assertError("FOUT1170")
function upt:invalid-uri2() {
    unparsed-text-lines(":/")
};

declare 
    %test:assertError("FOUT1170")
function upt:non-existant() {
    unparsed-text-lines("http://www.w3.org/fots/unparsed-text/does-not-exist.txt")
};

declare 
    %test:assertError("FOUT1170")
function upt:unsupported-scheme() {
    fn:unparsed-text-lines("surely-nobody-supports-this:/path.txt")
};

declare 
    %test:assertError("FOUT1170")
function upt:windows-path() {
    unparsed-text-lines("C:\file-might-exist.txt", "utf-8")
};