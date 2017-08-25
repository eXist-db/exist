xquery version "3.1";

(:~
 : Test various F+O functions
 :)
module namespace fnt="http://exist-db.org/xquery/test/fn";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $fnt:composed := '&#x00C5;&#x0073;&#x0074;&#x0072;&#x00F6;&#x006D;';
declare variable $fnt:decomposed := '&#x0041;&#x030A;&#x0073;&#x0074;&#x0072;&#x006F;&#x0308;&#x006D;';

declare
    %test:setUp
function fnt:store() {
    let $col := xmldb:create-collection("/db", "fn-test")
    return
        (
            xmldb:store($col, "test.xml", <something/>),
            xmldb:store($col, "test.bin", "some binary", "application/octet-stream")
        )
};

declare
    %test:tearDown
function fnt:cleanup() {
    xmldb:remove("/db/fn-test")
};

declare 
    %test:args("NFC")
    %test:assertEquals(6, 6)
    %test:args("NFD")
    %test:assertEquals(8, 8)
    %test:args("NFKD")
    %test:assertEquals(8, 8)
    %test:args("NFKC")
    %test:assertEquals(6, 6)
    %test:args("XYZ")
    %test:assertError
function fnt:normalize-unicode($normalization-form as xs:string) {
    count(string-to-codepoints(normalize-unicode($fnt:composed, $normalization-form))),
    count(string-to-codepoints(normalize-unicode($fnt:decomposed, $normalization-form)))
};

declare
    %test:args("test.xml")
    %test:assertEquals("true")
    %test:args("test.bin")
    %test:assertEquals("false")
    %test:args("no-such-file.xml")
    %test:assertEquals("false")
    %test:args("no-such-file.bin")
    %test:assertEquals("false")
function fnt:doc-available($filename as xs:string) {
    fn:doc-available("/db/fn-test/" || $filename)
};

declare
    %test:assertEquals(0, 0)
function fnt:tokenize-empty($filename as xs:string) {
     count(() => tokenize("\s")),
     tokenize((), "\s")
};

