xquery version "3.1";

module namespace concat="http://exist-db.org/xquery/test/string-concatenation";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $concat:XML :=
    <a>
        <b>
            <c>CC1</c>
            <c>CC2</c>
        </b>
    </a>;

declare
    %test:assertEquals("ungrateful")
function concat:strings() {
    ('un' ||  'grateful')
};

declare
    %test:assertEquals("The result is 22.5")
function concat:numbers() {
    ("The result is " ||  22.5)
};

declare
    %test:assertEquals("unGRATEFUL")
function concat:function-call() {
    ("un" ||  upper-case("grateful"))
};

declare
    %test:assertEquals("The result is ")
function concat:empty-sequence1() {
    ("The result is " ||  ())
};

declare
    %test:assertTrue
function concat:empty-sequence2() {
    (() || ()) instance of xs:string
};

declare
    %test:assertTrue
function concat:empty-sequence3() {
    (() || ()) eq ""
};

declare
    %test:assertEquals(1)
function concat:empty-strings() {
    count("" || "")
};

declare
    %test:assertEquals("zzzzz123")
function concat:nested() {
    (("zzz" || "zz") || "123")
};

declare
    %test:assertEquals("ungrateful death")
function concat:multiple() {
    ('un' || () || 'grateful' || ' ' || "death")
};

declare
    %test:assertEquals("12-16")
function concat:precedence1() {
    12 || 34 - 50
};

declare
    %test:assertTrue
function concat:precedence2() {
    "1234" eq 12 || 34
};

declare
    %test:assertEquals("1234")
function concat:precedence3() {
    if (true()) then ("1234") else (12)
    || "I belong to someone else"
};

declare
    %test:assertTrue
function concat:nodes() {
    $concat:XML/b/c[1] || 2 eq "CC12"
};

declare
    %test:assertError("FOTY0013")
function concat:bad-argument() {
    ("abc" || "abc" || fn:concat#3)
};

(: test for #1828 :)
declare
    %test:assertEquals("||||")
function concat:mode-safe-element-content() {
    <a>||</a> || '||'
};

declare
    %test:assertEquals("||||")
function concat:mode-safe-attribute-value() {
    <a b="||" />/@b || '||'
};

declare
    %test:assertEquals("<a>bc</a>")
function concat:enclosed-in-element-content() {
    <a>{ "b" || "c" }</a>
};

declare
    %test:assertEquals("<a b='12'/>")
function concat:decimal-in-attribute-value() {
    <a b="{ 1 || 2 }"/>
};

declare
    %test:assertEquals("<a b='cd'/>")
function concat:variables-in-attribute-value() {
    let $c := "c"
    let $d := "d"
    return <a b="{ $c || $d }"/>
};

(:~
 : The two tests below cannot be commented in.
 : They cause a parsing error and therefore must
 : stay like this, until the underlying issue has
 : been fixed
 : https://github.com/eXist-db/exist/issues/291
 :)
(:
declare
    %test:pending
    %test:assertEquals("<a b='ccdd'/>") 
function concat:sq-strings-in-attribute-value() {
    <a b="{ 'cc' || 'dd' }"/>
};

declare
    %test:pending
    %test:assertEquals("<a b='ccdd'/>")
function concat:dq-strings-in-attribute-value() {
    <a b="{ "cc" || "dd" }"/>
};
:)

declare
    %test:assertEquals("|| ||||")
function concat:string-constructor() {
    ``[|| `{ "||" || '||' }`]``
};
