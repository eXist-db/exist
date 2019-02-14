xquery version "3.0";

module namespace tsw="http://exist-db.org/xquery/test/typeswitch";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare 
    %test:assertEquals("a|b")
function tsw:elements1() {
    let $node := <root><a/><b/><c/></root>
    return
        typeswitch($node/b)
            case $a as element(a) | element(b) return "a|b"
            case $a as element(c) return "c"
            default return "unknown"
};

declare 
    %test:assertEquals("c")
function tsw:elements2() {
    let $node := <root><a/><b/><c/></root>
    return
        typeswitch($node/c)
            case $a as element(a) | element(b) return "a|b"
            case $a as element(c) return "c"
            default return "unknown"
};

declare 
    %test:assertEquals("a")
function tsw:elements-match-first() {
    let $node := <root><a/><b/><c/></root>
    return
        typeswitch($node/a)
            case $a as element(a) return "a"
            case $a as element() return "other element"
            default return "unknown"
};

declare 
    %test:assertEquals("other element")
function tsw:elements-match-supertype() {
    let $node := <root><a/><b/><c/></root>
    return
        typeswitch($node/b)
            case $a as element(a) return "a"
            case $a as element() return "other element"
            default return "unknown"
};

declare 
    %test:assertEquals("unknown")
function tsw:element-empty() {
    let $node := <root><a/><b/><c/></root>
    return
        typeswitch($node/d)
            case $a as element(a) | element(b) return "a|b"
            case $a as element(c) return "c"
            default return "unknown"
};

declare
    %test:assertEquals("a|b")
function tsw:element-one-or-more() {
    let $node := <root><a/><a/></root>
    return
        typeswitch($node/a)
            case $a as element(a)+ return "a|b"
            default return "unknown"
};

declare
    %test:assertEquals("unknown")
function tsw:element-exactly-one() {
    let $node := <root><a/><a/></root>
    return
        typeswitch($node/a)
            case $a as element(a) return "a|b"
            default return "unknown"
};

declare
    %test:assertEquals("a|b")
function tsw:element-zero-or-one() {
    let $node := <root><a/><a/></root>
    return
        typeswitch($node/b)
            case $a as element(a)? | element(b)? return "a|b"
            default return "unknown"
};

declare 
    %test:assertEquals("atomic")
function tsw:node-or-atomic() {
    let $value := 22.0
    return
        typeswitch($value)
            case node() return "element"
            case xs:anyAtomicType return "atomic"
            default return "unknown"
};

declare 
    %test:assertEquals("number")
function tsw:atomic-number() {
    let $value := 22.0
    return
        typeswitch ($value)
            case xs:integer | xs:decimal return "number"
            case xs:string return "string"
            default return "unknown"
};

declare 
    %test:assertEquals("string")
function tsw:atomic-string() {
    let $value := "Hello"
    return
        typeswitch ($value)
            case xs:integer | xs:decimal return "number"
            case xs:string return "string"
            default return "unknown"
};

declare 
    %test:assertEquals("boolean")
function tsw:atomic-boolean() {
    let $value := true()
    return
        typeswitch ($value)
            case xs:integer | xs:decimal return "number"
            case xs:boolean return "boolean"
            default return "unknown"
};

declare 
    %test:assertEquals("unknown")
function tsw:atomic-no-match() {
    let $value := xs:date("2015-02-03")
    return
        typeswitch ($value)
            case xs:integer | xs:decimal return "number"
            case xs:string return "string"
            default return "unknown"
};

declare 
    %test:assertEquals("3rd February, 2015")
function tsw:atomic-date() {
    let $value := xs:date("2015-02-03")
    return
        typeswitch ($value)
            case xs:integer | xs:decimal return "number"
            case xs:string return "string"
            case xs:date return format-date($value, "[D1o] [MNn], [Y]", "en", (), ())
            default return "unknown"
};

declare 
    %test:assertEquals("unknown")
function tsw:atomic-empty() {
    let $value := ()
    return
        typeswitch ($value)
            case xs:integer | xs:decimal return "number"
            case xs:string return "string"
            default return "unknown"
};

declare 
    %test:assertEquals("number")
function tsw:atomic-zero-or-one() {
    let $value := ()
    return
        typeswitch ($value)
            case xs:integer? | xs:decimal? return "number"
            case xs:string return "string"
            default return "unknown"
};

declare 
    %test:assertEquals("unknown")
function tsw:atomic-default() {
    let $value := <node/>
    return
        typeswitch ($value)
            case xs:integer? | xs:decimal? return "number"
            case xs:string return "string"
            default return "unknown"
};