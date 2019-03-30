xquery version "3.1";

module namespace ser="http://exist-db.org/xquery/test/serialize";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare %private function ser:adaptive($data, $itemSep as xs:string?) {
    let $options :=
        <output:serialization-parameters
            xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
            <output:method value="adaptive"/>
            <output:indent>no</output:indent>
            {
                if ($itemSep) then
                    <output:item-separator>{$itemSep}</output:item-separator>
                else
                    ()
            }
        </output:serialization-parameters>
    return
        fn:serialize($data, $options)
};

declare %private function ser:adaptive-map-params($data, $itemSep as xs:string?) {
    let $options :=
        map {
            "method": "adaptive",
            "indent": false(),
            "item-separator": $itemSep
        }
    return
        fn:serialize($data, $options)
};

declare %private function ser:adaptive($data) {
    ser:adaptive($data, ())
};

declare %private function ser:adaptive-map-params($data) {
    ser:adaptive-map-params($data, ())
};

declare variable $ser:atomic :=
    <atomic:root xmlns:atomic="http://www.w3.org/XQueryTest" xmlns:foo="http://www.example.com/foo"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <atomic:duration>P1Y2M3DT10H30M</atomic:duration>
      <atomic:dateTime>2002-04-02T12:00:00Z</atomic:dateTime>
      <atomic:time>13:20:10.5Z</atomic:time>
      <atomic:date>2000-01-01+05:00</atomic:date>
      <atomic:gYearMonth>2001-12</atomic:gYearMonth>
      <atomic:gYear>2001</atomic:gYear>
      <atomic:gMonthDay>--12-17</atomic:gMonthDay>
      <atomic:gDay>---17</atomic:gDay>
      <atomic:gMonth>--12</atomic:gMonth>
      <atomic:boolean>true</atomic:boolean>
      <atomic:base64Binary>R0lGODlhcgGSALMAAAQCAEMmCZtuMFQxDS8b</atomic:base64Binary>
      <atomic:hexBinary>A9FD64E12C</atomic:hexBinary>
      <atomic:float>1267.43233E12</atomic:float>
      <atomic:double>1267.43233E12</atomic:double>
      <atomic:anyURI>http://www.example.com</atomic:anyURI>
      <atomic:NCName atomic:attr="aNCname">aNCname</atomic:NCName>
      <atomic:QName atomic:attr="foo:aQname">foo:aQname</atomic:QName>
      <atomic:string>A String Function</atomic:string>
      <atomic:normalizedString>aNormalizedString</atomic:normalizedString>
      <atomic:language>EN</atomic:language>
      <atomic:decimal atomic:attr="12678967.543233">12678967.543233</atomic:decimal>
      <atomic:integer>12678967543233</atomic:integer>
      <atomic:nonPositiveInteger>-1</atomic:nonPositiveInteger>
      <atomic:long>12678967543233</atomic:long>
      <atomic:nonNegativeInteger>12678967543233</atomic:nonNegativeInteger>
      <atomic:negativeInteger>-12678967543233</atomic:negativeInteger>
      <atomic:int>126789675</atomic:int>
      <atomic:unsignedLong>12678967543233</atomic:unsignedLong>
      <atomic:positiveInteger>12678967543233</atomic:positiveInteger>
      <atomic:short>12678</atomic:short>
      <atomic:unsignedInt>1267896754</atomic:unsignedInt>
      <atomic:byte>126</atomic:byte>
      <atomic:unsignedShort>12678</atomic:unsignedShort>
      <atomic:unsignedByte>126</atomic:unsignedByte>
      <atomic:id1>id1</atomic:id1>
      <atomic:id2>id2</atomic:id2>
      <atomic:idrefs atomic:attr="id1 id2">id1 id2</atomic:idrefs>
    </atomic:root>;

declare variable $ser:test-xml := document {
    <?pi?>,
    <elem a="abc"><!--comment--><b>123</b></elem>
};

declare variable $ser:collection-name := "serialization-test";

declare variable $ser:collection := "/db/" || $ser:collection-name;

declare
    %test:setUp
function ser:setup() {
    xmldb:create-collection("/db", $ser:collection-name),
    xmldb:store($ser:collection, "test.xml", $ser:test-xml)
};

declare
    %test:tearDown
