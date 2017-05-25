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