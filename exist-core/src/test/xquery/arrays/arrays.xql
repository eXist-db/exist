xquery version "3.1";

(:~
 : Tests for the array datatype and associated functions.
 :)
module namespace arr="http://exist-db.org/test/arrays";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";
declare namespace json="http://www.json.org";

declare variable $arr:SERIALIZE_JSON :=
    <output:serialization-parameters>
        <output:method>json</output:method>
        <output:indent>no</output:indent>
    </output:serialization-parameters>;

declare variable $arr:SERIALIZE_JSON_INDENT :=
    <output:serialization-parameters>
        <output:method>json</output:method>
        <output:indent>yes</output:indent>
    </output:serialization-parameters>;

declare variable $arr:COLLECTION_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <create qname="a" type="xs:int"/>
            <range>
                <create qname="b" type="xs:int"/>
            </range>
        </index>
    </collection>;

declare variable $arr:XML_STORED :=
    <test>
        <a>1</a>
        <b>2</b>
    </test>;

declare variable $arr:primes := [2, 3, 5, 7, 11, 13, 17, 19];

declare
    %test:setUp
function arr:setup() {
    let $json := '[{"key1": "value1", "key2": "value2", "key1": "value3"}]'
    let $coll := xmldb:create-collection("/db", "array-test")
    let $confColl := xmldb:create-collection("/db/system/config/db", "array-test")
    return (
        xmldb:store($confColl, "collection.xconf", $arr:COLLECTION_CONF),
        xmldb:store($coll, "test.json", $json),
        xmldb:store($coll, "test.xml", $arr:XML_STORED)
    )
};

declare
    %test:tearDown
function arr:cleanup() {
    xmldb:remove("/db/array-test"),
    xmldb:remove("/db/system/config/db/array-test")
};

declare
    %test:args(1)
    %test:assertEquals(13)
    %test:args(3)
    %test:assertEquals(14)
function arr:function-item($pos as xs:int) {
    [13, 10, 14]($pos)
};

declare
    %test:args(1)
    %test:assertEquals(13)
    %test:args(3)
    %test:assertEquals(14)
function arr:function-item2($pos as xs:int) {
    let $a := [13, 10, 14]
    return
        $a($pos)
};

declare
    %test:args(1)
    %test:assertEquals(13)
    %test:args(3)
    %test:assertEquals(14)
function arr:function-item3($pos as xs:int) {
    let $a := [13, 10, 14]
    let $f := function($array as function(*), $position as xs:int) {
        $array($position)
    }
    return
        $f($a, $pos)
};

declare
    %test:assertError("FOAY0001")
function arr:function-item-out-of-bounds() {
    let $a := [13, 10, 14]
    return
        $a(22)
};

declare
    %test:assertError("err:XPTY0004")
function arr:function-item-invalid() {
    let $a := [13, 10, 14]
    return
        $a("x")
};

declare
    %test:assertError("FOAY0001")
function arr:get-item-out-of-bounds-positive() {
    let $a := [13, 10, 14]
    return
        array:get($a, 4)
};

declare
    %test:assertError("FOAY0001")
function arr:get-item-out-of-bounds-positive-large() {
    let $a := [13, 10, 14]
    return
        array:get($a, 22)
};

declare
    %test:assertError("FOAY0001")
function arr:get-item-out-of-bounds-zero() {
    let $a := [13, 10, 14]
    return
        array:get($a, 0)
};

declare
    %test:assertError("FOAY0001")
function arr:get-item-out-of-bounds-negative() {
    let $a := [13, 10, 14]
    return
        array:get($a, -1)
};

declare
    %test:pending("eXist-db does not correctly detect the type error. This is likely a larger issue than just arrays.")
    %test:assertError("XPTY0004")
function arr:get-invalid-type() {
    let $a := [13, 10, 14]
    return
        array:get($a, xs:double(0.1))
};

declare
    %test:assertEquals(13)
function arr:get-first() {
    let $a := [13, 10, 14]
    return
        array:get($a, 1)
};

