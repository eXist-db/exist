xquery version "3.0";

module namespace fnt="http://exist-db.org/test/fn";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $fnt:composed := '&#x00C5;&#x0073;&#x0074;&#x0072;&#x00F6;&#x006D;';
declare variable $fnt:decomposed := '&#x0041;&#x030A;&#x0073;&#x0074;&#x0072;&#x006F;&#x0308;&#x006D;';

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