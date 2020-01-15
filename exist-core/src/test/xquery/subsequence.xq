xquery version "3.1";

module namespace ss="http://exist-db.org/test/subsequence";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertTrue
function ss:pipeline1() {
    ("x", "y") => subsequence(1, 1) => matches("x")
};
