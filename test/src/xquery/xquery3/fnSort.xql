xquery version "3.1";

module namespace testSort="http://exist-db.org/xquery/test/function_sort";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("1","3","4","5","6")
function testSort:integers_vs_strings() {
    fn:sort((1, 4, 6, 5, 3))
};

declare
    %test:assertEquals("1","3","4","5","6")
function testSort:strings() {
    fn:sort(("1", "4", "6", "5", "3"))
};

declare
    %test:assertEquals(1,3,4,5,6)
function testSort:integers_vs_integers() {
    fn:sort((1, 4, 6, 5, 3))
};

declare
    %test:assertEquals("1","-2","5","8","10","-10","10")
function testSort:order_by_abs_key() {
    fn:sort((1, -2, 5, 10, -10, 10, 8), (), fn:abs#1)
};

declare
    %test:assertEquals("3","2","1")
function testSort:order_by_inline_function() {
    fn:sort((1,2,3), (), function($x) { -$x })
};

declare
    %test:assertError("FORG0006")
function testSort:order_fail() {
    fn:sort((1,'a'))
};

declare
    %test:assertTrue
function testSort:empty() {
    fn:empty(fn:sort(()))
};

declare
    %test:assertEquals(11)
function testSort:item_integer() {
    fn:sort(11)
};

declare
    %test:assertError("XPTY0004")
function testSort:two_parameters() {
    fn:sort(15,15)
};

declare
    %test:assertEquals(17,17)
function testSort:dublicate_items() {
    fn:sort((17,17))
};

declare
    %test:assertEquals("Donatello", "Leonardo", "Michelangelo", "Raphael")
function testSort:items_xml() {
    <turtles>
        <name>Leonardo</name>
        <name>Raphael</name>
        <name>Donatello</name>
        <name>Michelangelo</name>
    </turtles>
    !sort(*)!data()
};

declare
    %test:assertEquals("<e>a</e>","b","c","d","<f>e</f>")
function testSort:items_xml() {
    sort(("b", "d", <e>a</e>, "c", <f>e</f>))
};

declare
    %test:assertEquals("89","33","21")
function testSort:item_map() {
    fn:sort( ( map{"key":1, "value":89}, map{"key":6, "value":21}, map{"key":2, "value":33}), (), map:get(?, "key"))?value
};

declare
    %test:assertEquals("1","6","7","5","3","2","4")
function testSort:item_map() {
    let $employees := (
        <emp id='1'><name><last>Cawcutt</last></name></emp>,
        <emp id='2'><name><first>Hans</first><last>Gro&#xeb;r</last></name></emp>,
        <emp id='3'><name><first>Domingo</first><last>De Silveira</last></name></emp>,
        <emp id='4'><name><first>Keith</first><last>O'Brien</last></name></emp>,
        <emp id='5'><name><first>Susan</first><last>Cawcutt</last></name></emp>,
        <emp id='6'><name><first>Martin</first><last>Cawcutt</last></name></emp>,
        <emp id='7'><name><first>Martin</first><first>James</first><last>Cawcutt</last></name></emp>
    )
    return fn:sort($employees, default-collation(), function ($emp) {$emp/name/last, $emp/name/first}) ! number(@id)
};

declare
    %test:assertTrue
function testSort:items_mixed() {
    let $r := sort((
        [<e>a</e>,<e>b</e>],
        [(<f>a</f>,<f>b</f>)],
        [<g>a</g>,<g>b</g>],
        "A",
        <h><i><j>A</j></i></h>
    ))
    return
        fn:deep-equal($r[1], "A") and
        fn:deep-equal($r[2], <h><i><j>A</j></i></h>) and
        fn:deep-equal($r[3], [<e>a</e>, <e>b</e>]) and
        fn:deep-equal($r[4], [(<f>a</f>, <f>b</f>)]) and
        fn:deep-equal($r[5], [<g>a</g>, <g>b</g>])
};

declare
%test:assertTrue
function testSort:items_array_with_NaN() {
    let $a := [xs:float("NaN"), 1],
    $b := [xs:float("NaN"), 2],
    $r := sort(($a,$b,$a,$b))
    return
        fn:deep-equal($r[1], [xs:float("NaN"), 1]) and
        fn:deep-equal($r[2], [xs:float("NaN"), 1]) and
        fn:deep-equal($r[3], [xs:float("NaN"), 2]) and
        fn:deep-equal($r[4], [xs:float("NaN"), 2])

};

declare
    %test:assertTrue
function testSort:items_mixed_with_array_1() {
    let $r := fn:sort( ([1, 2], 1) )
    return
        fn:deep-equal($r[1], 1) and
        fn:deep-equal($r[2], [1, 2])
};

declare
    %test:assertTrue
function testSort:items_mixed_with_array_2() {
    let $r := fn:sort( (1, [1, 2]) )
    return
        fn:deep-equal($r[1], 1) and
        fn:deep-equal($r[2], [1, 2])
};

declare
    %test:assertTrue
function testSort:items_mixed_with_empty_array_1() {
    let $r := fn:sort( ([()], 1) )
    return
        fn:deep-equal($r[1], [()]) and
        fn:deep-equal($r[2], 1)
};

declare
    %test:assertTrue
function testSort:items_mixed_with_empty_array_2() {
    let $r := fn:sort( (1, [()]) )
    return
        fn:deep-equal($r[1], [()]) and
        fn:deep-equal($r[2], 1)
};