xquery version "3.1";

module namespace ao="http://exist-db.org/xquery/test/arrowop";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("Hello world")
function ao:func-by-name1() {
    "Hello" => concat(" ", "world")
};

declare
    %test:assertEquals(3)
function ao:func-by-name2() {
    ('A', 'B', 'C') => count()
};

declare
    %test:assertEquals("hey")
function ao:func-by-name3() {
    "hello" => replace("llo", "y")
};

declare
    %test:assertEquals(3)
function ao:func-inline() {
    ('A', 'B', 'C') => (function($sequence) { count($sequence)})()
};

declare
    %test:assertEquals(11)
function ao:func-in-sequence1() {
    "Hello" => concat(" world") => string-length()
};

declare
    %test:assertEquals("123", "ABC", "dl")
function ao:func-in-sequence2() {
    "123 abc ㎗" => upper-case() => normalize-unicode('NFKC') => tokenize("\s+")
};

declare
    %test:assertEquals("W-E-L-C-O-M-E")
function ao:func-in-sequence3() {
    'w e l c o m e' => upper-case() => tokenize(" ") => string-join('-')
};

declare
    %test:assertEquals("HELLO", "WORLD")
function ao:func-in-sequence-inner() {
    "hello world"=>(function($in) { $in=>upper-case()=>tokenize("\s+") })()
};

declare 
    %test:assertEquals(11)
function ao:var() {
    let $fn := string-length#1
    return
        "Hello" => concat(" world") => $fn()
};

declare 
    %test:assertEquals("HELLO", "WORLD")
function ao:var-partial() {
    let $fn1 := upper-case#1
    let $fn2 := tokenize(?, "\s+")
    return
        "Hello" => concat(" world") => $fn1() => $fn2()
};

declare 
    %test:assertEquals("a=1", "b=2", "c=3")
function ao:func-as-input1() {
    function($k, $v) {
        $k || "=" || $v
    } =>
    (
        map:for-each(
            map {
                "a" : "1",
                "b" : "2",
                "c" : "3"
            },
            ?
        )
    )()
};

declare 
    %test:assertEquals("a=1", "b=2", "c=3")
function ao:func-as-input2() {
    let $me := map:for-each(
        map {
            "a" : "1",
            "b" : "2",
            "c" : "3"
        },
        ?
    )
    return
        function($k, $v) {
            $k || "=" || $v
        } => $me()
};

declare %private function ao:foo($x) {
    ao:foo($x, "bar")
};

declare %private function ao:foo($x, $y) {
    $x => string-length()
};

declare
    %test:assertEquals(3)
function ao:forward-reference() {
    ao:foo("foo")
};

declare
    %test:assertEquals("1-2-3")
function ao:type-checks-internal-func() {
    (1, 2, 3) => string-join("-")
};

declare %private function ao:string-join($s as xs:string*, $sep as xs:string) {
    string-join($s, $sep)
};

declare
    %test:assertEquals("1-2-3")
function ao:type-checks-user-func() {
    (1, 2, 3) => ao:string-join("-")
};

declare function ao:A($x) { 
    let $y := $x || $x 
    return 
        $y => ao:B()
};

declare function ao:B($x) {
    string-length($x)
};

declare
	%private
function ao:wrap-with-explicit-type-conversion ($item as item()) as item() {
    <wrap>{xs:string($item)}</wrap>
};

declare
    %test:assertEquals(10)
function ao:function-declared-later() {
    ao:A("hello")
};

(:~
    the tests below (`ao:wrap-*`) were added because of
    https://github.com/exist-db/exist/issues/1960
~:)
declare
	%private
function ao:wrap ($item as item()) as item() {
    <wrap>{$item}</wrap>
};

declare
	%private
function ao:first-element-text ($s as node()*) as text() {
    $s[1]/text()
};

declare
    %test:assertEquals("1")
function ao:wrap-atomic-sequence () {
    (1, 2, 3)
        => for-each(ao:wrap#1)
        => ao:first-element-text()
};

declare
    %test:assertEquals("1")
function ao:wrap-atomic-sequence-with-explicit-type-conversion () {
    (1, 2, 3)
        => for-each(ao:wrap-with-explicit-type-conversion#1)
        => ao:first-element-text()
};

declare
	%private
function ao:get-i-elements ($i as item()) { $i//i };

(:~
    must be 3 not 9
    closes https://github.com/exist-db/exist/issues/1960
 ~:)
declare
    %test:assertEquals(3)
function ao:wrap-element-sequence () {
    let $xml := <root><i/><i/><i/></root>
    return $xml/node()
        => for-each(ao:wrap#1)
        => for-each(ao:get-i-elements#1)
        => count()
};

(:~
    must not fail with duplicate attribute exception err:XQDY0025
    closes https://github.com/exist-db/exist/issues/1960
 ~:)
declare
    %test:assertEquals('1')
function ao:wrap-attribute-from-sequence () {
    (<item n="1"/>, <item n="2"/>, <item n="3"/>)
        => for-each(function ($item as item()) as element(a) { <a>{$item/@n}</a> })
        => (function($sequence) {
                $sequence[1]/@n/string()
            })()
};

declare
    %test:assertError('XPDY0002')
function ao:filter-with-contextitem () {
    (<item n="1"/>, <item n="2"/>, <item n="3"/>)
         => filter(function ($item as item())  { . })
         => count()
};

declare
    %test:assertEquals('abcd')
function ao:fold-left-with-contextitem () {
    (<a/>,<b/>,<c/>,<d/>)
        => fold-left('', function ($r, $a) { $r || xs:string(node-name($a)) })
};

declare
    %test:assertEquals('dcba')
function ao:fold-right-with-contextitem () {
    (<a/>,<b/>,<c/>,<d/>)
        => fold-right('', function ($a, $r) { $r || xs:string(node-name($a)) })
};

declare
    %test:assertEquals('dcba')
function ao:fold-right-with-contextitem () {
    ('a','b','c','d')
        => fold-right('', function ($a, $r) { $r || $a })
};

declare
    %test:assertEquals('aabbaabb')
function ao:for-each-pair-with-contextitem () {
    (<a/>,<b/>,<a/>,<b/>)
        => for-each-pair((<a/>,<b/>,<a/>,<b/>), function ($a, $b) { node-name($a) || node-name($b) })
        => string-join()
};

