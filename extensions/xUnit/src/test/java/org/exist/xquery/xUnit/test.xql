xquery version "3.0";

declare namespace test="http://exist-db.org/xquery/xUnit";
declare namespace ex="http://exist-db.org/xquery/ex";

declare %test:assertEquals(70) function test:a1() {
    20 + 50
};

declare %test:assertEquals(70) function test:a2() {
    21 + 50
};

""