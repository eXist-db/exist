xquery version "1.0";

module namespace mt="http://exist-db.org/xquery/test/maps";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $mt:daysOfWeek :=
    map {
        "Su" : "Sunday",
        "Mo" : "Monday",
        "Tu" : "Tuesday",
        "We" : "Wednesday",
        "Th" : "Thursday",
        "Fr" : "Friday",
        "Sa" : "Saturday"
    };
    
declare %test:assertEquals("Wednesday") function mt:createLiteral1() {
    $mt:daysOfWeek("We")
};

declare %test:assertEquals("Wednesday") function mt:createWithIntKeys() {
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

declare %test:assertEquals(20) function mt:createWithFunctionValue() {
    let $fn := function($p) { $p * 2 }
    let $map := map { "callback" : $fn }
    return
        map:get($map, "callback")(10)
};

declare %test:assertEquals("Sunday") function mt:createWithEntry() {
    let $days := ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    let $map := map:new(map(function($day) { map:entry(substring($day, 1, 2), $day) }, $days))
    return
        $map("Su")
};

declare %test:assertXPath("count($result) = 8") %test:assertXPath("7 = $result") 
function mt:createFromTwoMaps() {
    let $week := map{0: "Sonntag", 1: "Montag", 2: "Dienstag", 3: "Mittwoch", 4: "Donnerstag", 5: "Freitag", 6: "Samstag"}
    let $map := map:new(($week, map { 7 : "Sonntag" }))
    return
        map:keys($map)
};

declare 
    %test:assertEquals("Sonntag", "Dienstag", "Donnerstag", "Samstag")
function mt:for-each-entry() {
    let $week := map{0: "Sonntag", 1: "Montag", 2: "Dienstag", 3: "Mittwoch", 4: "Donnerstag", 5: "Freitag", 6: "Samstag"}
    return
        map:for-each-entry($week, function($key, $value) {
            if ($key mod 2 = 0) then
                $value
            else
                ()
        })
};

declare 
    %test:assertEquals(3)
function mt:for-each-entry2() {
    let $nm := map:new(map:for-each-entry(map{"a":1, "b":2}, function($k, $v){map:entry($k, $v+1)}))
    return
        $nm?b
};

declare %test:assertEquals("Sunday") function mt:createWithSingleKey() {
    let $map := map:entry("Su", "Sunday")
    return
        $map("Su")
};

declare %test:assertEquals("Samstag", "Sonnabend") function mt:overwriteKeyInNewMap() {
    let $week := map {0: "Sonntag", 1: "Montag", 2: "Dienstag", 3: "Mittwoch", 4: "Donnerstag", 5: "Freitag", 6: "Samstag"}
    let $map := map:new(($week, map { 6 :  "Sonnabend" }))
    return
        ($week(6), $map(6))
};

declare %test:assertEmpty function mt:mapEmptyValue() {
    let $map := map {0: (), 1 : ("One", "Two") }
    return
        $map(0)
};

declare %test:assertEquals("One", "Two") function mt:mapSequenceValue() {
    let $map := map {0: (), 1 : ("One", "Two") }
    return
        $map(1)
};

declare %test:assertEquals("Heinz", "Roland", "Uschi", "Verona") function mt:keyTypeDate () {
    let $map := map { 
        xs:date("1975-03-19") : "Uschi", 
        xs:date("1980-01-22") : "Verona",
        xs:date("1960-06-14") : "Heinz",
        xs:date("1963-10-21") : "Roland"
    }
    for $key in map:keys($map)
    let $name := $map($key)
    order by $name ascending
    return
        $name
};

declare %test:assertEquals(0, "One") function mt:mixedKeyTypes() {
    let $map := map{ "Zero" : 0, 1 : "One" }
    return
        ($map("Zero"), $map(1))
};

declare %test:assertTrue function mt:containsOnEmptyValue() {
    let $map := map{0: () }
    return
        map:contains($map, 0)
};

declare %test:assertEquals("Monday") function mt:createWithCollation() {
    let $map := map:new(map:entry("Mo", "Monday"), "?strength=primary")
    return
        $map("mo")
};

declare %test:assertEquals("Fr", "Mo", "Sa", "Su", "Th", "Tu", "We") function mt:keys() {
    let $days := ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    let $map := map:new(map(function($day) { map:entry(substring($day, 1, 2), $day) }, $days))
    for $day in map:keys($map)
    order by $day ascending
    return
        $day
};

declare %test:assertEquals("Su") function mt:keysOnMapWithSingleKey() {
    let $map := map:entry("Su", "Sunday")
    return
        map:keys($map)
};

declare %test:assertEquals("Fr", "Mo", "Sa", "Su", "Th") function mt:remove() {
    let $days := ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    let $map := map:new(map(function($day) { map:entry(substring($day, 1, 2), $day) }, $days))
    for $day in map:keys(map:remove(map:remove($map, "We"), "Tu"))
    order by $day
    return
        $day
};

declare %test:assertFalse function mt:remove() {
    let $days := ("Monday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    let $map := map:new(map(function($day) { map:entry(substring($day, 1, 2), $day) }, $days))
    return
        map:contains(map:remove($map, "Tu"), "Tu")
};

declare %test:assertEmpty function mt:removeSingleKey() {
    let $map := map:entry("Su", "Sunday")
    let $empty := map:remove($map, "Su")
    return
        map:keys($empty)
};

declare %test:assertTrue function mt:contains() {
    map:contains($mt:daysOfWeek, "We")
};

declare %test:assertFalse function mt:containsOnMissingKey() {
    map:contains($mt:daysOfWeek, "Al")
};

declare %test:assertTrue function mt:containsSingleKey() {
    let $map := map:entry("Su", "Sunday")
    return
        map:contains($map, "Su")
};

declare %test:assertEquals("Hello world") function mt:computedKeyValue() {
    let $map := map { 2 + 1 : concat("Hello ", "world") }
    return
        $map(3)
};

declare %test:assertEquals("false", "true") function mt:immutability() {
    let $map := map {
        "Su" : "Sunday",
        "Mo" : "Monday",
        "Tu" : "Tuesday",
        "We" : "Wednesday",
        "Th" : "Thursday",
        "Fr" : "Friday"
    }
    let $map2 := map:new(($map, map:entry("Sa", "Saturday")))
    return
        (map:contains($map, "Sa"), map:contains($map2, "Sa"))
};

declare %test:assertTrue function mt:immutability2() {
    let $map := map {
        "Su" : "Sunday",
        "Mo" : "Monday",
        "Tu" : "Tuesday",
        "We" : "Wednesday",
        "Th" : "Thursday",
        "Fr" : "Friday"
    }
    let $map2 := map:remove($map, "Fr")
    return
        map:contains($map, "Fr")
};

declare %test:assertFalse function mt:immutability3() {
    let $map := map {
        "Su" : "Sunday",
        "Mo" : "Monday",
        "Tu" : "Tuesday",
        "We" : "Wednesday",
        "Th" : "Thursday",
        "Fr" : "Friday"
    }
    let $map2 := map:remove($map, "Fr")
    return
        map:contains($map2, "Fr")
};

declare %test:assertTrue function mt:immutability4() {

    let $daysOfWeek :=  map {   "Sunday" : 1,
                                 "Monday" : 2,
                                 "Tuesday" : 3,
                                 "Wednesday" : 4,
                                 "Thursday" : 5,
                                 "Friday" : 6,
                                 "Saturday" : 7
                             }
    let $workDays := map:remove($daysOfWeek, "Sunday")

    return map:contains($workDays,"Monday")
};

declare %test:assertEquals("Sunday") function mt:sequenceType1() {
    let $map := map { 1 : "Sunday" }
    return
        map:get(mt:mapTest($map), 1)
};

declare %private function mt:mapTest($map as map(*)) as map(xs:integer, xs:string) {
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
    let $map := map { "week" := map { "days" : $mt:daysOfWeek } }
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
function mt:doubleKeys($key as xs:double) {
    let $map := map { xs:double(1.1) : 1, xs:double(2) : 2 }
    return
        map:new(($map, map:entry(xs:double(2), 3)))($key)
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
    ( map { "id" : "W0342" }, map { "id" : "M0535" } )?id
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
function mt:lookupStringLiteral() {
    let $map := map { "some key": 1, "another key": "2" }
    return
        $map?"some key"
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
    %test:assertEquals(1)
function mt:compat() {
    let $map := map { "one":= 1, "two":= "2" }
    return
        $map("one")
};