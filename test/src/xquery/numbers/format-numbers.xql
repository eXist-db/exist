xquery version "3.0";

module namespace fd="http://exist-db.org/xquery/test/format-numbers";

declare namespace test="http://exist-db.org/xquery/xqsuite";


declare
    %test:args("12345.6")
    %test:assertError("FODF1310")
function fd:invalid-picture($number as numeric) {
    format-number($number, "#.###,00")
};

declare
    %test:args("12345.6")
    %test:assertEquals("12,345.60")
function fd:simple-number($number as numeric) {
    format-number($number, "#,###.##")
};

declare
    %test:args("#0")
    %test:assertEquals("0")
    %test:args("#0.0")
    %test:assertEquals("0.0")
    %test:args("#0.00")
    %test:assertEquals("0.00")
    %test:args("#0.000")
    %test:assertEquals("0.000")
    %test:args("#0.0#")
    %test:assertEquals("0.0")
function fd:decimal-zeros($picture as xs:string) {
    format-number(0, $picture)
};