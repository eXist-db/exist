xquery version "3.0";

declare namespace test="http://exist-db.org/xquery/xUnit";
declare namespace ex="http://exist-db.org/xquery/ex";

declare %test:assertEquals(70) function ex:add($a, $b) {
    $a + $b
};

let $f1 := ex:add#2
let $test1 := $f1(20, 50)
let $test2 := $f1(21, 50)

return "ok"