xquery version "3.1";

module namespace t="http://exist-db.org/xquery/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertFalse
function t:castable-test() {
    "0" castable as xs:QName
};