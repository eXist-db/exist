xquery version "3.0";
module namespace test="http://exist-db.org/xquery/xUnit";

declare %test:assertEquals(70) function test:a1() {
    20 + 50
};

declare %test:assertEquals(70) function test:a2() {
    21 + 50
};