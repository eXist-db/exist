xquery version "3.1";

module namespace hofs="http://exist-db.org/xquery/test/higher-order-functions";

declare namespace test="http://exist-db.org/xquery/xqsuite";


declare %private function hofs:declared-function ($a) { true() };


declare
    %test:assertError("XPTY0004")
function hofs:for-each-function-arity-0 () {
    for-each((1 to 3), function () { 1 })
};

declare
    %test:assertEquals(1, 1, 1)
function hofs:for-each-function-arity-1 () {
    for-each((1 to 3), function ($a) { 1 })
};

declare
    %test:assertError("XPTY0004")
function hofs:for-each-function-arity-2 () {
    for-each((1 to 3), function ($a, $b) { 1 })
};

declare
    %test:assertError("XPTY0004")
function hofs:for-each-pair-function-arity-0 () {
    for-each-pair((1 to 3), (1 to 3),
        function () { 1 })
};

declare
    %test:assertError("XPTY0004")
function hofs:for-each-pair-function-arity-1 () {
    for-each-pair((1 to 3), (1 to 3),
        function ($a) { 1 })
};

declare
    %test:assertEquals(2, 4, 6)
function hofs:for-each-pair-function-arity-2 () {
    for-each-pair((1 to 3), (1 to 3),
        function ($a, $b) { $a + $b })
};

declare
    %test:assertError("XPTY0004")
function hofs:filter-function-arity-0 () {
    filter((1 to 3), function () { true() })
};

declare
    %test:assertEquals(1,2,3)
function hofs:filter-function-arity-1 () {
    filter((1 to 3), function ($a) { true() })
};

declare
    %test:assertError("XPTY0004")
function hofs:filter-function-arity-2 () {
    filter((1 to 3), function ($a, $b) { true() })
};

declare
    %test:assertError("XPTY0004")
function hofs:fold-left-function-arity-0 () {
    fold-left((1 to 3), 0, function () { true() })
};

declare
    %test:assertError("XPTY0004")
function hofs:fold-left-function-arity-1 () {
    fold-left((1 to 3), 0,function ($a) { true() })
};

declare
    %test:assertTrue
function hofs:fold-left-function-arity-2 () {
    fold-left((1 to 3), 0,function ($r, $n) { true() })
};

declare
    %test:assertError("XPTY0004")
function hofs:fold-right-function-arity-0 () {
    fold-right((1 to 3), 0, function () { true() })
};

declare
    %test:assertError("XPTY0004")
function hofs:fold-right-function-arity-1 () {
    fold-right((1 to 3), 0,function ($a) { true() })
};

declare
    %test:assertTrue
function hofs:fold-right-function-arity-2 () {
    fold-right((1 to 3), 0,function ($r, $n) { true() })
};

declare
    %test:assertEquals(1, 2, 3)
function hofs:declared-function-test () {
    filter((1 to 3), hofs:declared-function#1)
};

(: https://github.com/eXist-db/exist/issues/3382 :)
declare
    %test:pending
    %test:assertError("XPTY0004")
function hofs:function-has-wrong-return-type () {
    filter((0 to 1), function ($a) { $a })
};

(: https://github.com/eXist-db/exist/issues/3382 :)
declare
    %test:pending
    %test:assertError("XPTY0004")
function hofs:return-mixed-types () {
    filter((true(), false(), 1, ""), function ($a) { $a })
};

declare
    %test:assertTrue
function hofs:return-boolean () {
    filter((true(), false()), function ($a) { $a })
};

declare
    %test:assertEquals(1)
function hofs:function-correct-return-type () {
    filter((0 to 1), function ($a) as xs:boolean { xs:boolean($a) })
};

declare
    %test:assertEquals(1)
function hofs:partially-applied-function () {
    filter((0 to 1), function ($a, $b) as xs:boolean {
        $a and $b }(?, true()))
};

declare
    %test:assertEquals(1)
function hofs:type-constructor () {
    filter((0 to 1), xs:boolean(?))
};
