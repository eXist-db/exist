
(:~
 : Tests for the array datatype and associated functions.
 :)
module namespace arr="http://exist-db.org/test/arrays";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare 
    %test:args(1)
    %test:assertEquals(13)
    %test:args(3)
    %test:assertEquals(14)
function arr:function-item($pos as xs:int) {
    [13, 10, 14]($pos)
};

declare 
    %test:args(1)
    %test:assertEquals(13)
    %test:args(3)
    %test:assertEquals(14)
function arr:function-item2($pos as xs:int) {
    let $a := [13, 10, 14]
    return
        $a($pos)
};

declare
    %test:args(1)
    %test:assertEquals(13)
    %test:args(3)
    %test:assertEquals(14)
function arr:function-item3($pos as xs:int) {
    let $a := [13, 10, 14]
    let $f := function($array as function(*), $position as xs:int) {
        $array($position)
    }
    return
        $f($a, $pos)
};

declare
    %test:assertError("FOAY0001")
function arr:function-item-out-of-bounds() {
    let $a := [13, 10, 14]
    return
        $a(22)
};

declare
    %test:assertError("err:XPTY0004")
function arr:function-item-invalid() {
    let $a := [13, 10, 14]
    return
        $a("x")
};

declare
    %test:args(1)
    %test:assertEmpty
    %test:args(2)
    %test:assertEquals(27, 17, 0)
function arr:square-constructor1($pos as xs:int) {
    let $a := [(), (27, 17, 0)]
    return $a($pos)
};

declare
    %test:args(1)
    %test:assertEquals("<p>test</p>")
    %test:args(2)
    %test:assertEquals(55)
function arr:square-constructor2($pos as xs:int) {
    let $a := [<p>test</p>, 55]
    return $a($pos)
};

declare
    %test:assertEquals(0)
function arr:square-constructor3() {
    let $a := []
    return array:size($a)
};

declare
    %test:args(1)
    %test:assertEquals(27)
    %test:args(2)
    %test:assertEquals(17)
function arr:curly-constructor1($pos as xs:int) {
    let $x := (27, 17, 0)
    let $a := array { $x }
    return $a($pos)
};

declare
    %test:args(1)
    %test:assertEquals(27)
    %test:args(2)
    %test:assertEquals(17)
function arr:curly-constructor2($pos as xs:int) {
    let $a := array { (), (27, 17, 0) }
    return $a($pos)
};

declare
    %test:assertEquals(2)
function arr:with-map1() {
    let $a := [ map { "a": 1, "b": 2 } ]
    return $a(1)("b")
};

declare
    %test:assertEquals(3)
function arr:with-map2() {
    let $a := map { "a": 1, "b": [2, 3, 4] }
    return $a("b")(2)
};

declare
    %test:assertEquals(5)
function arr:nested1() {
    let $a := [ [1, 2, 3], [4, 5, 6] ]
    return $a(2)(2)
};

declare
    %test:assertEquals(5)
function arr:nested2() {
    let $a := array { [1, 2, 3], [4, 5, 6] }
    return $a(2)(2)
};

declare
    %test:args(2)
function arr:size() {
    array:size([<p>test</p>, 55])
};

declare
    %test:assertEquals(3, 4, 5)
function arr:append1() {
    array:append([1, 2], (3, 4, 5))(3)
};

declare
    %test:assertEmpty
function arr:append2() {
    array:append([1, 2], ())(3)
};

declare
    %test:assertEquals("a")
function arr:head1() {
    array:head(["a", "b"])
};

declare
    %test:assertEquals(2)
function arr:head2() {
    array:head([[1, 2], 3, 4, 5])(2)
};

declare
    %test:assertError("FOAY0001")
function arr:head-empty() {
    array:head([])
};

declare
    %test:assertEquals(2)
function arr:tail1() {
    array:tail([1, 2, 3])(1)
};

declare
    %test:assertEquals(2)
function arr:tail2() {
    array:tail([1, [2, 3]])(1)
};

declare
    %test:assertEquals(0)
function arr:tail3() {
    array:size(array:tail([1]))
};

declare
    %test:assertError("FOAY0001")
function arr:tail-empty() {
    array:tail([])
};

declare
    %test:args(2)
    %test:assertEquals("b", 3)
function arr:subarray1($start as xs:int) {
    let $a := array:subarray(["a", "b", "c", "d"], $start)
    return
        ($a(1), array:size($a))
};

declare 
    %test:assertEquals("array")
function arr:array-type1() {
    let $a := [1, 2]
    return
        typeswitch($a)
            case array(*) return
                "array"
            default return
                "no array"
};