declare
    %test:assertEquals(14)
function arr:get-last() {
    let $a := [13, 10, 14]
    return
        array:get($a, 3)
};

declare
    %test:args(1)
    %test:assertEmpty
    %test:args(2)
    %test:assertEquals(27, 17, 0)
function arr:square-constructor1($pos as xs:int) {
    let $a := [(), (27, 17, 0)]
    return $a($pos)
};

declare
    %test:args(1)
    %test:assertEquals("<p>test</p>")
    %test:args(2)
    %test:assertEquals(55)
function arr:square-constructor2($pos as xs:int) {
    let $a := [<p>test</p>, 55]
    return $a($pos)
};

declare
    %test:assertEquals(0)
function arr:square-constructor3() {
    let $a := []
    return array:size($a)
};

declare
    %test:args(1)
    %test:assertEquals(27)
    %test:args(2)
    %test:assertEquals(17)
function arr:curly-constructor1($pos as xs:int) {
    let $x := (27, 17, 0)
    let $a := array { $x }
    return $a($pos)
};

declare
    %test:args(1)
    %test:assertEquals(27)
    %test:args(2)
    %test:assertEquals(17)
function arr:curly-constructor2($pos as xs:int) {
    let $a := array { (), (27, 17, 0) }
    return $a($pos)
};

declare
    %test:assertTrue
function arr:deep-equal1() {
    deep-equal([1, <node/>, "a", ["b", "c"]], [1, <node/>, "a", ["b", "c"]])
};

declare
    %test:assertFalse
function arr:deep-equal2() {
    deep-equal([1, 2, "a", ["b", "c"]], [1, 2, "a", ["b", "a"]])
};

declare
    %test:assertEquals(2)
function arr:with-map1() {
    let $a := [ map { "a": 1, "b": 2 } ]
    return $a(1)("b")
};

declare
    %test:assertEquals(3)
function arr:with-map2() {
    let $a := map { "a": 1, "b": [2, 3, 4] }
    return $a("b")(2)
};

declare
    %test:assertEquals(5)
function arr:nested1() {
    let $a := [ [1, 2, 3], [4, 5, 6] ]
    return $a(2)(2)
};

declare
    %test:assertEquals(5)
function arr:nested2() {
    let $a := array { [1, 2, 3], [4, 5, 6] }
    return $a(2)(2)
};

declare
    %test:args(2)
function arr:size1() {
    array:size([<p>test</p>, 55])
};

declare
    %test:args(1)
function arr:size2() {
    array:size([[]])
};

declare
    %test:assertEquals(3, 4, 5)
function arr:append1() {
    array:append([1, 2], (3, 4, 5))(3)
};

declare
    %test:assertEmpty
function arr:append2() {
    array:append([1, 2], ())(3)
};

declare
    %test:assertEquals("a")
function arr:head1() {
    array:head(["a", "b"])
};

declare
    %test:assertEquals(2)
function arr:head2() {
    array:head([[1, 2], 3, 4, 5])(2)
};

declare
    %test:assertError("FOAY0001")
function arr:head-empty() {
    array:head([])
};

declare
    %test:assertEquals(2)
function arr:tail1() {
    array:tail([1, 2, 3])(1)
};

declare
    %test:assertEquals(2)
function arr:tail2() {
    array:tail([1, [2, 3]])(1)(1)
};

declare
    %test:assertEquals(0)
function arr:tail3() {
    array:size(array:tail([1]))
};

declare
    %test:assertError("FOAY0001")
function arr:tail-empty() {
    array:tail([])
};

declare 
    %test:assertEquals(2, 3)
function arr:tail-on-array() {
    array:tail([1,[2,3]])?*?*
};

declare
    %test:args(2)
    %test:assertEquals("b", "c", "d")
    %test:args(5)
    %test:assertEmpty
function arr:subarray1($start as xs:int) {
    let $a := array:subarray(["a", "b", "c", "d"], $start)
    return
        $a?*
};

