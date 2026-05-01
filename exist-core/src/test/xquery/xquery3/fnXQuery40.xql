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
xquery version "3.1";

(:~
 : Tests for XQuery 4.0 functions implemented in eXist-db.
 :)
module namespace t = "http://exist-db.org/xquery/test/fn-xquery40";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: fn:foot :)

declare
    %test:assertEquals(5)
function t:foot-sequence() {
    foot(1 to 5)
};

declare
    %test:assertEmpty
function t:foot-empty() {
    foot(())
};

declare
    %test:assertEquals("c")
function t:foot-string-sequence() {
    foot(("a", "b", "c"))
};

(: fn:trunk :)

declare
    %test:assertEquals(1, 2, 3, 4)
function t:trunk-sequence() {
    trunk(1 to 5)
};

declare
    %test:assertEmpty
function t:trunk-empty() {
    trunk(())
};

declare
    %test:assertEmpty
function t:trunk-single() {
    trunk("a")
};

declare
    %test:assertEquals("a", "b")
function t:trunk-string-sequence() {
    trunk(("a", "b", "c"))
};

(: fn:identity :)

declare
    %test:assertEquals(0)
function t:identity-zero() {
    identity(0)
};

declare
    %test:assertEmpty
function t:identity-empty() {
    identity(())
};

declare
    %test:assertEquals(1, 2, 3)
function t:identity-sequence() {
    identity((1, 2, 3))
};

(: fn:void :)

declare
    %test:assertEmpty
function t:void-value() {
    void(1 to 1000000)
};

declare
    %test:assertEmpty
function t:void-no-args() {
    void()
};

(: fn:is-NaN :)

declare
    %test:assertFalse
function t:isNaN-integer() {
    is-NaN(23)
};

declare
    %test:assertFalse
function t:isNaN-string() {
    is-NaN("NaN")
};

declare
    %test:assertTrue
function t:isNaN-number-invalid() {
    is-NaN(number("twenty-three"))
};

(: fn:characters :)

declare
    %test:assertEquals("T", "h", "e")
function t:characters-basic() {
    characters("The")
};

declare
    %test:assertEmpty
function t:characters-empty-string() {
    characters("")
};

declare
    %test:assertEmpty
function t:characters-empty-sequence() {
    characters(())
};

(: fn:replicate :)

declare
    %test:assertEquals(0, 0, 0)
function t:replicate-basic() {
    replicate(0, 3)
};

declare
    %test:assertEmpty
function t:replicate-zero-count() {
    replicate("A", 0)
};

declare
    %test:assertEmpty
function t:replicate-empty-input() {
    replicate((), 5)
};

(: fn:insert-separator :)

declare
    %test:assertEquals(1, "|", 2, "|", 3)
function t:insertSeparator-basic() {
    insert-separator(1 to 3, "|")
};

declare
    %test:assertEmpty
function t:insertSeparator-empty() {
    insert-separator((), "|")
};

declare
    %test:assertEquals("A")
function t:insertSeparator-single() {
    insert-separator("A", "|")
};

(: fn:all-equal :)

declare
    %test:assertFalse
function t:allEqual-different() {
    all-equal((1, 2, 3))
};

declare
    %test:assertTrue
function t:allEqual-same() {
    all-equal((1, 1, 1))
};

declare
    %test:assertFalse
function t:allEqual-mixed-numeric-types() {
    (: XQ4: decimal 1.2 and double 1.2 differ in exact mathematical value :)
    all-equal((xs:decimal('1.2'), xs:double('1.2')))
};

declare
    %test:assertTrue
function t:allEqual-empty() {
    all-equal(())
};

declare
    %test:assertTrue
function t:allEqual-single() {
    all-equal("one")
};

(: fn:all-different :)

declare
    %test:assertTrue
function t:allDifferent-different() {
    all-different((1, 2, 3))
};

declare
    %test:assertFalse
function t:allDifferent-duplicates() {
    all-different((1, 2, 1))
};

declare
    %test:assertTrue
function t:allDifferent-empty() {
    all-different(())
};

(: fn:items-at :)

declare
    %test:assertEquals(14)
function t:itemsAt-single() {
    items-at(11 to 20, 4)
};

declare
    %test:assertEquals(17, 13)
function t:itemsAt-reorder() {
    items-at(11 to 20, (7, 3))
};

declare
    %test:assertEmpty
function t:itemsAt-empty-input() {
    items-at((), 832)
};

(: fn:index-where :)

declare
    %test:assertEquals(2, 3)
