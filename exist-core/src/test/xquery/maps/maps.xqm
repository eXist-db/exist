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

module namespace mt="http://exist-db.org/xquery/test/maps";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare %private function mt:getMapFixture () {
    map {
        "Su" : "Sunday",
        "Mo" : "Monday",
        "Tu" : "Tuesday",
        "We" : "Wednesday",
        "Th" : "Thursday",
        "Fr" : "Friday",
        "Sa" : "Saturday"
    }
};

declare variable $mt:daysOfWeek := mt:getMapFixture();

declare variable $mt:integerKeys := map {
    1 : "Sunday",
    2 : "Monday",
    3 : "Tuesday",
    4 : "Wednesday",
    5 : "Thursday",
    6 : "Friday",
    7 : "Saturday"
};

declare variable $mt:places := map {
    "Scotland": map {
        "Highlands": map {
            "Inverness": 1,
            "Fort William": 1
        },
        "Lowlands": map { "Glasgow": 1 }
    }
};

declare variable $mt:mapOfSequences := map {0: (), 1 : ("One", "Two") };

declare
    %test:assertEquals("Wednesday")
function mt:createLiteral1() {
    $mt:daysOfWeek("We")
};

declare
    %test:assertEquals("Wednesday")
function mt:createWithIntKeys() {
    let $map := map {
        1 : "Sunday",
        2 : "Monday",
        3 : "Tuesday",
        4 : "Wednesday",
        5 : "Thursday",
        6 : "Friday",
        7 : "Saturday"
    }
    return
        $map(4)
};

declare
    %test:assertError("err:XPST0003")
function mt:draftSyntaxNotAllowed () {
    (: this needs to be evaled, to catch the static compilation error :)
    util:eval('map { 1 := 1 }')
};

declare
    %test:assertEquals(20)
function mt:createWithFunctionVariable() {
    let $fn := function($p) { $p * 2 }
    let $map := map { "callback" : $fn }
    return
        map:get($map, "callback")(10)
};

declare
    %test:assertEquals(2)
function mt:createWithFunctionValue() {
    let $map := map { "callback" : function($p) { $p * 2 } }
    return
        map:get($map, "callback")(1)
};

declare
    %test:assertEquals("Sunday")
function mt:createWithEntry() {
    let $days := ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    let $map := map:merge($days ! map:entry(substring(., 1, 2), .))
    return
        $map("Su")
};

declare
    %test:assertXPath("count($result) = 8")
    %test:assertXPath("8 = $result")
function mt:createFromTwoMaps() {
    let $map := map:merge(($mt:integerKeys, map { 8 : "Caturday" }))
    return
        map:keys($map)
};

declare
    %test:assertEquals(0)
function mt:size-empty() {
    let $m := map {}
    return
      map:size($m)
};

declare
    %test:assertEquals("Good")
function mt:put-empty() {
    let $m := map {}
    return
      map:put($m, "Cats", "Good")("Cats")
};

declare
    %test:assertEquals("Bad")
function mt:put-single() {
    let $m := map {
        "Cats" : "Good"
    }
    return
      map:put($m, "Dogs", "Bad")("Dogs")
};

declare
    %test:assertEquals("Duck")
function mt:put() {
    let $m := map {
        "Cats" : "Good",
        "Dogs" : "Bad",
        1 : "Chicken"
    }
    return
      map:put($m, 2, "Duck")(2)
};

declare
    %test:assertEquals(4)
function mt:size() {
    let $m := map {
        "Cats": "Good",
        "Dogs": "Bad",
        1: "Chicken",
        2: "Duck"
    }
    return
        map:size($m)
};

declare
    %test:assertEquals("Sunday", "Tuesday", "Thursday", "Saturday")
function mt:for-each() {
    map:for-each($mt:integerKeys, function($key, $value) {
        if ($key mod 2) then ($value) else ()
    })
};

declare
    %test:assertEquals(3)
function mt:for-each2() {
    let $nm := map:merge(
        map:for-each(
            map { "a": 1, "b": 2 },
            function($k, $v) { map:entry($k, $v + 1) }
        )
    )

    return
        $nm?b
};

declare
    %test:assertEquals("Sunday")
function mt:createWithSingleKey() {
    let $map := map { "Su": "Sunday" }
    return
        $map("Su")
};

(:~
 : TODO(AR) implicit behaviour of map:merge according to XQ3.1 specification should be use-first not use-last
:)
declare
    %test:assertEquals("Saturday", "Caturday")
