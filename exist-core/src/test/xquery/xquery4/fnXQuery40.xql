(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "4.0";

(:~
 : Tests for XQuery 4.0 parser features implemented in eXist-db.
 :)
module namespace t = "http://exist-db.org/xquery/test/fn-xquery40";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: String templates :)
declare
    %test:assertEquals("hello")
function t:string-template-basic() {
    `hello`
};

declare
    %test:assertEquals("There were 10 green bottles")
function t:string-template-interpolation() {
    let $n := 10 return `There were {$n} green bottles`
};

declare
    %test:assertEquals("")
function t:string-template-empty() {
    ``
};

declare
    %test:assertEquals("a{b}c")
function t:string-template-escapes() {
    `a{{b}}c`
};

declare
    %test:assertTrue
function t:string-template-escapes-complex() {
    let $n := 10
    let $result := `"{{}}"'[``]'\\<> {$n}`
    (: Expected: "{}  then  '[`]'\\<> 10 :)
    let $expected := codepoints-to-string((34, 123, 125, 34, 39, 91, 96, 93, 39, 92, 92, 60, 62, 32, 49, 48))
    return $result eq $expected
};

(: otherwise operator :)

declare
    %test:assertEquals("hello")
function t:otherwise-non-empty() {
    "hello" otherwise "fallback"
};

declare
    %test:assertEquals("fallback")
function t:otherwise-empty() {
    () otherwise "fallback"
};

declare
    %test:assertEquals("first")
function t:otherwise-chain() {
    () otherwise () otherwise "first"
};

declare
    %test:assertEquals(42)
function t:otherwise-with-expr() {
    let $x := ()
    return $x otherwise 42
};

(: for key / for value :)

declare
    %test:assertEmpty
function t:for-key-empty-map() {
    for key $k in map { }
    return $k
};

declare
    %test:assertEquals(2, 4, 6)
function t:for-key-basic() {
    for key $k in map { 1: 'a', 2: 'b', 3: 'c' }
    order by $k
    return $k + $k
};

declare
    %test:assertEmpty
function t:for-value-empty-map() {
    for value $v in map { }
    return $v
};

declare
    %test:assertEquals(2, 4, 6)
function t:for-value-basic() {
    for value $v in map { 'a': 1, 'b': 2, 'c': 3 }
    order by $v
    return $v + $v
};

(: for key $k value $v :)

declare
    %test:assertEmpty
function t:for-key-value-empty-map() {
    for key $k value $v in map { }
    return $k || "=" || $v
};

declare
    %test:assertEquals("1=a", "2=b", "3=c")
function t:for-key-value-basic() {
    for key $k value $v in map { 1: 'a', 2: 'b', 3: 'c' }
    order by $k
    return $k || "=" || $v
};

declare
    %test:assertEquals("a=1", "b=2", "c=3")
function t:for-key-value-with-let() {
    for key $k value $v in map { 'a': 1, 'b': 2, 'c': 3 }
    let $pair := $k || "=" || $v
    order by $k
    return $pair
};

(: while clause :)

declare
    %test:assertEquals(1, 2, 3)
function t:while-basic() {
    for $x in 1 to 10
    while $x le 3
    return $x
};

declare
    %test:assertEmpty
function t:while-false-first() {
    for $x in 1 to 10
    while $x gt 100
    return $x
};

declare
    %test:assertEquals(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
function t:while-always-true() {
    for $x in 1 to 10
    while true()
    return $x
};

declare
    %test:assertEquals(2, 4)
function t:while-with-let() {
    for $x in 1 to 10
    let $doubled := $x * 2
    while $doubled le 5
    return $doubled
};

(: pipeline operator :)

declare
    %test:assertEquals(23)
function t:pipeline-basic() {
    23 -> .
};

declare
    %test:assertEquals(23, 24)
function t:pipeline-sequence() {
    (23, 24) -> .
};

declare
    %test:assertEmpty
function t:pipeline-empty() {
    () -> .
};

declare
    %test:assertEquals(8)
function t:pipeline-chain() {
    5 -> (1, 2, .) -> sum(.)
};

(: ordered maps — requires MapType changes from XQ4 functions branch :)

(: optional map keyword :)

declare
    %test:assertEquals(0)
function t:bare-map-empty() {
    map:size({})
};

declare
    %test:assertEquals(2)
function t:bare-map-single-entry() {
    {"a": 2}("a")
};

declare
    %test:assertEquals(2)
function t:bare-map-multi-entry() {
    map:size({"a": 1, "b": 2})
};

(: bare-map-keys-ordered: requires ordered MapType from functions branch :)

declare
    %test:assertEquals(2)
function t:bare-map-after-return() {
    let $m := {1: 2}
    return $m(1)
};

(: ========== Braced if ========== :)

declare
    %test:assertEquals("yes")
function t:braced-if-true() {
    (: XQ4 braced if: no else clause allowed with braces :)
    if (true()) { "yes" }
};

declare
    %test:assertEmpty
function t:braced-if-false() {
    (: XQ4 braced if: returns empty sequence when condition is false :)
    if (false()) { "yes" }
};

declare
    %test:assertEmpty
function t:braced-if-no-else() {
    if (false()) { "yes" }
};

declare
    %test:assertEquals(2)
function t:braced-if-numeric() {
    if (1 > 0) { 1 + 1 }
};

declare
    %test:assertEquals("big")
function t:braced-if-nested() {
    (: Braced if can contain traditional if/then/else inside :)
    if (true()) {
        if (10 > 5) then "big" else "small"
    }
};

(: ============ Braced switch ============ :)

declare
    %test:assertEquals("Meow")
function t:braced-switch-basic() {
    let $animal := "Cat"
    return
    switch ($animal) {
        case "Cow" return "Moo"
        case "Cat" return "Meow"
        case "Duck" return "Quack"
        default return "Unknown"
    }
};

declare
    %test:assertEquals("Oink")
function t:switch-multi-item-case() {
    let $x := 3
    return
    switch ($x)
        case (1 to 5) return "Oink"
        default return "Baa"
};

declare
    %test:assertEquals("Meow")
function t:switch-omitted-comparand() {
    let $animal := "Cat"
    return
    switch () {
        case $animal eq "Cow" return "Moo"
        case $animal eq "Cat" return "Meow"
        case $animal eq "Duck" return "Quack"
        default return "Unknown"
    }
};

declare
    %test:assertEquals("Empty")
function t:switch-empty-matches-empty() {
    let $x := ()
    return
    switch (head($x))
        case "a" return "A"
        case () return "Empty"
        default return "Default"
};

declare
    %test:assertEquals(5)
function t:braced-typeswitch-basic() {
    typeswitch (1) {
        case $i as xs:double return <wrap>test failed</wrap>
        case $i as xs:integer return 10 idiv 2
        case $i as xs:string return <wrap>test failed</wrap>
        default return <wrap>test failed</wrap>
    }
};

declare
    %test:assertEquals("text")
function t:braced-typeswitch-string() {
    typeswitch ("hello") {
        case $i as xs:integer return "number"
        case $i as xs:string return "text"
        default return "other"
    }
};

(: ============ Mapping arrow =!> ============ :)

declare
    %test:assertEquals("1", "2", "3")
function t:mapping-arrow-basic() {
    (1, 2, 3) =!> string()
};

declare
    %test:assertEquals("AB", "CB")
function t:mapping-arrow-concat() {
    ("A", "C") =!> concat("B")
};

declare
    %test:assertEmpty
function t:mapping-arrow-empty() {
    () =!> string()
};

declare
    %test:assertEquals(2, 4, 6)
function t:mapping-arrow-inline-fn() {
    (1, 2, 3) =!> (function($x) { $x * 2 })()
};

(: ============ Array/map filter ?[] ============ :)

declare
    %test:assertEquals(1, 2)
function t:filter-am-array-basic() {
    let $a := ["A", "B", 1, 2]
    return array:flatten($a?[. instance of xs:integer])
};

declare
    %test:assertEquals("B")
function t:filter-am-array-string() {
    let $a := ["A", "B", "C"]
    return array:flatten($a?[. = "B"])
};

declare
    %test:assertEquals(0)
function t:filter-am-array-empty() {
    array:size([]?[true()])
};

declare
    %test:assertEquals(2)
function t:filter-am-numeric-pred() {
    array:flatten([1, 2, 3]?[2])
};

declare
    %test:assertEquals("v1")
function t:filter-am-map() {
    let $m := map { "a": "v1", "b": "v2" }
    return $m?[.?key = "a"]?a
};

declare
    %test:assertEquals("abc")
function t:filter-am-map-lookup-key() {
    (: XQ4 ?key unary lookup in map filter predicate :)
    let $m := map { "abc": "a", "def": "g" }
    return map:keys($m?[contains(?key, ?value)])
};

(: ============ Hex/binary numeric literals ============ :)

declare
    %test:assertEquals(255)
function t:hex-literal-basic() {
    0xff
};

declare
    %test:assertEquals(3405691582)
function t:hex-literal-underscore() {
    0xCAFE_BABE
};

declare
    %test:assertEquals(10)
function t:binary-literal-basic() {
    0b1010
};

declare
    %test:assertEquals(240)
function t:binary-literal-underscore() {
    0b1111_0000
};

declare
    %test:assertEquals(1000000)
function t:numeric-underscore() {
    1_000_000
};

declare
    %test:assertEquals(1000.000001)
function t:decimal-underscore() {
    (: XQ4: underscores in decimal fractional part :)
    1_000.000_001
};

declare
    %test:assertEquals(1.000001e2)
function t:double-underscore() {
    (: XQ4: underscores in double literal :)
    1.000_001e0_2
};

(: ======== Lookup key selectors (XQ4) ======== :)

declare
    %test:assertEquals(81)
function t:lookup-string-literal() {
    (: XQ4: string literal as lookup key selector :)
    let $x := map { "first value": 81, "second value": 18 }
    return $x?"first value"
};

declare
    %test:assertEquals("two")
function t:lookup-decimal-literal() {
    (: XQ4: decimal literal as lookup key selector :)
    map { 1.1: "one", 1.2: "two", 1.3: "three" }?1.2
};

declare
    %test:assertEquals("two")
function t:lookup-double-literal() {
    (: XQ4: double literal as lookup key selector :)
    map { 1.1e0: "one", 1.2e0: "two", 1.3e0: "three" }?1.2e0
};

declare
    %test:assertEquals(81, 18)
function t:lookup-variable-ref() {
    (: XQ4: variable reference as lookup key selector :)
    let $x := map{"first":81, "second":18} return
    for $n in ("first", "second") return $x?$n
};

declare
    %test:assertEquals("b")
function t:lookup-context-item() {
    (: XQ4: context item as lookup key selector :)
    "second" -> map{"first": "a", "second": "b"}?.
};

declare
    %test:assertEquals("b")
function t:lookup-context-item-array() {
    (: XQ4: context item as key selector on array :)
    2 -> ["a", "b", "c"]?.
};

declare
    %test:assertTrue
function t:lookup-qname-literal() {
    (: XQ4: QName literal as lookup key selector :)
    map{ #xml:base : true(), #xml:space : false() }?#xml:base
};

(: ======== QName Literals ======== :)

declare
    %test:assertEquals("foo")
function t:qname-literal-local() {
    local-name-from-QName( #foo )
};

declare
    %test:assertEquals("")
function t:qname-literal-local-no-ns() {
    namespace-uri-from-QName( #foo )
};

declare
    %test:assertEquals("xs:integer")
function t:qname-literal-prefixed() {
    string( #xs:integer )
};

declare
    %test:assertEquals("http://www.w3.org/2001/XMLSchema")
function t:qname-literal-prefixed-ns() {
    namespace-uri-from-QName( #xs:integer )
};

(: ======== Default Parameter Values ======== :)

declare function local:add($x as xs:integer, $y as xs:integer := 10) {
    $x + $y
};

declare
    %test:assertEquals(13)
function t:default-param-override() {
    local:add(10, 3)
};

declare
    %test:assertEquals(20)
function t:default-param-used() {
    local:add(10)
};

declare function local:greet($name as xs:string, $greeting as xs:string := "Hello") {
    $greeting || ", " || $name || "!"
};

declare
    %test:assertEquals("Hello, World!")
function t:default-param-string() {
    local:greet("World")
};

declare
    %test:assertEquals("Hi, World!")
function t:default-param-string-override() {
    local:greet("World", "Hi")
};

(: ======================== :)
(: Choice/Union Item Types  :)
(: ======================== :)

declare
    %test:assertTrue
function t:choice-type-instance-of-string() {
    "hello" instance of (xs:string | xs:integer)
};

declare
    %test:assertTrue
function t:choice-type-instance-of-integer() {
    42 instance of (xs:string | xs:integer)
};

declare
    %test:assertFalse
function t:choice-type-instance-of-no-match() {
    3.14 instance of (xs:string | xs:integer)
};

declare
    %test:assertTrue
function t:choice-type-instance-of-three-types() {
    xs:date("2024-01-01") instance of (xs:string | xs:integer | xs:date)
};

declare
    %test:assertTrue
function t:choice-type-with-node-types() {
    <foo/> instance of (element() | text())
};

declare
    %test:assertTrue
function t:choice-type-text-node() {
    text { "hello" } instance of (element() | text())
};

declare
    %test:assertFalse
function t:choice-type-no-match-node() {
    <!-- comment --> instance of (element() | text())
};

declare function local:choice-param($x as (xs:string | xs:integer)) as xs:string {
    string($x)
};

declare
    %test:assertEquals("hello")
function t:choice-type-param-string() {
    local:choice-param("hello")
};

declare
    %test:assertEquals("42")
function t:choice-type-param-integer() {
    local:choice-param(42)
};

declare
    %test:assertTrue
function t:choice-type-with-cardinality() {
    (1, 2, 3) instance of (xs:string | xs:integer)*
};

declare
    %test:assertTrue
function t:choice-type-mixed-sequence() {
    ("hello", 42) instance of (xs:string | xs:integer)*
};

declare
    %test:assertXPath("$result instance of xs:date")
function t:choice-type-cast-as() {
    "2024-01-15" cast as (xs:dateTime | xs:date | xs:time)
};

declare
    %test:assertTrue
function t:choice-type-castable-as() {
    "2024-01-15" castable as (xs:dateTime | xs:date | xs:time)
};

declare
    %test:assertFalse
function t:choice-type-castable-as-false() {
    "not-a-date" castable as (xs:dateTime | xs:date | xs:time)
};

(: ======================== :)
(: Enumeration Types        :)
(: ======================== :)

declare
    %test:assertTrue
function t:enum-instance-of-match() {
    "c" instance of enum("a", "b", "c", "d")
};

declare
    %test:assertFalse
function t:enum-instance-of-no-match() {
    "g" instance of enum("a", "b", "c", "d")
};

declare
    %test:assertEquals("a")
function t:enum-cast-as() {
    "a" cast as enum("a", "b", "c", "d")
};

declare
    %test:assertTrue
function t:enum-castable-as-match() {
    "c" castable as enum("a", "b", "c", "d")
};

declare
    %test:assertFalse
function t:enum-castable-as-no-match() {
    "g" castable as enum("a", "b", "c", "d")
};

declare
    %test:assertFalse
function t:enum-instance-of-not-string() {
    42 instance of enum("42")
};

declare
    %test:assertTrue
function t:enum-in-choice-type() {
    "b" instance of (enum("a", "b") | xs:integer)
};

declare
    %test:assertTrue
function t:enum-choice-integer-match() {
    42 instance of (enum("a", "b") | xs:integer)
};

(: ======================== :)
(: Ternary Conditional Expr :)
(: ======================== :)

declare
    %test:assertEquals("yes")
function t:ternary-true() {
    true() ?? "yes" !! "no"
};

declare
    %test:assertEquals("no")
function t:ternary-false() {
    false() ?? "yes" !! "no"
};

declare
    %test:assertEquals(42)
function t:ternary-with-expr() {
    (1 = 1) ?? 42 !! 0
};

declare
    %test:assertEquals("B")
function t:ternary-nested() {
    false() ?? "A" !! (true() ?? "B" !! "C")
};

declare
    %test:assertEquals(2)
function t:ternary-with-or() {
    (false() or true()) ?? 2 !! 3
};

(: ========== XQ4 Method Call Operator (=?>) ========== :)

declare
    %test:assertEquals(6)
function t:method-call-simple() {
    let $rectangle := map { 'length': 3, 'width': 2,
        'area': function($self) { $self?length * $self?width } }
    return $rectangle =?> area()
};

declare
    %test:assertEquals(24)
function t:method-call-with-args() {
    let $rectangle := map { 'length': 3, 'width': 2,
        'resize': function($self, $scale) {
            map:put(map:put($self, 'length', $self?length * $scale), 'width', $self?width * $scale)
        },
        'area': function($self) { $self?length * $self?width }
    }
    return $rectangle =?> resize(2) =?> area()
};

declare
    %test:assertEmpty
function t:method-call-empty-sequence() {
    let $rectangle := map { 'length': 3, 'width': 2,
        'area': function($self) { $self?length * $self?width } }
    return $rectangle[2] =?> area()
};

declare
    %test:assertEquals(6, 20)
function t:method-call-multiple-maps() {
    let $rectangles := (
        map { 'length': 3, 'width': 2, 'area': function($self) { $self?length * $self?width } },
        map { 'length': 4, 'width': 5, 'area': function($self) { $self?length * $self?width } }
    )
    return $rectangles =?> area()
};

declare
    %test:assertError("XPTY0004")
function t:method-call-not-a-map() {
    let $arr := [1, 2, 3]
    return $arr =?> foo()
};

declare
    %test:assertError("XPTY0004")
function t:method-call-not-a-function() {
    let $m := map { 'length': 3 }
    return $m =?> length()
};

(: ========== XQ4 Let Destructuring ========== :)

declare
    %test:assertEquals(1)
function t:let-seq-destructure-single() {
    let $($x) := (1, 2)
    return $x
};

declare
    %test:assertEquals(1, 2)
function t:let-seq-destructure-basic() {
    let $($x, $y) := (1, 2)
    return ($x, $y)
};

declare
    %test:assertEquals(1, 2, 3)
function t:let-seq-destructure-triple() {
    let $($a, $b, $c) := (1, 2, 3)
    return ($a, $b, $c)
};

declare
    %test:assertEquals(1, 2)
function t:let-array-destructure-basic() {
    let $[$x, $y] := [1, 2]
    return ($x, $y)
};

declare
    %test:assertEquals(1, 2, 3)
function t:let-array-destructure-triple() {
    let $[$a, $b, $c] := [1, 2, 3]
    return ($a, $b, $c)
};

declare
    %test:assertEquals(1, 2)
function t:let-map-destructure-basic() {
    let ${$x, $y} := map { 'x': 1, 'y': 2 }
    return ($x, $y)
};

declare
    %test:assertEquals("hello", 42)
function t:let-map-destructure-mixed() {
    let ${$name, $age} := map { 'name': 'hello', 'age': 42 }
    return ($name, $age)
};

declare
    %test:assertEquals(3, 7)
function t:let-seq-destructure-in-flwor() {
    for $pair in ([1, 2], [3, 4])
    let $[$x, $y] := $pair
    return $x + $y
};

declare
    %test:assertEquals(3)
function t:let-seq-destructure-overflow() {
    (: more items than variables - extras discarded :)
    let $($x) := (3, 4, 5)
    return $x
};

declare
    %test:assertEquals(3, 7)
function t:let-destructure-multiple() {
    let $($a, $b) := (1, 2), $($c, $d) := (3, 4)
    return ($a + $b, $c + $d)
};

(: === Unicode multiplication sign === :)

declare
    %test:assertEquals(6)
function t:unicode-multiply-basic() {
    3 × 2
};

declare
    %test:assertEquals(24)
function t:unicode-multiply-expression() {
    2 × 3 × 4
};

(: === Destructuring with per-variable type annotations === :)

declare
    %test:assertEquals(1, 2)
function t:seq-destructure-typed() {
    let $($x as xs:integer, $y as xs:integer) := (1, 2)
    return ($x, $y)
};

declare
    %test:assertEquals(1, "two")
function t:array-destructure-typed() {
    let $[$x as xs:integer, $y as xs:string] := [1, "two"]
    return ($x, $y)
};

declare
    %test:assertError("XPTY0004")
function t:seq-destructure-typed-error() {
    let $($x as xs:integer, $y as xs:date) := (1, "two")
    return ($x, $y)
};

declare
    %test:assertEquals(1, 2)
function t:array-destructure-overall-typed() {
    let $[$x, $y] as array(xs:integer+) := [1, 2]
    return ($x, $y)
};

(: === Choice types with enum === :)

declare
    %test:assertTrue
function t:choice-type-with-enum() {
    "a" instance of (enum("a","b") | xs:integer)
};

declare
    %test:assertTrue
function t:choice-type-int-in-enum-union() {
    42 instance of (enum("a","b") | xs:integer)
};

(: === String constructor atomization === :)

declare
    %test:assertEquals("There were 10 green bottles")
function t:string-constructor-array-interpolation() {
    let $n := 10
    return ``[There were `{[$n]}` green bottles]``
};

declare
    %test:assertError("FOTY0013")
function t:string-constructor-map-atomization-error() {
    let $n := map{"a":10}
    return ``[There were `{$n}` green bottles]``
};

declare
    %test:assertTrue
function t:string-constructor-entity-not-expanded() {
    (: Entity refs inside string constructors should be literal text, not expanded :)
    ``[There were &lt; 10 green bottles]`` eq "There were &amp;lt; 10 green bottles"
};

(: Test 027/028 - backtick-curly in element content :)
(: SKIPPED: ANTLR 2 lexer lookahead timing — }` after enclosed expression in element :)
(: content is lexed before parser restores inElementContent=true :)

(: Tests 029-034 - entity/char refs not expanded in string constructors inside element constructors :)
(: Entity refs like &lt; should remain as literal text inside ``[...]``, not expanded to < :)
declare
    %test:assertTrue
function t:string-constructor-029-entity-in-element() {
    let $result := <a>{``[There were &lt; 10 green bottles]``}</a>
    let $expected := "There were " || codepoints-to-string(38) || "lt; 10 green bottles"
    return string($result) eq $expected
};

declare
    %test:assertTrue
function t:string-constructor-030-charref-in-element() {
    let $result := <a>{``[There were &#x003C; 10 green bottles]``}</a>
    let $expected := "There were " || codepoints-to-string(38) || "#x003C; 10 green bottles"
    return string($result) eq $expected
};

declare
    %test:assertTrue
function t:string-constructor-031-entity-with-interp() {
    let $result := <a>{``[There were &lt; `{10}` green bottles]``}</a>
    let $expected := "There were " || codepoints-to-string(38) || "lt; 10 green bottles"
    return string($result) eq $expected
};

declare
    %test:assertTrue
function t:string-constructor-032-charref-with-interp() {
    let $result := <a>{``[There were &#x003C; `{10}` green bottles]``}</a>
    let $expected := "There were " || codepoints-to-string(38) || "#x003C; 10 green bottles"
    return string($result) eq $expected
};

declare
    %test:assertTrue
function t:string-constructor-033-entity-after-interp() {
    let $result := <a>{``[There were `{10}` &lt; green bottles]``}</a>
    let $expected := "There were 10 " || codepoints-to-string(38) || "lt; green bottles"
    return string($result) eq $expected
};

declare
    %test:assertTrue
function t:string-constructor-034-charref-after-interp() {
    let $result := <a>{``[There were `{10}` &#x003C; green bottles]``}</a>
    let $expected := "There were 10 " || codepoints-to-string(38) || "#x003C; green bottles"
    return string($result) eq $expected
};

(: Focus functions :)

declare
    %test:assertEquals(2, 3, 4, 5, 6)
function t:focusFunction-filter() {
    (1 to 5) ! (fn { . + 1 })(.)
};

declare
    %test:assertEquals(2, 4, 6)
function t:focusFunction-forEach() {
    for-each((1, 2, 3), fn { . * 2 })
};

declare
    %test:assertEquals(6)
function t:focusFunction-functionKeyword() {
    (function { . * 3 })(2)
};

declare
    %test:assertEquals(3)
function t:focusFunction-withHigherOrder() {
    let $add1 := fn { . + 1 }
    return $add1(2)
};

(: Keyword arguments — parser syntax test only, full dispatch tested with XQ4 functions :)

(: XQ4 annotation literals :)

declare
    %test:assertTrue
function t:annotation-true-false-literals() {
    (: XQ4 allows true(), false(), and negative literals in annotations :)
    let $f := %Q{http://example.com/test}check(true(), false(), -42) function($x) { $x }
    return $f(true())
};

declare
    %test:assertEquals(1)
function t:annotation-negative-numeric-literal() {
    let $f := %Q{http://example.com/test}range(-1, -3.14, -2.5e3) fn { . }
    return $f(1)
};

(: === try/catch/finally (XQ4) === :)

declare
    %test:assertEquals(42)
function t:try-finally-basic() {
    (: XQ4: try with finally clause, no catch :)
    try { 42 } finally {}
};

declare
    %test:assertEquals(42)
function t:try-finally-empty-seq() {
    try { 42 } finally {()}
};

declare
    %test:assertEquals(42)
function t:try-catch-finally() {
    (: XQ4: try with catch and finally :)
    try { 42 } catch * { 97 } finally {()}
};

declare
    %test:assertError("XQTY0153")
function t:try-finally-nonempty-error() {
    (: XQ4: finally must produce empty sequence :)
    try { 42 } finally { 99 }
};

declare
    %test:assertError("FOAR0001")
function t:try-finally-error-replaces-result() {
    (: XQ4: error in finally replaces try result :)
    try { 42 } finally { 10 div 0 }
};

declare
    %test:assertEquals(99)
function t:try-catch-finally-catch-fires() {
    (: XQ4: catch fires, finally runs with empty result :)
    try { 10 div 0 }
    catch err:FOAR0001 { 99 }
    finally {()}
};

declare
    %test:assertError("FOAR0001")
function t:try-finally-error-not-caught-by-same() {
    (: XQ4: finally error is NOT caught by same try/catch :)
    try { 42 }
    catch err:FOAR0001 { "wrong" }
    finally { 10 div 0 }
};

(: ======================== XQ4 switch expression ======================== :)

declare
    %test:assertEquals("Oink")
function t:switch-sequence-case-operand() {
    (: XQ4: case operand may be a sequence; match if any item equals comparand :)
    let $in := 2
    return switch ($in)
        case 1 return "Moo"
        case 5 return "Meow"
        case 3 return "Quack"
        case ($in to 4) return "Oink"
        default return "Baa"
};

declare
    %test:assertEquals("Oink")
function t:switch-braced-syntax() {
    (: XQ4: braced switch syntax :)
    let $in := 2
    return switch ($in) {
        case 1 return "Moo"
        case 5 return "Meow"
        case ($in to 4) return "Oink"
        default return "Baa"
    }
};

declare
    %test:assertEquals("Meow")
function t:switch-boolean-mode-no-braces() {
    (: XQ4: omitted comparand, without braces :)
    let $animal := "Cat"
    return switch ()
        case $animal eq "Cow" return "Moo"
        case $animal eq "Cat" return "Meow"
        case $animal eq "Duck" return "Quack"
        default return "What's that odd noise?"
};

(: XQ4 focus constructors :)

declare
    %test:assertEquals(42)
function t:focus-constructor-integer() {
    (: XQ4 focus constructor: xs:type() with context item :)
    '42' ! xs:integer()
};

declare
    %test:assertEquals(3.14)
function t:focus-constructor-double() {
    '3.14' ! xs:double()
};

declare
    %test:assertEquals("Woof")
function t:switch-nan-matches-nan() {
    (: XQ3: NaN matches NaN in switch - reproduces switch-011 :)
    let $in := xs:double('NaN')
    return
    <out>{ switch ($in)
        case 42 return "Moo"
        case <a>42</a> return "Meow"
        case 42e0 return "Quack"
        case "42e0" return "Oink"
        case xs:float('NaN') return "Woof"
        default return "Expletive deleted" }</out>/string()
};