function ser:teardown() {
    xmldb:remove($ser:collection)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-element() {
    fn:serialize($ser:atomic)
};

declare
    %test:assertError
function ser:serialize-attribute() {
    fn:serialize(($ser:atomic//@*)[1])
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-with-params() {
    let $params :=
      <output:serialization-parameters
           xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
        <output:method value="xml"/>
        <output:indent value="yes"/>
      </output:serialization-parameters>
    return fn:serialize($ser:atomic, $params)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-with-params-map-params() {
    let $params :=
      map {
        "method": "xml",
        "indent": true()
      }
    return fn:serialize($ser:atomic, $params)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-no-method() {
    let $params :=
        <output:serialization-parameters xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
            <output:indent value="yes"/>
        </output:serialization-parameters>
    return fn:serialize($ser:atomic, $params)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-no-method-map-params() {
    let $params :=
        map {
            "indent": true()
        }
    return fn:serialize($ser:atomic, $params)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-empty-params() {
    let $params := ()
    return fn:serialize($ser:atomic, $params)
};

declare
    %test:assertEquals("aaa bbb")
function ser:serialize-atomic() {
    let $nodes := ("aaa", "bbb")
    return
        fn:serialize($nodes)
};

declare
    %test:assertEquals("")
function ser:serialize-empty-sequence() {
    let $nodes := ()
    return
        fn:serialize($nodes)
};

declare
    %test:args(1234)
    %test:assertEquals('"1234"')
    %test:args('Hello "world"!')
    %test:assertEquals('"Hello ""world""!"')
function ser:adaptive-simple-atomic($atomic as xs:anyAtomicType) {
    ser:adaptive($atomic)
};

declare
    %test:args(1234)
    %test:assertEquals('"1234"')
    %test:args('Hello "world"!')
    %test:assertEquals('"Hello ""world""!"')
function ser:adaptive-simple-atomic-map-params($atomic as xs:anyAtomicType) {
    ser:adaptive-map-params($atomic)
};

declare
    %test:assertEquals("fn:substring#3
math:pi#0
fn:exists#1
(anonymous-function)#1
Q{http://exist-db.org/xquery/test/serialize}adaptive-simple-atomic#1")
function ser:adaptive-function-item() {
    let $input := (
        substring#3,
        math:pi#0,
        Q{http://www.w3.org/2005/xpath-functions}exists#1,
        function ($a) { $a },
        ser:adaptive-simple-atomic#1
    )
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals("fn:substring#3
math:pi#0
fn:exists#1
(anonymous-function)#1
Q{http://exist-db.org/xquery/test/serialize}adaptive-simple-atomic#1")
function ser:adaptive-function-item-map-params() {
    let $input := (
        substring#3,
        math:pi#0,
        Q{http://www.w3.org/2005/xpath-functions}exists#1,
        function ($a) { $a },
        ser:adaptive-simple-atomic#1
    )
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml() {
    let $input := ($ser:test-xml, $ser:test-xml/elem, $ser:test-xml/elem/@a, $ser:test-xml/elem/comment())
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml-map-params() {
    let $input := ($ser:test-xml, $ser:test-xml/elem, $ser:test-xml/elem/@a, $ser:test-xml/elem/comment())
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml-stored() {
    let $doc := doc($ser:collection || "/test.xml")
    let $input := ($doc, $doc/elem, $doc/elem/@a, $doc/elem/comment())
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml-stored-map-params() {
    let $doc := doc($ser:collection || "/test.xml")
    let $input := ($doc, $doc/elem, $doc/elem/@a, $doc/elem/comment())
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals('xmlns:foo="http://exist-db.org/foo"')
function ser:adaptive-xml-namespace() {
    let $input := namespace foo { "http://exist-db.org/foo" }
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('xmlns:foo="http://exist-db.org/foo"')
function ser:adaptive-xml-namespace-map-params() {
    let $input := namespace foo { "http://exist-db.org/foo" }
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals("Q{http://exist.sourceforge.net/NS/exist}test")
function ser:adaptive-qname() {
    ser:adaptive(xs:QName("exist:test"))
};

declare
    %test:assertEquals("Q{http://exist.sourceforge.net/NS/exist}test")
function ser:adaptive-qname-map-params() {
    ser:adaptive-map-params(xs:QName("exist:test"))
};

declare
    %test:assertEquals('[(1,2,3),"hello ""world""!",true(),false()]')
function ser:adaptive-array() {
    let $input := [(1 to 3), 'hello "world"!', true(), 1 = 0]
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('[(1,2,3),"hello ""world""!",true(),false()]')
function ser:adaptive-array-map-params() {
    let $input := [(1 to 3), 'hello "world"!', true(), 1 = 0]
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals('map{"k1":(1,2,3),"k2":map{"k3":a="abc","k4":[1,2]}}')
function ser:adaptive-map() {
    let $input := map {
        "k1": 1 to 3,
        "k2": map { "k3": $ser:test-xml/elem/@a, "k4": array { 1 to 2 } }
    }
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('map{"k1":(1,2,3),"k2":map{"k3":a="abc","k4":[1,2]}}')
function ser:adaptive-map-map-params() {
    let $input := map {
        "k1": 1 to 3,
        "k2": map { "k3": $ser:test-xml/elem/@a, "k4": array { 1 to 2 } }
    }
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals('1.0e0
3.141592653589793e0
2.543e1')
function ser:adaptive-double() {
    let $input := (xs:double(1), xs:double(math:pi()), xs:double(2.543e1))
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('1.0e0
3.141592653589793e0
2.543e1')
function ser:adaptive-double-map-params() {
    let $input := (xs:double(1), xs:double(math:pi()), xs:double(2.543e1))
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals('a="abc"
["a",2,&lt;b&gt;123&lt;/b&gt;,()]
3.141592653589793e0
math:pi#0
"hello"
"""quoted"""
true()
map{"k":()}')
function ser:adaptive-mixed() {
    let $input := (
        $ser:test-xml/elem/@a,
        ["a", 2, $ser:test-xml/elem/b, ()],
        xs:double(math:pi()),
        math:pi#0,
        "hello",
        '"quoted"',
        2 = 1+1,
        map:entry("k", ())
    )
    return
        ser:adaptive($input)
};

declare
    %test:assertEquals('a="abc"
["a",2,&lt;b&gt;123&lt;/b&gt;,()]
3.141592653589793e0
math:pi#0
"hello"
"""quoted"""
true()
map{"k":()}')
function ser:adaptive-mixed-map-params() {
    let $input := (
        $ser:test-xml/elem/@a,
        ["a", 2, $ser:test-xml/elem/b, ()],
        xs:double(math:pi()),
        math:pi#0,
        "hello",
        '"quoted"',
        2 = 1+1,
        map:entry("k", ())
    )
    return
        ser:adaptive-map-params($input)
};

declare
    %test:assertEquals("[]")
function ser:adaptive-empty-array() {
    ser:adaptive([])
};

declare
    %test:assertEquals("[]")
function ser:adaptive-empty-array-map-params() {
    ser:adaptive-map-params([])
};

declare
    %test:assertEquals("map{}")
function ser:adaptive-empty-map() {
    ser:adaptive(map { })
};

declare
    %test:assertEquals("map{}")
function ser:adaptive-empty-map-map-params() {
    ser:adaptive-map-params(map { })
};

declare
    %test:assertEquals("")
function ser:adaptive-empty-seq() {
    ser:adaptive(())
};

declare
    %test:assertEquals("")
function ser:adaptive-empty-seq-map-params() {
    ser:adaptive-map-params(())
};

declare
    %test:assertEquals("content")
function ser:adaptive-text-node() {
    ser:adaptive(<test>content</test>/text())
};

declare
    %test:assertEquals("content")
function ser:adaptive-text-node-map-params() {
    ser:adaptive-map-params(<test>content</test>/text())
};

declare
    %test:assertEquals("<!--comment-->")
function ser:adaptive-comment-node() {
    ser:adaptive(<!--comment-->)
};

declare
    %test:assertEquals("<!--comment-->")
function ser:adaptive-comment-node-map-params() {
    ser:adaptive-map-params(<!--comment-->)
};

declare
    %test:assertEquals("<?target instruction ?>")
function ser:adaptive-processing-instr() {
    ser:adaptive(<?target instruction ?>)
};

declare
    %test:assertEquals("<?target instruction ?>")
function ser:adaptive-processing-instr-map-params() {
    ser:adaptive-map-params(<?target instruction ?>)
};

declare
    %test:assertEquals('att-name="att-value"')
function ser:adaptive-attribute-node() {
    ser:adaptive(attribute att-name { "att-value" })
};

declare
    %test:assertEquals('att-name="att-value"')
function ser:adaptive-attribute-node-map-params() {
    ser:adaptive-map-params(attribute att-name { "att-value" })
};

declare
    %test:assertEquals('[xs:float("NaN")]')
function ser:adaptive-array-with-NaN() {
    ser:adaptive([ xs:float("NaN") ])
};

declare
    %test:assertEquals('[xs:float("NaN")]')
function ser:adaptive-array-with-NaN-map-params() {
    ser:adaptive-map-params([ xs:float("NaN") ])
};

declare
    %test:assertEquals('[xs:float("NaN")]')
function ser:adaptive-array-with-NaN() {
    ser:adaptive([ xs:float("NaN") ])
};

declare
    %test:assertEquals('[xs:float("NaN")]')
function ser:adaptive-array-with-NaN-map-params() {
    ser:adaptive-map-params([ xs:float("NaN") ])
};

declare
    %test:assertEquals('1-2-3-4-5')
function ser:adaptive-seq-with-item-separator() {
    ser:adaptive(1 to 5, "-")
};

declare
    %test:assertEquals('1-2-3-4-5')
function ser:adaptive-seq-with-item-separator-map-params() {
    ser:adaptive-map-params(1 to 5, "-")
};

declare
    %test:assertEquals('"the quick", "brown fox"')
function ser:adaptive-seq-with-item-separator2() {
    ser:adaptive(("the quick", "brown fox"), ", ")
};

declare
    %test:assertEquals('"the quick", "brown fox"')
function ser:adaptive-seq-with-item-separator2-map-params() {
    ser:adaptive-map-params(("the quick", "brown fox"), ", ")
};

declare
    %test:assertEquals('map{"a":("quotes ("")","apos (&apos;)")}')
function ser:adaptive-map-with-itemsep-no-quotes() {
    let $input := map{ "a":("quotes ("")", "apos (')") }
    return
        ser:adaptive($input, ",")
};

declare
    %test:assertEquals('map{"a":("quotes ("")","apos (&apos;)")}')
function ser:adaptive-map-with-itemsep-no-quotes-map-params() {
    let $input := map{ "a":("quotes ("")", "apos (')") }
    return
        ser:adaptive-map-params($input, ",")
};

declare
    %test:assertEquals('xs:dateTime("1999-05-31T13:20:00-05:00")')
function ser:adaptive-xs-date-time() {
    ser:adaptive(xs:dateTime('1999-05-31T13:20:00-05:00'))
};

declare
    %test:assertEquals('xs:dateTime("1999-05-31T13:20:00-05:00")')
function ser:adaptive-xs-date-time-map-params() {
    ser:adaptive-map-params(xs:dateTime('1999-05-31T13:20:00-05:00'))
};

declare
    %test:assertEquals('xs:duration("P1Y2M3DT10H30M23S")')
function ser:adaptive-xs-duration() {
    ser:adaptive(xs:duration("P1Y2M3DT10H30M23S"))
};

declare
    %test:assertEquals('xs:duration("P1Y2M3DT10H30M23S")')
function ser:adaptive-xs-duration-map-params() {
    ser:adaptive-map-params(xs:duration("P1Y2M3DT10H30M23S"))
};

declare
    %test:assertEquals('xs:float("1")')
function ser:adaptive-xs-float() {
    ser:adaptive(xs:float("1e0"))
};

declare
    %test:assertEquals('xs:float("1")')
function ser:adaptive-xs-float-map-params() {
    ser:adaptive-map-params(xs:float("1e0"))
};

declare
    %test:assertEquals('1.0e0')
function ser:adaptive-xs-double() {
    let $input := xs:double(1e0)
    return
        ser:adaptive($input, ",")
};

declare
    %test:assertEquals('1.0e0')
function ser:adaptive-xs-double-map-params() {
    let $input := xs:double(1e0)
    return
        ser:adaptive-map-params($input, ",")
};

declare
    %test:assertEquals('1.2,1,0,-1,0,0')
function ser:adaptive-xs-integers() {
    let $input := (xs:decimal(1.2), xs:integer(1), xs:nonPositiveInteger("0"),
        xs:negativeInteger(-1), xs:long(0), xs:int(0))
    return
        ser:adaptive($input, ",")
};

declare
    %test:assertEquals('1.2,1,0,-1,0,0')
function ser:adaptive-xs-integers-map-params() {
    let $input := (xs:decimal(1.2), xs:integer(1), xs:nonPositiveInteger("0"),
        xs:negativeInteger(-1), xs:long(0), xs:int(0))
    return
        ser:adaptive-map-params($input, ",")
};

declare
    %test:assertEquals('"""","""",""""')
function ser:adaptive-string-escaping() {
    let $input := ('"', xs:untypedAtomic('"'), xs:anyURI('"'))
    return
        ser:adaptive($input, ",")
};

declare
    %test:assertEquals('"""","""",""""')
function ser:adaptive-string-escaping-map-params() {
    let $input := ('"', xs:untypedAtomic('"'), xs:anyURI('"'))
    return
        ser:adaptive-map-params($input, ",")
};

declare
    %test:assertEquals('"en","en","en","en","en"')
function ser:adaptive-xs-strings() {
    let $input := (xs:normalizedString("en"), xs:token("en"), xs:language("en"), xs:ID("en"), xs:NCName("en"))
    return
        ser:adaptive($input, ",")
};

declare
    %test:assertEquals('"en","en","en","en","en"')
function ser:adaptive-xs-strings-map-params() {
    let $input := (xs:normalizedString("en"), xs:token("en"), xs:language("en"), xs:ID("en"), xs:NCName("en"))
    return
        ser:adaptive-map-params($input, ",")
};
