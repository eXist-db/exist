xquery version "3.0";

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
    %test:assertEquals(3)
function ao:func-inline() {
    ('A', 'B', 'C') => (function( $sequence) { count( $sequence)})()
};

declare
    %test:assertEquals(11)
function ao:func-in-sequence1() {
    "Hello" => concat(" world") => string-length()
};

declare
    %test:assertEquals("HELLO", "WORLD")
function ao:func-in-sequence2() {
    "hello world"=>upper-case()=>normalize-unicode()=>tokenize("\s+")
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