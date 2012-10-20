xquery version "3.0";

module namespace fd="http://exist-db.org/xquery/test/format-numbers";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:
declare
    %test:args("12345.6")
    %test:assertEquals("12.345,00")
function fd:simple-number($number as numeric) {
    format-number($number, "#.###,00")
};
:)

declare
    %test:args("12345.6")
    %test:assertEquals("12,345.60")
function fd:simple-number($number as numeric) {
    format-number($number, "#,###.##")
};
