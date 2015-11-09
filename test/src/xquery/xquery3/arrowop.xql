xquery version "3.0";

module namespace ao="http://exist-db.org/xquery/test/arrowop";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("Hello world")
function ao:func-by-name() {
    "Hello" => concat(" ", "world")
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
    %test:assertEquals("HELLO", "WORLD")
function ao:func-in-sequence2() {
    "hello world"=>(function($in) { $in=>upper-case()=>tokenize("\s+") })()
};