function t:indexWhere-basic() {
    index-where((0, 4, 9), boolean#1)
};

declare
    %test:assertEmpty
function t:indexWhere-empty() {
    index-where((), boolean#1)
};

(: fn:take-while :)

declare
    %test:assertEquals(10, 11, 12)
function t:takeWhile-basic() {
    take-while(10 to 20, function($x) { $x le 12 })
};

declare
    %test:assertEmpty
function t:takeWhile-empty() {
    take-while((), boolean#1)
};

(: fn:slice :)

declare
    %test:assertEquals("b", "c", "d")
function t:slice-startEnd() {
    let $in := ("a", "b", "c", "d", "e")
    return slice($in, 2, 4)
};

declare
    %test:assertEquals("e")
function t:slice-negative-start() {
    let $in := ("a", "b", "c", "d", "e")
    return slice($in, -1)
};

(: fn:duplicate-values :)

declare
    %test:assertEquals(1)
function t:duplicateValues-basic() {
    duplicate-values((1, 2, 3, 1))
};

declare
    %test:assertEmpty
function t:duplicateValues-noDups() {
    duplicate-values((1, 2, 3))
};

(: fn:hash :)

declare
    %test:assertEquals("900150983CD24FB0D6963F7D28E17F72")
function t:hash-md5() {
    string(hash("abc"))
};

declare
    %test:assertEmpty
function t:hash-empty() {
    hash(())
};

(: fn:while-do :)

declare
    %test:assertEquals(16)
function t:whileDo-doubling() {
    while-do(1, function($x) { $x lt 10 }, function($x) { $x * 2 })
};

(: fn:do-until :)

declare
    %test:assertEquals(16)
function t:doUntil-doubling() {
    do-until(1, function($x) { $x * 2 }, function($x) { $x ge 10 })
};

(: fn:sort-with :)

declare
    %test:assertEquals(1, 1, 3, 4, 5)
function t:sortWith-ascending() {
    sort-with((3, 1, 4, 1, 5), function($a, $b) { compare(string($a), string($b)) })
};

(: fn:op :)

declare
    %test:assertEquals(7)
function t:op-add() {
    op("+")(3, 4)
};

declare
    %test:assertTrue
function t:op-lt() {
    op("lt")(3, 4)
};

declare
    %test:assertEquals(7)
function t:op-subtract() {
    op("-")(10, 3)
};

(: fn:char :)

declare
    %test:assertEquals("A")
function t:char-codepoint() {
    char(65)
};

declare
    %test:assertEquals("&amp;")
function t:char-name() {
    char("amp")
};

(: fn:atomic-equal :)

declare
    %test:assertTrue
function t:atomicEqual-same() {
    atomic-equal(1, 1)
};

declare
    %test:assertFalse
function t:atomicEqual-different-type() {
    atomic-equal("1", 1)
};

declare
    %test:assertTrue
function t:atomicEqual-nan() {
    atomic-equal(number("NaN"), number("NaN"))
};

(: fn:expanded-QName :)

declare
    %test:assertEquals("Q{}local")
function t:expandedQName-noNS() {
    expanded-QName(QName("", "local"))
};

declare
    %test:assertEquals("Q{http://example.com}test")
function t:expandedQName-withNS() {
    expanded-QName(QName("http://example.com", "test"))
};

(: fn:highest / fn:lowest :)

declare
    %test:assertEquals(5)
function t:highest-basic() {
    highest((3, 1, 5, 2, 4))
};

declare
    %test:assertEquals(1)
function t:lowest-basic() {
    lowest((3, 1, 5, 2, 4))
};

(: fn:partition :)

declare
    %test:assertEquals(3)
function t:partition-basic() {
    count(partition(1 to 6, function($current, $next, $pos) { $pos mod 2 eq 1 }))
};

(: fn:parse-uri :)

declare
    %test:assertEquals("http")
function t:parseUri-scheme() {
    parse-uri("http://example.com/path")?scheme
};

declare
    %test:assertTrue
function t:parseUri-hierarchical() {
    parse-uri("http://example.com/path")?hierarchical
};

declare
    %test:assertEquals("example.com")
function t:parseUri-host() {
    parse-uri("http://example.com/path")?host
};

declare
    %test:assertEquals("/path")
function t:parseUri-path() {
    parse-uri("http://example.com/path")?path
};

declare
    %test:assertFalse
function t:parseUri-opaque() {
    parse-uri("mailto:user@example.com")?hierarchical
};

(: fn:scan-left :)

declare
    %test:assertEquals(3)
function t:scanLeft-count() {
    count(scan-left(1 to 2, 0, function($acc, $item) { $acc + $item }))
};

declare
    %test:assertEquals(0, 1, 3)
function t:scanLeft-sums() {
    for $arr in scan-left(1 to 2, 0, function($acc, $item) { $acc + $item })
    return $arr?1
};

(: fn:scan-right :)

declare
    %test:assertEquals(3)
function t:scanRight-count() {
    count(scan-right(1 to 2, 0, function($item, $acc) { $acc + $item }))
};

declare
    %test:assertEquals(3, 2, 0)
function t:scanRight-sums() {
    for $arr in scan-right(1 to 2, 0, function($item, $acc) { $acc + $item })
    return $arr?1
};

(: fn:build-uri :)

declare
    %test:assertEquals("https://qt4cg.org/specifications/index.html")
function t:buildUri-basic() {
    build-uri(map {
        "scheme": "https",
        "host": "qt4cg.org",
        "path": "/specifications/index.html"
    })
};

(: fn:every :)

declare
    %test:assertTrue
function t:every-all-true() {
    every((1, 2, 3), function($x) { $x gt 0 })
};

declare
    %test:assertFalse
function t:every-one-false() {
    every((1, -1, 3), function($x) { $x gt 0 })
};

declare
    %test:assertTrue
function t:every-empty() {
    every((), function($x) { $x gt 0 })
};

declare
    %test:assertTrue
function t:every-1arg-truthy() {
    every((1, true(), "yes"))
};

declare
    %test:assertFalse
function t:every-1arg-falsy() {
    every((1, 0, "yes"))
};

(: fn:some :)

declare
    %test:assertTrue
function t:some-one-true() {
    some((-1, 0, 3), function($x) { $x gt 0 })
};

declare
    %test:assertFalse
function t:some-none-true() {
    some((-1, -2, -3), function($x) { $x gt 0 })
};

declare
    %test:assertFalse
function t:some-empty() {
    some((), function($x) { $x gt 0 })
};

declare
    %test:assertTrue
function t:some-1arg-truthy() {
    some((0, false(), 1))
};

(: fn:sort-by :)

declare
    %test:assertEquals("a", "bb", "ccc")
function t:sortBy-stringLength() {
    sort-by(("ccc", "a", "bb"), map { "key": string-length#1 })
};

declare
    %test:assertEquals("ccc", "bb", "a")
function t:sortBy-descending() {
    sort-by(("a", "bb", "ccc"), map { "key": string-length#1, "order": "descending" })
};

declare
    %test:assertEmpty
function t:sortBy-empty() {
    sort-by((), map { "key": string-length#1 })
};

(: fn:contains-subsequence :)

declare
    %test:assertTrue
function t:containsSubseq-present() {
    contains-subsequence((1, 2, 3, 4, 5), (2, 3, 4))
};

declare
    %test:assertFalse
function t:containsSubseq-absent() {
    contains-subsequence((1, 2, 3, 4, 5), (2, 4))
};

declare
    %test:assertTrue
function t:containsSubseq-emptySubseq() {
    contains-subsequence((1, 2, 3), ())
};

(: fn:starts-with-subsequence :)

declare
    %test:assertTrue
function t:startsWithSubseq-true() {
    starts-with-subsequence((1, 2, 3, 4), (1, 2))
};

declare
    %test:assertFalse
function t:startsWithSubseq-false() {
    starts-with-subsequence((1, 2, 3, 4), (2, 3))
};

declare
    %test:assertTrue
function t:startsWithSubseq-empty() {
    starts-with-subsequence((1, 2, 3), ())
};

(: fn:ends-with-subsequence :)

declare
    %test:assertTrue
function t:endsWithSubseq-true() {
    ends-with-subsequence((1, 2, 3, 4), (3, 4))
};

declare
    %test:assertFalse
function t:endsWithSubseq-false() {
    ends-with-subsequence((1, 2, 3, 4), (2, 3))
};

(: fn:decode-from-uri :)

declare
    %test:assertEquals("hello world")
function t:decodeFromUri-plus() {
    decode-from-uri("hello+world")
};

declare
    %test:assertEquals("a/b")
function t:decodeFromUri-percent() {
    decode-from-uri("a%2Fb")
};

declare
    %test:assertEquals("")
function t:decodeFromUri-empty() {
    decode-from-uri(())
};

(: fn:parse-integer :)

declare
    %test:assertEquals(42)
function t:parseInteger-decimal() {
    parse-integer("42")
};

declare
    %test:assertEquals(255)
function t:parseInteger-hex() {
    parse-integer("FF", 16)
};

declare
    %test:assertEquals(7)
function t:parseInteger-binary() {
    parse-integer("111", 2)
};

declare
    %test:assertEquals(1000)
function t:parseInteger-underscores() {
    parse-integer("1_000")
};

declare
    %test:assertEmpty
function t:parseInteger-empty() {
    parse-integer(())
};

(: fn:divide-decimals :)

declare
    %test:assertEquals(3)
function t:divideDecimals-quotient() {
    divide-decimals(10, 3)?quotient
};

declare
    %test:assertEquals(1)
function t:divideDecimals-remainder() {
    divide-decimals(10, 3)?remainder
};

declare
    %test:assertEquals(3.3)
function t:divideDecimals-precision() {
    divide-decimals(10, 3, 1)?quotient
};

(: fn:distinct-ordered-nodes :)

declare
    %test:assertEquals(3)
function t:distinctOrderedNodes-basic() {
    let $doc := <root><a/><b/><c/></root>
    return count(distinct-ordered-nodes(($doc/a, $doc/c, $doc/b, $doc/a)))
};

(: fn:siblings :)

declare
    %test:assertEquals(3)
function t:siblings-count() {
    let $doc := <root><a/><b/><c/></root>
    return count(siblings($doc/b))
};

declare
    %test:assertEmpty
function t:siblings-empty() {
    siblings(())
};

(: fn:type-of :)

declare
    %test:assertEquals("xs:integer")
function t:typeOf-integer() {
    type-of(42)
};

declare
    %test:assertEquals("xs:string")
function t:typeOf-string() {
    type-of("hello")
};

declare
    %test:assertEquals("empty-sequence()")
function t:typeOf-empty() {
    type-of(())
};

declare
    %test:assertEquals("element()")
function t:typeOf-element() {
    type-of(<foo/>)
};

declare
    %test:assertEquals("map(*)")
function t:typeOf-map() {
    type-of(map { "a": 1 })
};

(: fn:unix-dateTime :)

declare
    %test:assertEquals("1970-01-01T00:00:00Z")
function t:unixDateTime-epoch() {
    string(unix-dateTime(xs:nonNegativeInteger(0)))
};

declare
    %test:assertEquals("1970-01-01T00:00:01Z")
function t:unixDateTime-oneSecond() {
    string(unix-dateTime(xs:nonNegativeInteger(1000)))
};

(: fn:message :)

declare
    %test:assertEmpty
function t:message-basic() {
    message("test output")
};

declare
    %test:assertEmpty
function t:message-withLabel() {
    message("test output", "DEBUG")
};

(: fn:parse-QName :)

declare
    %test:assertEmpty
function t:parseQName-empty() {
    parse-QName(())
};

declare
    %test:assertEquals("foo")
function t:parseQName-ncname() {
    local-name-from-QName(parse-QName("foo"))
};

declare
    %test:assertEquals("")
function t:parseQName-ncname-ns() {
    namespace-uri-from-QName(parse-QName("foo"))
};

declare
    %test:assertEquals("local")
function t:parseQName-uriQualified() {
    local-name-from-QName(parse-QName("Q{http://example.com}local"))
};

declare
    %test:assertEquals("http://example.com")
function t:parseQName-uriQualified-ns() {
    namespace-uri-from-QName(parse-QName("Q{http://example.com}local"))
};

declare
    %test:assertEquals("integer")
function t:parseQName-prefixed() {
    local-name-from-QName(parse-QName("xs:integer"))
};

declare
    %test:assertEquals("http://www.w3.org/2001/XMLSchema")
function t:parseQName-prefixed-ns() {
    namespace-uri-from-QName(parse-QName("xs:integer"))
};

(: fn:atomic-type-annotation :)

declare
    %test:assertTrue
function t:atomicTypeAnnotation-integer-name() {
    let $r := atomic-type-annotation(42)
    return $r?name eq xs:QName("xs:integer")
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-integer-isSimple() {
    atomic-type-annotation(42)?is-simple
};

declare
    %test:assertEquals("atomic")
function t:atomicTypeAnnotation-integer-variety() {
    atomic-type-annotation(42)?variety
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-string-name() {
    let $r := atomic-type-annotation("hello")
    return $r?name eq xs:QName("xs:string")
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-boolean-name() {
    let $r := atomic-type-annotation(true())
    return $r?name eq xs:QName("xs:boolean")
};

(: fn:node-type-annotation :)

declare
    %test:assertTrue
function t:nodeTypeAnnotation-element() {
    let $r := node-type-annotation(<x/>)
    return $r?name eq xs:QName("xs:untyped")
};

declare
    %test:assertFalse
function t:nodeTypeAnnotation-element-isSimple() {
    node-type-annotation(<x/>)?is-simple
};

declare
    %test:assertEquals("mixed")
function t:nodeTypeAnnotation-element-variety() {
    node-type-annotation(<x/>)?variety
};

declare
    %test:assertTrue
function t:nodeTypeAnnotation-attribute() {
    let $r := node-type-annotation((<x a="1"/>)/@a)
    return $r?name eq xs:QName("xs:untypedAtomic")
};

declare
    %test:assertTrue
function t:nodeTypeAnnotation-attribute-isSimple() {
    node-type-annotation((<x a="1"/>)/@a)?is-simple
};

declare
    %test:assertEquals("atomic")
function t:nodeTypeAnnotation-attribute-variety() {
    node-type-annotation((<x a="1"/>)/@a)?variety
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-hasBaseType() {
    let $r := atomic-type-annotation(true())
    return map:contains($r, "base-type")
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-hasMatches() {
    let $r := atomic-type-annotation(true())
    return map:contains($r, "matches")
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-hasConstructor() {
    let $r := atomic-type-annotation(true())
    return map:contains($r, "constructor")
};

declare
    %test:assertTrue
function t:nodeTypeAnnotation-element-hasBaseType() {
    let $r := node-type-annotation(<x/>)
    return map:contains($r, "base-type")
};

(: fn:atomic-type-annotation — base-type function :)

declare
    %test:assertTrue
function t:atomicTypeAnnotation-baseType-returns-parent() {
    let $r := atomic-type-annotation(42)
    let $base := $r?base-type()
    return $base?name eq xs:QName("xs:decimal")
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-baseType-chain-to-anyType() {
    (: Walk the chain: integer → decimal → anyAtomicType → anySimpleType → anyType :)
    let $r := atomic-type-annotation(42)
    let $decimal := $r?base-type()
    let $atomic := $decimal?base-type()
    let $simple := $atomic?base-type()
    let $anyType := $simple?base-type()
    return $anyType?name eq xs:QName("xs:anyType")
};

(: fn:atomic-type-annotation — primitive-type function :)

declare
    %test:assertTrue
function t:atomicTypeAnnotation-primitiveType-integer() {
    (: primitive type of xs:integer is xs:decimal :)
    let $r := atomic-type-annotation(42)
    let $prim := $r?primitive-type()
    return $prim?name eq xs:QName("xs:decimal")
};

declare
    %test:assertTrue
function t:atomicTypeAnnotation-primitiveType-string-self() {
    (: primitive type of xs:string is xs:string itself :)
    let $r := atomic-type-annotation("hello")
    let $prim := $r?primitive-type()
    return $prim?name eq xs:QName("xs:string")
};

(: fn:atomic-type-annotation — matches function :)

declare
    %test:assertTrue
function t:atomicTypeAnnotation-matches-true() {
    let $r := atomic-type-annotation(42)
    return $r?matches(xs:integer(99))
};

declare
    %test:assertFalse
function t:atomicTypeAnnotation-matches-false() {
    let $r := atomic-type-annotation(42)
    return $r?matches("not an integer")
};

(: fn:atomic-type-annotation — constructor function :)

declare
    %test:assertEquals(42)
function t:atomicTypeAnnotation-constructor-cast() {
    let $r := atomic-type-annotation(42)
    return $r?constructor("42")
};

(: fn:atomic-type-annotation — variety for special types :)

declare
    %test:assertTrue
function t:atomicTypeAnnotation-anySimpleType-noVariety() {
    (: xs:anySimpleType has no variety :)
    let $r := atomic-type-annotation(42)
    (: Walk to anySimpleType: integer → decimal → anyAtomicType → anySimpleType :)
    let $simple := $r?base-type()?base-type()?base-type()
    return not(map:contains($simple, "variety"))
};

declare
    %test:assertEquals("mixed")
function t:nodeTypeAnnotation-anyType-variety() {
    (: Walk: untyped → anyType :)
    let $r := node-type-annotation(<x/>)
    let $anyType := $r?base-type()
    return $anyType?variety
};

(: fn:civil-timezone :)

declare
    %test:assertEquals("PT1H")
function t:civilTimezone-paris-winter() {
    string(civil-timezone(xs:dateTime("2024-11-05T12:00:00"), "Europe/Paris"))
};

declare
    %test:assertEquals("PT2H")
function t:civilTimezone-paris-summer() {
    string(civil-timezone(xs:dateTime("2024-05-05T12:00:00"), "Europe/Paris"))
};

declare
    %test:assertEquals("PT5H30M")
function t:civilTimezone-india() {
    string(civil-timezone(xs:dateTime("2024-06-15T12:00:00"), "Asia/Kolkata"))
};

declare
    %test:assertEquals("-PT5H")
function t:civilTimezone-peru() {
    string(civil-timezone(xs:dateTime("2024-06-15T12:00:00"), "America/Lima"))
};

declare
    %test:assertError("FODT0004")
function t:civilTimezone-unknown-place() {
    civil-timezone(xs:dateTime("2024-06-15T12:00:00"), "North/Pole")
};

(: fn:format-number with XQ4 map options and char:rendition :)

declare
    %test:assertEquals("12,56")
function t:formatNumber-map-decimalRendition() {
    (: decimal-separator marker is . for picture, rendered as , in output :)
    format-number(12.56, '#0.##', map {
        'decimal-separator': '.:,'
    })
};

declare
    %test:assertEquals("1 234.56")
function t:formatNumber-map-groupingRendition() {
    (: grouping-separator marker is , for picture, but space is rendered :)
    format-number(1234.56, '#,##0.##', map {
        'grouping-separator': ',: '
    })
};

declare
    %test:assertEquals("14pc")
function t:formatNumber-map-percentRendition() {
    (: percent marker is % in picture, but "pc" is rendered :)
    format-number(0.14, '01%', map {
        'percent': '%:pc'
    })
};

declare
    %test:assertEquals("1,234.56")
function t:formatNumber-map-noRendition() {
    (: No rendition — marker used directly in output :)
    format-number(1234.56, '#,##0.##', map {
        'decimal-separator': '.',
        'grouping-separator': ','
    })
};

declare
    %test:assertEquals("1.5EXP2")
function t:formatNumber-map-exponentRendition() {
    (: exponent-separator marker is e for picture, "EXP" is rendered :)
    format-number(150, '0.0e0', map {
        'exponent-separator': 'e:EXP'
    })
};

(: fn:function-annotations :)

declare %private function local:annotated-fn() { 42 };

declare
    %test:assertTrue
function t:functionAnnotations-private() {
    (: %private annotation should be returned :)
    let $anns := function-annotations(local:annotated-fn#0)
    return some $m in $anns satisfies
        map:keys($m) = xs:QName("fn:private")
};

declare
    %test:assertTrue
function t:functionAnnotations-builtin-empty() {
    (: Built-in functions have no annotations :)
    empty(function-annotations(true#0))
};

declare
    %test:assertTrue
function t:functionAnnotations-returns-maps() {
    (: Each annotation is a single-entry map :)
    let $anns := function-annotations(local:annotated-fn#0)
    return every $m in $anns satisfies ($m instance of map(*) and map:size($m) = 1)
};

(: fn:function-identity :)

declare
    %test:assertTrue
function t:functionIdentity-same() {
    (: Same named function returns same identity :)
    function-identity(true#0) eq function-identity(true#0)
};

declare
    %test:assertFalse
function t:functionIdentity-different() {
    (: Different functions return different identities :)
    function-identity(true#0) eq function-identity(false#0)
};

declare
    %test:assertTrue
function t:functionIdentity-isString() {
    (: Returns a string :)
    function-identity(true#0) instance of xs:string
};

declare
    %test:assertTrue
function t:functionIdentity-map-self-equal() {
    (: Same map variable has same identity :)
    let $m := map { "a": 1 }
    return function-identity($m) eq function-identity($m)
};

declare
    %test:assertTrue
function t:functionIdentity-array-self-equal() {
    (: Same array variable has same identity :)
    let $a := [ 1, 2, 3 ]
    return function-identity($a) eq function-identity($a)
};

(: ==================== fn:load-xquery-module content option ==================== :)

declare
    %test:assertEquals("world")
function t:load-xquery-module-content() {
    let $src := "module namespace m = 'http://example.com/test';
                 declare function m:hello() as xs:string { 'world' };"
    let $mod := fn:load-xquery-module('http://example.com/test', map { 'content': $src })
    let $hello := $mod?functions(QName('http://example.com/test', 'hello'))
    return $hello?0()
};