declare
    %test:args(5, 0)
    %test:assertEmpty
    %test:args(2, 0)
    %test:assertEmpty
    %test:args(2, 1)
    %test:assertEquals("b")
    %test:args(2, 2)
    %test:assertEquals("b", "c")
    %test:args(4, 1)
    %test:assertEquals("d")
    %test:args(1, 2)
    %test:assertEquals("a", "b")
    %test:args(1, 5)
    %test:assertError("FOAY0001")
    %test:args(0, 1)
    %test:assertError("FOAY0001")
    %test:args(1, "-1")
    %test:assertError("FOAY0002")
function arr:subarray2($start as xs:int, $length as xs:int) {
    let $a := array:subarray(["a", "b", "c", "d"], $start, $length)
    return
        $a?*
};

declare
    %test:args(1)
    %test:assertEquals("b", "c", "d")
    %test:args(2)
    %test:assertEquals("a", "c", "d")
    %test:args(4)
    %test:assertEquals("a", "b", "c")
    %test:args(0)
    %test:assertError("FOAY0001")
    %test:args(5)
    %test:assertError("FOAY0001")
function arr:remove1($pos as xs:int) {
    array:remove(["a", "b", "c", "d"], $pos)?*
};

declare
    %test:assertEmpty
function arr:remove2() {
    array:remove(["a"], 1)?*
};

declare
    %test:assertEquals("d", "c", "b", "a")
function arr:reverse1() {
    array:reverse(["a", "b", "c", "d"])?*
};

declare
    %test:assertEquals("c", "d", "a", "b")
function arr:reverse2() {
    array:reverse([("a", "b"), ("c", "d")])?*
};

declare
    %test:assertEquals("1", "2", "3", "4", "5")
function arr:reverse3() {
    array:reverse([(1 to 5)])?*
};

declare
    %test:assertEmpty
function arr:reverse4() {
    array:reverse([])?*
};

declare
    %test:assertEmpty
function arr:join1() {
    array:join(())?*
};

declare
    %test:assertEquals("a", "b", "c", "d")
function arr:join2() {
    array:join((["a", "b"], ["c", "d"], []))?*
};

declare
    %test:assertEquals(5)
function arr:join3() {
    array:size(array:join((["a", "b"], ["c", "d"], [["e", "f"]])))
};

declare
    %test:assertEquals("A", "B", "C", "D")
