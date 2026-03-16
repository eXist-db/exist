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
    string(unix-dateTime(0))
};

declare
    %test:assertEquals("1970-01-01T00:00:01Z")
function t:unixDateTime-oneSecond() {
    string(unix-dateTime(1000))
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

(: ordered maps :)

declare
    %test:assertEquals("a", "b", "c")
function t:ordered-map-keys() {
    map:keys(map { "a": 1, "b": 2, "c": 3 })
};

declare
    %test:assertEquals("x", "y", "z")
function t:ordered-map-literal-order() {
    let $m := map { "x": 10, "y": 20, "z": 30 }
    return map:keys($m)
};

declare
    %test:assertEquals("a", "b", "c", "d")
function t:ordered-map-put-appends() {
    let $m := map { "a": 1, "b": 2, "c": 3 }
    let $m2 := map:put($m, "d", 4)
    return map:keys($m2)
};

declare
    %test:assertEquals("a", "b", "c")
function t:ordered-map-put-existing-keeps-position() {
    let $m := map { "a": 1, "b": 2, "c": 3 }
    let $m2 := map:put($m, "b", 99)
    return map:keys($m2)
};

declare
    %test:assertEquals("a", "c")
function t:ordered-map-remove-preserves-order() {
    let $m := map { "a": 1, "b": 2, "c": 3 }
    let $m2 := map:remove($m, "b")
    return map:keys($m2)
};

declare
    %test:assertEquals("a", "b", "c", "d")
function t:ordered-map-merge-preserves-order() {
    let $m1 := map { "a": 1, "b": 2 }
    let $m2 := map { "c": 3, "d": 4 }
    return map:keys(map:merge(($m1, $m2)))
};

declare
    %test:assertEquals("a", "b", "c")
function t:ordered-map-merge-existing-keeps-position() {
    let $m1 := map { "a": 1, "b": 2 }
    let $m2 := map { "b": 99, "c": 3 }
    return map:keys(map:merge(($m1, $m2)))
};

declare
    %test:assertEquals(1, 2, 3)
function t:ordered-map-for-each-preserves-order() {
    let $m := map { "a": 1, "b": 2, "c": 3 }
    return map:for-each($m, function($k, $v) { $v })
};

declare
    %test:assertEquals("x", "s", "a")
function t:ordered-map-mixed-types() {
    let $m := map { "x": 0, "s": 0, "a": 0 }
    return map:keys($m)
};

declare
    %test:assertEquals(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
function t:ordered-map-merge-entries() {
    map:keys(map:merge(for $n in 1 to 10 return map:entry($n, $n+1)))
};

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

declare
    %test:assertEquals("a", "b", "c")
function t:bare-map-keys-ordered() {
    map:keys({"a": 1, "b": 2, "c": 3})
};

declare
    %test:assertEquals(2)
function t:bare-map-after-return() {
    let $m := {1: 2}
    return $m(1)
};

(: ========== map:empty ========== :)

declare
    %test:assertTrue
function t:map-empty-true() {
    map:empty(map {})
};

declare
    %test:assertFalse
function t:map-empty-false() {
    map:empty(map { "a": 1 })
};

declare
    %test:assertFalse
function t:map-empty-with-empty-value() {
    map:empty(map { 1: () })
};

(: ========== map:items ========== :)

declare
    %test:assertEquals("yes", "no")
function t:map-items-basic() {
    map:items(map { 1: "yes", 2: "no" })
};

declare
    %test:assertEquals("red", "green", "blue", "yellow")
function t:map-items-multi-value() {
    map:items(map { 1: ("red", "green"), 2: ("blue", "yellow"), 3: () })
};

declare
    %test:assertEmpty
function t:map-items-empty-map() {
    map:items(map {})
};

(: ========== map:entries ========== :)

declare
    %test:assertEquals(2)
function t:map-entries-count() {
    count(map:entries(map { 1: "yes", 0: "no" }))
};

declare
    %test:assertTrue
function t:map-entries-are-single-entry-maps() {
    let $entries := map:entries(map { "a": 1, "b": 2 })
    return every $e in $entries satisfies (map:size($e) eq 1)
};

declare
    %test:assertEquals("a", "b", "c")
function t:map-entries-preserve-order() {
    let $m := map { "a": 1, "b": 2, "c": 3 }
    return map:entries($m) ! map:keys(.)
};

(: ========== map:keys-where ========== :)

declare
    %test:assertEquals(2, 3)
function t:map-keys-where-basic() {
    let $numbers := map { 0: "zero", 1: "one", 2: "two", 3: "three" }
    return map:keys-where($numbers, function($key, $value) { $value = ("two", "three") })
};

declare
    %test:assertEquals(3, 4)
function t:map-keys-where-square() {
    let $square := map:merge((1 to 5) ! map:entry(., . * .))
    return map:keys-where($square, function($key, $value) { $value > 5 and $value < 20 })
};

declare
    %test:assertEmpty
function t:map-keys-where-none-match() {
    map:keys-where(map { 1: "a", 2: "b" }, function($k, $v) { $k > 10 })
};

(: ========== map:filter ========== :)

declare
    %test:assertEquals(2)
function t:map-filter-by-key() {
    map:size(map:filter(
        map { 1: "Sunday", 2: "Monday", 3: "Tuesday", 4: "Wednesday",
              5: "Thursday", 6: "Friday", 7: "Saturday" },
        function($k, $v) { $k = (1, 7) }
    ))
};

declare
    %test:assertEquals(2)
function t:map-filter-by-value() {
    map:size(map:filter(
        map { 1: "Sunday", 2: "Monday", 3: "Tuesday", 4: "Wednesday",
              5: "Thursday", 6: "Friday", 7: "Saturday" },
        function($k, $v) { $v = ("Saturday", "Sunday") }
    ))
};

declare
    %test:assertEquals(0)
function t:map-filter-none() {
    map:size(map:filter(map { 1: "a", 2: "b" }, function($k, $v) { false() }))
};

declare
    %test:assertEquals("a", "c")
function t:map-filter-preserves-order() {
    let $m := map { "a": 1, "b": 2, "c": 3 }
    return map:keys(map:filter($m, function($k, $v) { $v != 2 }))
};

(: ========== map:build ========== :)

declare
    %test:assertEquals(0)
function t:map-build-empty-input() {
    map:size(map:build(()))
};

declare
    %test:assertEquals(5)
function t:map-build-identity() {
    map:size(map:build(1 to 5))
};

declare
    %test:assertEquals(3)
function t:map-build-with-key-fn() {
    (: 1 mod 3 = 1, 2 mod 3 = 2, 3 mod 3 = 0, etc. — 3 distinct keys :)
    map:size(map:build(1 to 10, function($x) { $x mod 3 }))
};

declare
    %test:assertEquals("one", "two", "three")
function t:map-build-with-value-fn() {
    let $m := map:build(1 to 3, (), function($x) { ("one", "two", "three")[$x] })
    return ($m(1), $m(2), $m(3))
};

declare
    %test:assertEquals(3, 6, 9)
function t:map-build-combine-duplicates() {
    (: Default behavior: duplicate keys combine values :)
    let $m := map:build(1 to 10, function($x) { $x mod 3 })
    return $m(0)
};

declare
    %test:assertEquals(12, 15, 6)
function t:map-build-with-custom-duplicates-fn() {
    let $m := map:build(
        ("apple", "apricot", "banana", "blueberry", "cherry"),
        function($s) { substring($s, 1, 1) },
        string-length#1,
        map { "duplicates": function($a, $b) { $a + $b } }
    )
    return ($m("a"), $m("b"), $m("c"))
};

declare
    %test:assertEquals("Wang", "Liu", "Zhao")
function t:map-build-keys-in-order() {
    let $m := map:build(
        ("Wang", "Liu", "Zhao"),
        function($name) { $name },
        function($name) { $name }
    )
    return map:keys($m)
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

(: Keyword arguments :)

declare
    %test:assertEquals("c", "d", "e")
function t:keywordArgs-slice-start() {
    slice(("a", "b", "c", "d", "e"), start := 3)
};

declare
    %test:assertEquals("b", "c", "d")
function t:keywordArgs-slice-startEnd() {
    slice(("a", "b", "c", "d", "e"), start := 2, end := 4)
};

declare
    %test:assertEmpty
function t:keywordArgs-slice-empty() {
    slice((), start := 1)
};

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

(: fn:function-annotations tests :)

declare
    %test:assertEmpty
function t:function-annotations-builtin() {
    (: Built-in functions have no annotations :)
    fn:function-annotations(true#0)
};

declare
    %test:assertTrue
function t:function-annotations-exists() {
    (: fn:function-annotations#1 exists :)
    exists(fn:function-annotations#1)
};

declare
    %test:assertEquals(1)
function t:function-annotations-private() {
    (: Test %private annotation on a user function :)
    let $anns := fn:function-annotations(t:annotated-private#1)
    return count($anns)
};

declare %private function t:annotated-private($x) { $x + 1 };

declare
    %test:assertEquals(1)
function t:function-annotations-inline() {
    (: Annotations on inline function expressions :)
    let $f := %local:color("red") function ($c) { $c + 1 }
    return count(fn:function-annotations($f))
};

declare
    %test:assertEquals(1)
function t:function-annotations-declared() {
    (: Annotations on user-declared function ref :)
    count(fn:function-annotations(t:annotated-private#1))
};

(: fn:function-identity tests :)

declare
    %test:assertTrue
function t:function-identity-type() {
    (: Returns a string :)
    fn:function-identity(abs#1) instance of xs:string
};

declare
    %test:assertTrue
function t:function-identity-consistent() {
    (: Same function item returns same identity :)
    fn:function-identity(abs#1) eq fn:function-identity(abs#1)
};

declare
    %test:assertFalse
function t:function-identity-different() {
    (: Different functions return different identities :)
    fn:function-identity(abs#1) eq fn:function-identity(round#1)
};

declare
    %test:assertTrue
function t:function-identity-user-func() {
    (: User function identity is consistent :)
    fn:function-identity(t:annotated-private#1) eq fn:function-identity(t:annotated-private#1)
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

(: ==================== XQ4 No-Namespace Function Override (PR2200) ==================== :)

declare
    %test:assertEquals(8)
function t:no-namespace-function-override() {
    util:eval('
        xquery version "4.0";
        declare function f($x as xs:integer) as xs:integer { $x + 3 };
        f(5)
    ')
};

declare
    %test:assertEquals(105)
function t:no-namespace-override-builtin() {
    util:eval('
        xquery version "4.0";
        declare function abs($x as xs:integer) as xs:integer { $x + 100 };
        abs(5)
    ')
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