function mt:merge-duplicate-keys-use-last-implicit-1() {
    let $specialWeek := map:merge(($mt:integerKeys, map { 7 : "Caturday" }))
    return
        ($mt:integerKeys(7), $specialWeek(7))
};

(:~
 : TODO(AR) implicit behaviour of map:merge according to XQ3.1 specification should be use-first not use-last
:)
declare
    %test:assertEquals("Saturday", "Saturday")
function mt:merge-duplicate-keys-use-last-implicit-2() {
    let $specialWeek := map:merge((map { 7 : "Caturday" }, $mt:integerKeys))
    return
        ($mt:integerKeys(7), $specialWeek(7))
};

declare
    %test:assertEquals("Saturday", "Saturday")
function mt:merge-duplicate-keys-use-first-explicit-1() {
    let $specialWeek := map:merge(($mt:integerKeys, map { 7 : "Caturday" }), map { "duplicates": "use-first" })
    return
        ($mt:integerKeys(7), $specialWeek(7))
};

declare
    %test:assertEquals("Saturday", "Caturday")
function mt:merge-duplicate-keys-use-first-explicit-2() {
    let $specialWeek := map:merge((map { 7 : "Caturday" }, $mt:integerKeys), map { "duplicates": "use-first" })
    return
        ($mt:integerKeys(7), $specialWeek(7))
};

declare
    %test:assertEquals("Saturday", "Caturday")
function mt:merge-duplicate-keys-use-last-explicit-1() {
    let $specialWeek := map:merge(($mt:integerKeys, map { 7 : "Caturday" }), map { "duplicates": "use-last" })
    return
        ($mt:integerKeys(7), $specialWeek(7))
};

declare
    %test:assertEquals("Saturday", "Saturday")
function mt:merge-duplicate-keys-use-last-explicit-2() {
    let $specialWeek := map:merge((map { 7 : "Caturday" }, $mt:integerKeys), map { "duplicates": "use-last" })
    return
        ($mt:integerKeys(7), $specialWeek(7))
};

declare
    %test:assertError("err:FOJS0003")