function arr:for-each1() {
    array:for-each(["a", "b", "c", "d"], upper-case#1)?*
};

declare
    %test:assertEquals("false", "false", "true", "true")
function arr:for-each2() {
    array:for-each(["a", "b", 1, 2], function($z) { $z instance of xs:integer })?*
};

declare
    %test:assertEquals("1", "2")
function arr:filter1() {
    array:filter(["a", "b", 1, 2], function($z) { $z instance of xs:integer })?*
};

declare
    %test:assertEquals("a", "b", 1)
function arr:filter2() {
    array:filter(["a", "b", "", 0, 1], boolean#1)?*
};

declare
    %test:assertEquals("the cat", "on the mat")
function arr:filter3() {
    array:filter(["the cat", "sat", "on the mat"], function($s) { count(tokenize($s, " ")) gt 1 })?*
};

declare
    %test:assertFalse
function arr:fold-left1() {
    array:fold-left([true(), true(), false()], true(), function($x, $y) { $x and $y })
};

declare
    %test:assertTrue
function arr:fold-left2() {
    array:fold-left([true(), true(), false()], false(), function($x, $y) { $x or $y })
};

declare
    %test:assertEquals(1)
function arr:fold-left3() {
    array:fold-left([], 1, function($x, $y) { $x + $y })
};

declare
    %test:assertEquals(8)
function arr:fold-left4() {
    array:fold-left(["abc", "def", "gh"], 0, function($x, $y) { $x + string-length($y) })
};

declare
    %test:assertEquals("abcdefgh")
function arr:fold-left5() {
    array:fold-left(["abc", "def", "gh"], "", function($x, $y) { $x || $y })
};

declare
    %test:assertEquals(1)
function arr:fold-left6() {
    array:fold-left([1, 2], (), function($x, $y) { [$x, $y] })?1?2
};

declare
    %test:assertFalse
function arr:fold-right1() {
    array:fold-right([true(), true(), false()], true(), function($x, $y) { $x and $y })
};

declare
    %test:assertTrue
function arr:fold-right2() {
    array:fold-right([true(), true(), false()], false(), function($x, $y) { $x or $y })
};

declare
    %test:assertEquals(2)
function arr:fold-right3() {
    array:fold-right([1, 2, 3], (), function($x, $y) { [$x, $y] })?2?1
};

declare
    %test:assertEquals(5, 4, 3, 2, 1)
function arr:fold-right4() {
    array:fold-right(
        array { 1 to 5 },
        (),
        function($a, $b) { $b, $a }
    )
};

declare
    %test:assertEquals("AB", "BC", "CD")
function arr:for-each-pair1() {
    let $a := ["A", "B", "C", "D"]
    return
        array:for-each-pair($a, array:tail($a), concat#2)?*
};

declare
    %test:assertEquals(5, 7, 9)
function arr:for-each-pair2() {
    array:for-each-pair(
        array { 1 to 3 },
        array { 4 to 6 },
        function($a, $b) { $a + $b }
    )?*
};

declare
    %test:assertEmpty
function arr:for-each-pair3() {
    array:for-each-pair(
        [],
        array { 4 to 6 },
        function($a, $b) { $a + $b }
    )?*
};

declare
    %test:assertEquals(1, 4, 6, 5, 3)
function arr:flatten1() {
    array:flatten([1, 4, 6, 5, 3])
};

declare
    %test:assertEquals(1, 2, 5, 10, 11, 12, 13)
function arr:flatten2() {
    array:flatten(([1, 2, 5], [[10, 11], 12], [], 13))
};

declare
    %test:assertEquals(1, 1, 0, 1, 1)
function arr:flatten3() {
    array:flatten((1, [(1, 0), (1, 1)]))
};

declare
    %test:assertEmpty
function arr:flatten4() {
    array:flatten(())
};

declare
    %test:assertEquals("a", "b", "x", "c", "d")
function arr:insert-before1() {
    array:insert-before(["a", "b", "c", "d"], 3, "x")?*
};

declare
    %test:assertEquals("x", "y")
function arr:insert-before2() {
    array:insert-before(["a", "b", "c", "d"], 3, ("x", "y"))?3
};

declare
    %test:assertEquals("a", "b", "c", "d", "x")
function arr:insert-before3() {
    array:insert-before(["a", "b", "c", "d"], 5, "x")?*
};

declare
    %test:assertEquals("array")
function arr:array-type1() {
    let $a := [1, 2]
    return
        typeswitch($a)
            case array(*) return
                "array"
            default return
                "no array"
};

declare
    %test:assertEquals("<t>test</t>")
function arr:apply-inline() {
    let $fn := function($a as xs:string, $b as xs:int, $c as element()) {
        $c
    }
    return
        apply($fn, ["a", 1, <t>test</t>])
};

declare
    %test:assertEquals("abc")
function arr:apply-internal() {
    apply(concat#3, ["a", "b", "c"])
};

declare
    %test:assertEquals("a", 1)
function arr:apply-param-array() {
    let $fn := function($a as array(*)) {
        $a?*
    }
    return
        apply($fn, [["a", 1]])
};

declare
    %test:assertEquals("1")
function arr:apply-param-map() {
    let $fn := function($m as map(*)) {
        $m?*
    }
    return
        apply($fn, [map { "key": 1 }])
};

declare
    %test:assertEquals(3)
function arr:apply-param-seq() {
    let $fn := function($s as item()*) {
        count($s)
    }
    return
        apply($fn, [(1, 2, 3)])
};

declare
    %test:assertEquals("HELLO WORLD")
function arr:apply-closure() {
    let $s := "Hello world"
    let $fn := function() {
        upper-case($s)
    }
    return
        apply($fn, [])
};

declare
    %test:args('"a"')
    %test:assertEquals("a")
    %test:args("2")
    %test:assertEquals(2)
    %test:args("true")
    %test:assertTrue
    %test:args("false")
    %test:assertFalse
    %test:args("null")
    %test:assertEmpty
    %test:args("[]")
    %test:assertXPath("array:size($result) = 0")
    %test:args('[1, "a"]')
    %test:assertXPath("$result?2 = 'a'")
    %test:args('[1, ["a", "b"]]')
    %test:assertXPath("$result?2?2 = 'b'")
    %test:args('[1, []]')
    %test:assertXPath("array:size($result?2) = 0")
    %test:args('[1, null]')
    %test:assertXPath("array:size($result) = 2")
    %test:args('{"key1": "val1", "key2": "val2"}')
    %test:assertXPath("$result?key2 = 'val2'")
    %test:args('{"key1": "val1", "key2": [1, 2]}')
    %test:assertXPath("$result?key2?2 = 2")
    %test:args('{"key1": null, "key2": "val2"}')
    %test:assertXPath("empty($result?key1)")
    %test:args('{key: "value"}')
    %test:assertError("FOJS0001")
    %test:args("{key: 'value'}")
    %test:assertError("FOJS0001")
    %test:args('{"k1": "v1", "k1": "v2"}')
    %test:assertXPath("$result?k1 = 'v2'")
function arr:parse-json($json as xs:string) {
    parse-json($json)
};

declare
    %test:args('{key: "value"}')
    %test:assertXPath("$result?key = 'value'")
    %test:args("{'key': 'value'}")
    %test:assertXPath("$result?key = 'value'")
function arr:parse-json-liberal($json as xs:string) {
    parse-json($json, map { "liberal": true() })
};

declare
    %test:args('{"k1": "v1", "k1": "v2"}', "reject")
    %test:assertError("FOJS0003")
    %test:args('{"k1": "v1", "k1": "v2"}', "use-first")
    %test:assertXPath("$result?k1 = 'v1'")
    %test:args('{"k1": "v1", "k1": "v2"}', "use-last")
    %test:assertXPath("$result?k1 = 'v2'")
function arr:parse-json-duplicates($json as xs:string, $duplicates as xs:string) {
    parse-json($json, map { "duplicates": $duplicates })
};

declare
    %test:assertXPath("$result?1?key2 = 'value2'")
function arr:json-doc-db() {
    json-doc("/db/array-test/test.json")
};

declare
    %test:assertError("FOJS0003")
function arr:json-doc-options() {
    json-doc("/db/array-test/test.json", map { "duplicates": "reject" })
};

declare
    %test:assertXPath("$result?1?key2 = 'value2'")
    %test:pending(" Requires running server")
function arr:json-doc-http() {
    json-doc("http://localhost:8080/exist/rest/db/array-test/test.json")
};

declare
    %test:assertError("FOUT1170")
function arr:json-doc-invalid() {
    json-doc("notfound.json")
};

declare
    %test:assertEquals('{"a1":22,"a2":["z","b","c"]}')
function arr:serialize() {
    let $json :=
        map {
            "a1": 22,
            "a2": [ "z", "b", "c" ]
        }
    let $serialized :=
        serialize($json, $arr:SERIALIZE_JSON)
    return
        $serialized
};

declare
    %test:assertTrue
function arr:serialize-roundtrip() {
    let $json :=
        map {
            "k1": 22,
            "k2": [ "z", "b", "c" ],
            "k3": map { "k4": array { 1 to 100 }, "k5": () }
        }
    let $serialized :=
        serialize($json, $arr:SERIALIZE_JSON)
    let $parsed := parse-json($serialized)
    return
        deep-equal($json, $parsed)
};

declare
    %test:assertEquals('{"xml":"<div><p>Test</p></div>"}')
function arr:serialize-node() {
    let $json :=
        map {
            "xml": <div><p>Test</p></div>
        }
    return
        serialize($json, $arr:SERIALIZE_JSON)
};

declare
    %test:assertEquals('{"status":true}')
function arr:serialize-old-json-compat() {
    let $xmlJson := <result><status json:literal="true">true</status></result>
    return
        serialize($xmlJson, $arr:SERIALIZE_JSON)
};

declare
    %test:assertEquals('{ "status" : true }')
function arr:serialize-old-json-compat-indent() {
    let $xmlJson := <result><status json:literal="true">true</status></result>
    return
        serialize($xmlJson, $arr:SERIALIZE_JSON_INDENT)
};

declare
    %test:args(2)
    %test:assertTrue
    %test:args(3)
    %test:assertTrue
    %test:args(6)
    %test:assertTrue
    %test:args(7)
    %test:assertFalse
function arr:general-comparison1($val as xs:int) {
    [1, 2, 3, [5, 6]] = $val
};

declare
    %test:assertTrue
function arr:general-comparison2() {
    let $xml := <test><a>2</a></test>
    return
        $xml/a[. = [2, 3]]
};

declare
    %test:assertFalse
function arr:general-comparison3() {
    let $xml := <test><a>2</a></test>
    return
        $xml/a[. = [3, 4]]
};

declare
    %test:assertEquals("<a>1</a>")
function arr:general-comparison-range() {
    collection("/db/array-test")//a[. = [1, 2]]
};

declare
    %test:assertEquals("<b>2</b>")
function arr:general-comparison-range-new() {
    collection("/db/array-test")//b[. = [1, 2]]
};

declare
    %test:assertError
function arr:value-comparison() {
    let $xml := <test><a>3</a></test>
    return
        $xml/a[. eq [3, 4]]
};

declare
    %test:assertEquals(1)
function arr:cast-as() {
    array { 1 } cast as xs:double
};

declare
    %test:assertError
function arr:cast-as-invalid() {
    array { 1 to 3 } cast as xs:double
};

declare
    %test:assertEquals(1)
function arr:castable-as() {
    array { 1 } castable as xs:double
};

declare
    %test:assertError
function arr:castable-as-invalid() {
    array { 1 to 3 } castable as xs:double
};

declare
    %test:assertEquals("<test><p>Hello <b>world</b>!</p></test>")
function arr:enclosed-expr-element() {
    <test><p>Hello {[<b>world</b>, "!"]}</p></test>
};

declare
    %test:assertEquals("<p>Hello world !</p>")
function arr:element-computed() {
    element p {
        "Hello", ["world", "!"]
    }
};

declare
    %test:assertEquals('<test attr="1 2"/>')
function arr:enclosed-expr-attribute() {
    <test attr="{[1, 2]}"/>
};

declare
    %test:assertEquals('<p attr="1 2"/>')
function arr:attribute-computed() {
    element p {
        attribute attr { [1, 2] }
    }
};

declare
    %test:assertEquals('<p>Hello world</p>')
function arr:text-computed() {
    element p {
        text { ["Hello", "world"] }
    }
};

declare
    %test:assertEquals('<p><!--Hello world--></p>')
function arr:comment-computed() {
    element p {
        comment { ["Hello", "world"] }
    }
};

declare
    %test:assertEquals(1, 2, 3, 4)
function arr:fn-data() {
    data([1, 2, [3, 4]])
};

declare
    %test:assertEquals("a", "b")
function arr:nested-for-each() {
    array:for-each([["a","b"]],function($v){
        array:for-each($v,function($_){
            $_
        })
    })?*?*
};

declare
    %test:assertTrue
function arr:lookupWildcard() {
    let $expected := (1 to array:size($arr:primes)) ! $arr:primes(.)
    let $actual := $arr:primes?*
    return
        count($actual) eq count($expected)
        and
        (every $prime in $actual satisfies $prime = $expected)
};
