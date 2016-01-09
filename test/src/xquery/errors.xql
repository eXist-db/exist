xquery version "3.0";

(:~
 : Tests to check if the reported line numbers for errors are correct
 : if a function is called dynamically. In previous eXist versions, the
 : line number always pointed to the place where the function item was created,
 : not the actual function body.
 :)
module namespace et="http://exist-db.org/xquery/test/error-test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: Causes an error when called :)
declare function et:test($a) {
    $a + "bla"
};

(: May cause an error when called :)
declare function et:test2($a) {
    $a/foo
};

declare
    %test:assertEquals(15)
function et:dynamically-called-function() {
    let $fn := function-lookup(xs:QName("et:test"), 1)
    return
        try {
            $fn(1)
        } catch * {
            $err:line-number
        }
};

declare
    %test:assertEquals(20)
function et:dynamically-called-function-path-expr() {
    let $fn := function-lookup(xs:QName("et:test2"), 1)
    return
        try {
            $fn(1)
        } catch * {
            $err:line-number
        }
};

declare
    %test:assertEquals(51)
function et:inline-function-call() {
    let $fn := function($a) {
        $a + "bla"
    }
    return
        try {
            $fn(1)
        } catch * {
            $err:line-number
        }
};

declare
    %test:assertEquals(15)
function et:function-reference-call() {
    let $fn := et:test#1
    return
        try {
            $fn(1)
        } catch * {
            $err:line-number
        }
};