function mt:merge-duplicate-keys-reject-has-duplicates() {
    let $specialWeek := map:merge((map { 7 : "Caturday" }, $mt:integerKeys), map { "duplicates": "reject" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Saturday", "Saturday", "Caturday")
function mt:merge-duplicate-keys-reject-no-duplicates() {
    let $specialWeek := map:merge((map { 8 : "Caturday" }, $mt:integerKeys), map { "duplicates": "reject" })
    return
        ($mt:integerKeys(7), $specialWeek(7), $specialWeek(8))
};

declare
    %test:assertEquals("Caturday","Maturday","Saturday")
function mt:merge-duplicate-keys-combine-has-duplicates-sequence() {
    let $specialWeek := map:merge((map { 7 : ("Caturday","Maturday") }, $mt:integerKeys), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Saturday","Caturday","Maturday")
function mt:merge-duplicate-keys-combine-has-duplicates-sequence-order() {
    let $specialWeek := map:merge(($mt:integerKeys, map { 7 : ("Caturday","Maturday") }), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Saturday","Caturday","Maturday", "Caturday", "Zaturday")
function mt:merge-duplicate-keys-combine-3-has-duplicates-sequence-order() {
    let $specialWeek := map:merge(($mt:integerKeys, map { 7 : ("Caturday","Maturday") }, map { 7 : ("Caturday","Zaturday") }), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Caturday","Saturday")
function mt:merge-duplicate-keys-combine-has-duplicates-atomic() {
    let $specialWeek := map:merge((map { 7 : ("Caturday") }, $mt:integerKeys), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Caturday","Saturday", "Maturday")
function mt:merge-duplicate-keys-combine-has-duplicates-three() {
    let $specialWeek := map:merge((map { 7 : ("Caturday") }, $mt:integerKeys, map { 7: ("Maturday")}), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Saturday", "Caturday", "Maturday", "Waturday")
function mt:merge-duplicate-keys-combine-has-duplicates-four() {
    let $specialWeek := map:merge((map { 7 : () }, $mt:integerKeys, map { 7: ("Caturday", "Maturday", "Waturday")}), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEquals("Caturday","Saturday")
function mt:merge-duplicate-keys-combine-has-duplicates-empty-third() {
    let $specialWeek := map:merge((map { 7 : ("Caturday") }, $mt:integerKeys, map { 7: ()}), map { "duplicates": "combine" })
    return
        ($specialWeek(7))
};

declare
    %test:assertEmpty
function mt:mapEmptyValue() {
    let $map := $mt:mapOfSequences
    return
        $map(0)
};

declare
    %test:assertEquals("One", "Two")
function mt:mapSequenceValue() {
    let $map := $mt:mapOfSequences
    return
        $map(1)
};

declare
    %test:assertEquals("Heinz", "Roland", "Uschi", "Verona")
function mt:orderBzKeyTypeDate () {
    let $map := map {
        xs:date("1975-03-19"): "Uschi",
        xs:date("1980-01-22"): "Verona",
        xs:date("1960-06-14"): "Heinz",
        xs:date("1963-10-21"): "Roland"
    }

    for $key in map:keys($map)
    let $name := $map($key)
    order by $name ascending
    return
        $name
};

declare
    %test:assertEquals(0, "One")
function mt:mixedKeyTypes() {
    let $map := map{ "Zero": 0, 1: "One" }
    return
        ($map("Zero"), $map(1))
};

declare
    %test:assertTrue
function mt:containsOnEmptyValue() {
    let $map := map{ 0: () }
    return
        map:contains($map, 0)
};

declare
    %test:assertEquals("Fr", "Mo", "Sa", "Su", "Th", "Tu", "We")
function mt:keysInFor() {
    for $day in map:keys(mt:getMapFixture())
    order by $day ascending
    return
        $day
};

declare
    %test:assertEquals("Su")
function mt:keysOnMapWithSingleKey() {
    let $map := map:entry("Su", "Sunday")
    return
        map:keys($map)
};

declare
    %test:assertFalse
function mt:remove() {
    let $map := mt:getMapFixture()
    return
        map:contains(map:remove($map, "Tu"), "Tu")
};

declare
    %test:assertEquals("Fr", "Mo", "Sa", "Su", "Th")
function mt:remove2() {
    let $map := mt:getMapFixture()
    for $day in map:keys(map:remove(map:remove($map, "We"), "Tu"))
    order by $day
    return
        $day
};


declare
    %test:assertEmpty
function mt:removeSingleKey() {
    let $empty := map:remove(map { "Su": "Sunday" }, "Su")
    return
        map:keys($empty)
};

declare
    %test:assertEquals("Su")
function mt:removeSingleNonExistentKey() {
    let $map := map:remove(map { "Su": "Sunday" }, "Xx")
    return
        map:keys($map)
};

declare
    %test:assertEquals("Sa")
function mt:removeSequenceOfStringKeys() {
    mt:getMapFixture()
        => map:remove(("Mo", "Tu", "We", "Th", "Fr", "Su"))
        => map:keys()
};

declare
    %test:assertEquals("Sa")
function mt:removeSequenceOfStringKeysWithNonExistent() {
    mt:getMapFixture()
        => map:remove(("Mo", "Tu", "We", "Th", "Fr", "Su", "Xx"))
        => map:keys()
};

declare
    %test:assertEquals(7)
function mt:removeRangeOfIntegerKeys() {
    $mt:integerKeys
        => map:remove(1 to 6)
        => map:keys()
};

declare
    %test:assertEquals(7)
function mt:removeIntegerKeysWithNonExistent() {
    $mt:integerKeys
        => map:remove((1, 2, 3, 4, 5, 6, 10))
        => map:keys()
};

declare
    %test:assertTrue
function mt:contains() {
    map:contains($mt:daysOfWeek, "We")
};

declare
    %test:assertFalse
function mt:containsOnMissingKey() {
    map:contains($mt:daysOfWeek, "Al")
};

declare
    %test:assertTrue
function mt:containsSingleKey() {
    map:contains(map { "Su": "Sunday" }, "Su")
};

declare
    %test:assertEquals("Hello world")
function mt:computedKeyValue() {
    let $map := map { 2 + 1 : concat("Hello ", "world") }
    return
        $map(3)
};

declare
    %test:assertEquals("false", "true")
function mt:immutabilityMerge() {
    let $original := mt:getMapFixture()
    let $derived := map:merge(($original, map:entry("Ca", "Caturday")))
    return (
        map:contains($original, "Ca"),
        map:contains($derived, "Ca")
    )
};

declare
    %test:assertEquals("true", "false")
function mt:immutabilityRemove() {
    let $original := mt:getMapFixture()
    let $derived := map:remove($original, "Fr")
    return (
        map:contains($original, "Fr"),
        map:contains($derived, "Fr")
    )
};

declare
    %test:assertEquals("false", "true")
function mt:immutabilityPut() {
    let $original := mt:getMapFixture()
    let $derived := map:put($original, "Ca", "Caturday")
    return (
        map:contains($original, "Ca"),
        map:contains($derived, "Ca")
    )
};

declare
    %test:assertEquals("Sunday")
function mt:sequenceType1() {
    let $map := map { 1 : "Sunday" }
    return
        map:get(mt:mapTest($map), 1)
};

declare
    %test:assertEquals("Sunday")
function mt:sequenceType2() {
    let $map := map { 1 : "Sunday" }
    return
        map:get(mt:mapTestFail($map), 1)
};

declare
    %private
function mt:mapTest($map as map(*)) as map(xs:integer, xs:string) {
    $map
};

declare
    %private
function mt:mapTestFail($map as map(*)) as map(xs:date, xs:string) {
    $map
};

declare
    %test:assertError("EXMPDY001")
    %test:name("Throw error if key is not a single atomic value")
function mt:wrongCardinality() {
    let $map := map { (1, 2) : "illegal" }
    return
        $map(1)
};

declare
    %test:assertEquals("two")
function mt:nestedMaps() {
    let $map := map { 1: map { 1: "one", 2: "two" } }
    return
        $map(1)(2)
};

declare
    %test:assertEquals("Monday")
function mt:nestedMaps2() {
    let $map := map { "week" : map { "days" : $mt:daysOfWeek } }
    return
        $map("week")("days")("Mo")
};

declare
    %test:assertEquals(2)
function mt:qnameKeys() {
    let $map := map { xs:QName("mt:one") : 1, xs:QName("mt:two") : 2 }
    return
        $map(xs:QName("mt:two"))
};

declare
    %test:assertEquals(2)
function mt:longKeys() {
    let $map := map { xs:long(1) : 1, xs:long(2) : 2 }
    return
        $map(2)
};

declare
    %test:args(2)
    %test:assertEquals(3)
    %test:args("Three")
    %test:assertEmpty
function mt:doubleKeys($key as item()) {
    let $map := map { xs:double(1.1) : 1, xs:double(2) : 2 }
    return
        map:merge(($map, map:entry(xs:double(2), 3)))($key)
};

declare
    %test:assertEquals(2, "value")
function mt:mixedNumeric() {
    let $map := map { 1.1 : 1, 2 : 2, "key" : "value" }
    return
        ($map(2), $map("key"))
};

declare
    %test:assertEquals("<c ns='c'/>", "<c ns='c'/>")
function mt:pathExpr() as element(c)+ {
    let $test :=
        <a>
            <b ns="b">
                <c ns="c"/>
            </b>
        </a>
    let $map :=
        map {
            "a" : $test//c,
            "b" : ($test//c)
        }
    return
        ($map("a"), $map("b"))
};

declare
    %test:assertEquals("What were you thinking?")
function mt:lookupUnaryOperator() {
    let $errors := (
      map { "level" : 1, "text" : "Boys will be boys ..." },
      map { "level" : 2, "text" : "What were you thinking?" },
      map { "level" : 3, "text" : "Call the cops!" }
    )
    return $errors[?level = 2]?text
};

declare
    %test:assertEquals("W0342", "M0535")
function mt:lookupMultipleMaps() {
    let $maps := ( map { "id" : "W0342" }, map { "id" : "M0535" } )
    return $maps?id
};

declare
    %test:assertEquals("W0342", "M0535")
function mt:lookupMultipleMaps2() {
    (
        map { "id" : "W0342" },
        map { "id" : "M0535" }
    )?id
};

declare
    %test:assertEquals("large")
function mt:lookupNestedMap() {
    let $map := map {
        "name": "sofa", "keys": map { "price": 200.0, "size": "large" }
    }
    return
        $map?keys?size
};

declare
    %test:args("One")
    %test:assertEquals(1)
    %test:args("Two")
    %test:assertEquals("2")
function mt:lookupParenthesized($key as xs:string) {
    let $map := map { "one": 1, "two": "2" }
    return
        $map?(lower-case($key))
};

declare
    %test:assertEquals(1)
function mt:lookupParenthesized() {
    let $f := function () { "one" }
    let $map := map { "one": 1 }
    return
        $map($f())
};

declare
    %test:assertError
function mt:lookupWrongType() {
    let $map := map { "one": 1, "two": "2" }
    let $str := "Hello"
    return
        ($map, $str)?one
};

declare
    %test:assertTrue
function mt:lookupWildcard() {
    let $expected := map:keys($mt:daysOfWeek) ! $mt:daysOfWeek(.)
    let $actual := $mt:daysOfWeek?*
    return
        count($actual) eq count($expected)
        and
        (every $day in $actual satisfies $day = $expected)
};

declare
    %test:assertEquals(1)
function mt:compat() {
    let $map := map { "one": 1, "two": "2" }
    return
        $map("one")
};

declare
    %test:assertError
function mt:no-atomization() {
    data(map { "k": "v" })
};

declare
    %test:name("for-each on a map with a single entry caused NPE")
    %test:assertEquals("k")
function mt:single-entry-map() {
    let $map := map:entry("k", ())
    return
        map:for-each($map, function($k, $v) { $k })
};

declare
    %test:assertEquals(5)
function mt:qname() {
    let $a := 1
    let $m := map { $a: fn:string-length("hello") }
    return
        $m?1
};

declare
    %test:assertEmpty
function mt:no-such-entry() {
    map:get(map {"foo": "bar"}, "baz")
};

declare
    %test:assertEqualsPermutation("uk", "nl", "se", "de")
function mt:multi-merge() {
    map:merge((
  	    map { "adam": "uk" },
  	    map { "dannes": "nl" },
  	    map { "leif": "se" },
  	    map { "wolfgang": "de" }
  	))?*
};

(:
  immutability tests for https://github.com/eXist-db/exist/issues/3724
:)
declare variable $mt:test-key-one := 1;
declare variable $mt:test-key-two := 2;
declare variable $mt:test-key-three := 3;

declare function mt:create-test-map() {
    map {
        $mt:test-key-one : true(),
        $mt:test-key-two : true()
    }
};

declare function mt:create-test-map2() {
    map {
        $mt:test-key-three : true()
    }
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-put-then-put() {
    let $extended := map:put(mt:create-test-map(), $mt:test-key-two, false())
    let $expected := $extended($mt:test-key-one)
    let $result := map:put($extended, $mt:test-key-one, false())
    return
        (
            $expected eq $extended($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-put-then-remove() {
    let $extended := map:put(mt:create-test-map(), $mt:test-key-two, false())
    let $expected := $extended($mt:test-key-one)
    let $result := map:remove($extended, $mt:test-key-one)
    return
        (
            $expected eq $extended($mt:test-key-one),
            empty($result($mt:test-key-one))
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-put-then-merge() {
    let $extended := map:put(mt:create-test-map(), $mt:test-key-two, false())
    let $expected := $extended($mt:test-key-one)
    let $result := map:merge(($extended, map { $mt:test-key-one : false() }))
    return
        (
            $expected eq $extended($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-remove-then-put() {
    let $removed := map:remove(mt:create-test-map(), $mt:test-key-two)
    let $expected := $removed($mt:test-key-one)
    let $result := map:put($removed, $mt:test-key-one, false())
    return
        (
            $expected eq $removed($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals("true", 1, "true", "true", 0)
function mt:immutable-remove-then-remove() {
    let $removed := map:remove(mt:create-test-map(), $mt:test-key-two)
    let $expected := $removed($mt:test-key-one)
    let $result := map:remove($removed, $mt:test-key-one)
    return
        (
            fn:empty($removed($mt:test-key-two)),
            map:size($removed),
            $expected eq $removed($mt:test-key-one),
            fn:empty($result($mt:test-key-one)),
            map:size($result)
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-remove-then-merge() {
    let $removed := map:remove(mt:create-test-map(), $mt:test-key-two)
    let $expected := $removed($mt:test-key-one)
    let $result := map:merge(($removed, map { $mt:test-key-one : false() }))
    return
        (
            $expected eq $removed($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-merge-then-put() {
    let $merged := map:merge(mt:create-test-map())
    let $expected := $merged($mt:test-key-one)
    let $result := map:put($merged, $mt:test-key-one, false())
    return
        (
            $expected eq $merged($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals(2, "true", "true", 1)
function mt:immutable-merge-then-remove() {
    let $merged := map:merge(mt:create-test-map())
    let $expected := $merged($mt:test-key-one)
    let $result := map:remove($merged, $mt:test-key-one)
    return
        (
            map:size($merged),
            $expected eq $merged($mt:test-key-one),
            fn:empty($result($mt:test-key-one)),
            map:size($result)
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-merge-then-merge() {
    let $merged := map:merge(mt:create-test-map())
    let $expected := $merged($mt:test-key-one)
    let $result := map:merge(($merged, map { $mt:test-key-one : false() }))
    return
        (
            $expected eq $merged($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals("true", "true")
function mt:immutable-merge2-then-put() {
    let $merged := map:merge((mt:create-test-map(), mt:create-test-map2()))
    let $expected := $merged($mt:test-key-one)
    let $result := map:put($merged, $mt:test-key-one, false())
    return
        (
            $expected eq $merged($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals(3, "true", "true", 2)
function mt:immutable-merge2-then-remove() {
    let $merged := map:merge((mt:create-test-map(), mt:create-test-map2()))
    let $expected := $merged($mt:test-key-one)
    let $result := map:remove($merged, $mt:test-key-one)
    return
        (
           map:size($merged),
           $expected eq $merged($mt:test-key-one),
           fn:empty($result($mt:test-key-one)),
           map:size($result)
        )
};

(:~
 : TODO(AR) implicit behaviour of map:merge according to XQ3.1 specification should be use-first not use-last,
 :          therefore the result should be ("true", "true") instead
:)
declare
    %test:assertEquals("true", "false")
function mt:immutable-merge2-then-merge() {
    let $merged := map:merge((mt:create-test-map(), mt:create-test-map2()))
    let $expected := $merged($mt:test-key-one)
    let $result := map:merge((map { $mt:test-key-one : false() }, $merged))
    return
        (
            $expected eq $merged($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};
declare
    %test:assertEquals("true", "true")
function mt:immutable-merge-duplicates-then-put() {
    let $merged := map:merge((mt:create-test-map(), mt:create-test-map()))
    let $expected := $merged($mt:test-key-one)
    let $result := map:put($merged, $mt:test-key-one, false())
    return
        (
            $expected eq $merged($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

declare
    %test:assertEquals(2, "true", "true", 1)
function mt:immutable-merge-duplicates-then-remove() {
    let $merged := map:merge((mt:create-test-map(), mt:create-test-map()))
    let $expected := $merged($mt:test-key-one)
    let $result := map:remove($merged, $mt:test-key-one)
    return
        (
            map:size($merged),
            $expected eq $merged($mt:test-key-one),
            fn:empty($result($mt:test-key-one)),
            map:size($result)
        )
};

(:~
 : TODO(AR) implicit behaviour of map:merge according to XQ3.1 specification should be use-first not use-last,
 :          therefore the result should be ("true", "true") instead
:)
declare
    %test:assertEquals("true", "false")
function mt:immutable-merge-duplicates-then-merge() {
    let $merged := map:merge((mt:create-test-map(), mt:create-test-map()))
    let $expected := $merged($mt:test-key-one)
    let $result := map:merge((map { $mt:test-key-one : false() }, $merged))
    return
        (
            $expected eq $merged($mt:test-key-one),
            $expected ne $result($mt:test-key-one)
        )
};

(:~
 : ensure that empty options map is allowed and behaves like
 : map:merge#1
 :)
declare
    %test:assertTrue
function mt:map-merge-2-empty-options-map() {
    let $maps := (mt:getMapFixture(), map { "Su": "Sunnuntai" })
    let $expected := map:merge($maps)
    let $actual := map:merge($maps, map {})
    return $expected?Su eq $actual?Su
};

(: test for issue https://github.com/eXist-db/exist/issues/5685 :)
declare
    %test:assertEquals("<ul><li>Scotland<ul><li>Highlands<ul><li>Fort William</li><li>Inverness</li></ul></li><li>Lowlands<ul><li>Glasgow</li></ul></li></ul></li></ul>")
function mt:nested-map-for-each() {
    <ul>{
        map:for-each($mt:places, function($country-key, $region-map) {
            <li>{
                $country-key,
                <ul>{
                    map:for-each($region-map, function($region-key, $town-map) {
                        <li>{
                            $region-key,
                            <ul>{
                                map:for-each($town-map, function($town-key, $town-value) {
                                    <li>{ $town-key }</li>
                                })
                            }</ul>
                        }</li>
                    })
                }</ul>
            }</li>
        })
    }</ul>
    => serialize(map{'indent':false()})
};
