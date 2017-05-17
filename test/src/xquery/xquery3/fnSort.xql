xquery version "3.0";

module namespace testSort="http://exist-db.org/xquery/test/function_sort";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("1 3 4 5 6")
function testSort:single_parameter() {
    fn:sort((1, 4, 6, 5, 3))
};

declare
    %test:assertEquals("1 -2 5 8 10 -10 10")
function testSort:order_by_abs_key() {
    fn:sort((1, -2, 5, 10, -10, 10, 8), (), fn:abs#1)
};