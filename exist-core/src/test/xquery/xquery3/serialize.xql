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

module namespace ser="http://exist-db.org/xquery/test/serialize";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";

declare %private variable $ser:opt-map-html5 :=
    map {
        "method": "html",
        "html-version": 5.0
    }
;

declare %private variable $ser:opt-xml-adaptive-no-indent :=
    <output:serialization-parameters>
        <output:method>adaptive</output:method>
        <output:indent>no</output:indent>
    </output:serialization-parameters>
;

declare %private function ser:opt-xml-with-separator($item-separator as xs:string) {
    <output:serialization-parameters>
        <output:item-separator>{$item-separator}</output:item-separator>
        <output:method>adaptive</output:method>
        <output:indent>no</output:indent>
    </output:serialization-parameters>
};

declare %private variable $ser:opt-map-adaptive-no-indent := map {
    "method": "adaptive",
    "indent": false()
};

declare %private function ser:opt-map-with-separator($item-separator as xs:string) {
    map:put($ser:opt-map-adaptive-no-indent, "item-separator", $item-separator)
};

declare variable $ser:atomic :=
    <atomic:root
        xmlns:atomic="http://www.w3.org/XQueryTest"
        xmlns:foo="http://www.example.com/foo"
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
    </atomic:root>
;


declare variable $ser:test-xsl := document {
    <?xml-stylesheet href="xmldb:exist:///db/serialization-test/test.xsl" type="text/xsl"?>,
    <elem a="abc"><!--comment--><b>123</b></elem>
};

declare variable $ser:xsl := document {
    <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
    <xsl:template match="b">processed</xsl:template>
    </xsl:stylesheet>
};

declare variable $ser:test-xml := document {
    <?pi?>,
    <elem a="abc"><!--comment--><b>123</b></elem>
};

declare variable $ser:mixed := (
    $ser:test-xml/elem/@a,
    ["a", 2, $ser:test-xml/elem/b, ()],
    xs:double(math:pi()),
    math:pi#0,
    "hello",
    '"quoted"',
    2 = 1+1,
    map:entry("k", ())
);

declare variable $ser:function-items := (
    substring#3,
    math:pi#0,
    Q{http://www.w3.org/2005/xpath-functions}exists#1,
    function ($a) { $a },
    ser:adaptive-simple-atomic#1
);

declare variable $ser:nested-map := map {
    "k1": 1 to 3,
    "k2": map {
        "k3": $ser:test-xml/elem/@a,
        "k4": array { 1 to 2 }
    }
};

declare variable $ser:doubles := (
    xs:double(1), xs:double(math:pi()), xs:double(2.543e1)
);

declare variable $ser:strings := (
    xs:normalizedString("en"), xs:token("en"),
    xs:language("en"), xs:ID("en"), xs:NCName("en")
);

declare variable $ser:decimals := (
    xs:decimal(1.2), xs:integer(1), xs:nonPositiveInteger("0"),
    xs:negativeInteger(-1), xs:long(0), xs:int(0)
);

declare variable $ser:test-xml-with-doctype := '<!DOCTYPE bookmap PUBLIC "-//OASIS//DTD DITA BookMap//EN" "bookmap.dtd"><bookmap id="bookmap-1"/>';

declare variable $ser:collection-name := "serialization-test";

declare variable $ser:collection := "/db/" || $ser:collection-name;

declare variable $ser:xi-doc := document {
    <article xmlns:xi="http://www.w3.org/2001/XInclude">
        <title>My Title</title>
        <xi:include href="{$ser:collection}/test.xml"/>
    </article>
};

declare variable $ser:in-memory-book :=
    <book>
        <author>John Doe</author>
        <author>Robert Smith</author>
    </book>
;

declare
    %test:setUp
function ser:setup() {
    xmldb:create-collection("/db", $ser:collection-name),
    xmldb:store($ser:collection, "test.xml", $ser:test-xml),
    xmldb:store($ser:collection, "test-xsl.xml", $ser:test-xsl),
    xmldb:store($ser:collection, "test.xsl", $ser:xsl),
    xmldb:store($ser:collection, "test-with-doctype.xml", $ser:test-xml-with-doctype)
};

declare
    %test:tearDown
function ser:teardown() {
    xmldb:remove($ser:collection)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-element() {
    serialize($ser:atomic)
};

declare
    %test:assertError
function ser:serialize-attribute() {
    serialize(($ser:atomic//@*)[1])
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-with-params() {
    serialize(
        $ser:atomic,
        <output:serialization-parameters>
            <output:method value="xml"/>
            <output:indent value="yes"/>
        </output:serialization-parameters>
    )
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-with-params-map-params() {
    serialize($ser:atomic,
        map { "method": "xml", "indent": true() })
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-no-method() {
    serialize($ser:atomic,
        <output:serialization-parameters />)
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-no-method-map-params() {
    serialize($ser:atomic, map{})
};

declare
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-empty-params() {
    serialize($ser:atomic, ())
};

declare
    %test:assertEquals("aaa bbb")
function ser:serialize-atomic() {
    serialize(("aaa", "bbb"))
};

(: test for https://github.com/eXist-db/exist/issues/4704 :)
declare
    %test:assertEquals("aaabbb")
function ser:serialize-atomic-empty-separator() {
    serialize(("aaa", "bbb"), map { "item-separator": "" })
};

(: test for https://github.com/eXist-db/exist/issues/4704 :)
declare
    %test:assertEquals("aaabbb")
function ser:serialize-atomic-empty-separator-xml-options() {
    serialize(("aaa", "bbb"),
        <output:serialization-parameters>
            <output:item-separator value=""/>
        </output:serialization-parameters>
    )
};

declare
    %test:assertEquals("")
function ser:serialize-empty-sequence() {
    serialize(())
};

declare
    %test:args(1234)
    %test:assertEquals('1234')
    %test:args('1234')
    %test:assertEquals('"1234"')
    %test:args('Hello "world"!')
    %test:assertEquals('"Hello ""world""!"')
function ser:adaptive-simple-atomic($atomic as xs:anyAtomicType) {
    serialize($atomic, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:args(1234)
    %test:assertEquals('1234')
    %test:args('1234')
    %test:assertEquals('"1234"')
    %test:args('Hello "world"!')
    %test:assertEquals('"Hello ""world""!"')
function ser:adaptive-simple-atomic-map-params($atomic as xs:anyAtomicType) {
    serialize($atomic, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("fn:substring#3
math:pi#0
fn:exists#1
(anonymous-function)#1
Q{http://exist-db.org/xquery/test/serialize}adaptive-simple-atomic#1")
function ser:adaptive-function-item() {
    serialize($ser:function-items, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("fn:substring#3
math:pi#0
fn:exists#1
(anonymous-function)#1
Q{http://exist-db.org/xquery/test/serialize}adaptive-simple-atomic#1")
function ser:adaptive-function-item-map-params() {
    serialize($ser:function-items, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml() {
    serialize(($ser:test-xml, $ser:test-xml/elem, $ser:test-xml/elem/@a, $ser:test-xml/elem/comment()),
        $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml-map-params() {
    serialize(($ser:test-xml, $ser:test-xml/elem, $ser:test-xml/elem/@a, $ser:test-xml/elem/comment()),
        $ser:opt-map-adaptive-no-indent)
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
        serialize($input, $ser:opt-xml-adaptive-no-indent)
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
        serialize($input, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('xmlns:foo="http://exist-db.org/foo"')
function ser:adaptive-xml-namespace() {
    serialize(namespace foo { "http://exist-db.org/foo" }, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('xmlns:foo="http://exist-db.org/foo"')
function ser:adaptive-xml-namespace-map-params() {
    serialize(namespace foo { "http://exist-db.org/foo" }, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("Q{http://exist.sourceforge.net/NS/exist}test")
function ser:adaptive-qname() {
    serialize(xs:QName("exist:test"), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("Q{http://exist.sourceforge.net/NS/exist}test")
function ser:adaptive-qname-map-params() {
    serialize(xs:QName("exist:test"), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('[(1,2,3),"hello ""world""!",true(),false()]')
function ser:adaptive-array() {
    serialize([(1 to 3), 'hello "world"!', true(), 1 = 0],
        $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('[(1,2,3),"hello ""world""!",true(),false()]')
function ser:adaptive-array-map-params() {
    serialize([(1 to 3), 'hello "world"!', true(), 1 = 0],
        $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('map{"k1":(1,2,3),"k2":map{"k3":a="abc","k4":[1,2]}}')
function ser:adaptive-map() {
    serialize($ser:nested-map, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('map{"k1":(1,2,3),"k2":map{"k3":a="abc","k4":[1,2]}}')
function ser:adaptive-map-map-params() {
    serialize($ser:nested-map, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('1.0e0
3.141592653589793e0
2.543e1')
function ser:adaptive-double() {
    serialize($ser:doubles, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('1.0e0
3.141592653589793e0
2.543e1')
function ser:adaptive-double-map-params() {
    serialize($ser:doubles, $ser:opt-map-adaptive-no-indent)
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
    serialize($ser:mixed, $ser:opt-xml-adaptive-no-indent)
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
    serialize($ser:mixed, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("[]")
function ser:adaptive-empty-array() {
    serialize([], $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("[]")
function ser:adaptive-empty-array-map-params() {
    serialize([], $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("map{}")
function ser:adaptive-empty-map() {
    serialize(map {}, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("map{}")
function ser:adaptive-empty-map-map-params() {
    serialize(map {}, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("")
function ser:adaptive-empty-seq() {
    serialize((), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("")
function ser:adaptive-empty-seq-map-params() {
    serialize((), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("content")
function ser:adaptive-text-node() {
    serialize(<test>content</test>/text(), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("content")
function ser:adaptive-text-node-map-params() {
    serialize(<test>content</test>/text(), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("<!--comment-->")
function ser:adaptive-comment-node() {
    serialize(<!--comment-->, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("<!--comment-->")
function ser:adaptive-comment-node-map-params() {
    serialize(<!--comment-->, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals("<?target instruction ?>")
function ser:adaptive-processing-instr() {
    serialize(<?target instruction ?>, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals("<?target instruction ?>")
function ser:adaptive-processing-instr-map-params() {
    serialize(<?target instruction ?>, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('att-name="att-value"')
function ser:adaptive-attribute-node() {
    serialize(attribute att-name { "att-value" }, $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('att-name="att-value"')
function ser:adaptive-attribute-node-map-params() {
    serialize(attribute att-name { "att-value" }, $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('[xs:float("NaN")]')
function ser:adaptive-array-with-NaN() {
    serialize([ xs:float("NaN") ], $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('[xs:float("NaN")]')
function ser:adaptive-array-with-NaN-map-params() {
    serialize([ xs:float("NaN") ], $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('1-2-3-4-5')
function ser:adaptive-seq-with-item-separator() {
    serialize(1 to 5, ser:opt-xml-with-separator("-"))
};

declare
    %test:assertEquals('1-2-3-4-5')
function ser:adaptive-seq-with-item-separator-map-params() {
    serialize(1 to 5, ser:opt-map-with-separator("-"))
};

declare
    %test:assertEquals('"the quick", "brown fox"')
function ser:adaptive-seq-with-item-separator2() {
    serialize(("the quick", "brown fox"), ser:opt-xml-with-separator(", "))
};

declare
    %test:assertEquals('"the quick", "brown fox"')
function ser:adaptive-seq-with-item-separator2-map-params() {
    serialize(("the quick", "brown fox"), ser:opt-map-with-separator(", "))
};

declare
    %test:assertEquals('map{"a":("quotes ("")","apos (&apos;)")}')
function ser:adaptive-map-with-itemsep-no-quotes() {
    serialize(map{ "a":("quotes ("")", "apos (')") }, ser:opt-xml-with-separator(","))
};

declare
    %test:assertEquals('map{"a":("quotes ("")","apos (&apos;)")}')
function ser:adaptive-map-with-itemsep-no-quotes-map-params() {
    serialize(map{ "a":("quotes ("")", "apos (')") }, ser:opt-map-with-separator(","))
};

declare
    %test:assertEquals('xs:dateTime("1999-05-31T13:20:00-05:00")')
function ser:adaptive-xs-date-time() {
    serialize(xs:dateTime('1999-05-31T13:20:00-05:00'), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('xs:dateTime("1999-05-31T13:20:00-05:00")')
function ser:adaptive-xs-date-time-map-params() {
    serialize(xs:dateTime('1999-05-31T13:20:00-05:00'), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('xs:duration("P1Y2M3DT10H30M23S")')
function ser:adaptive-xs-duration() {
    serialize(xs:duration("P1Y2M3DT10H30M23S"), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('xs:duration("P1Y2M3DT10H30M23S")')
function ser:adaptive-xs-duration-map-params() {
    serialize(xs:duration("P1Y2M3DT10H30M23S"), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('xs:float("1")')
function ser:adaptive-xs-float() {
    serialize(xs:float("1e0"), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('xs:float("1")')
function ser:adaptive-xs-float-map-params() {
    serialize(xs:float("1e0"), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('1.0e0')
function ser:adaptive-xs-double() {
    serialize(xs:double(1e0), $ser:opt-xml-adaptive-no-indent)
};

declare
    %test:assertEquals('1.0e0')
function ser:adaptive-xs-double-map-params() {
    serialize(xs:double(1e0), $ser:opt-map-adaptive-no-indent)
};

declare
    %test:assertEquals('1.2,1,0,-1,0,0')
function ser:adaptive-xs-integers() {
    serialize($ser:decimals, ser:opt-xml-with-separator(","))
};

declare
    %test:assertEquals('1.2,1,0,-1,0,0')
function ser:adaptive-xs-integers-map-params() {
    serialize($ser:decimals, ser:opt-map-with-separator(","))
};

declare
    %test:assertEquals('"""","""",""""')
function ser:adaptive-string-escaping() {
    serialize(('"', xs:untypedAtomic('"'), xs:anyURI('"')), ser:opt-xml-with-separator(","))
};

declare
    %test:assertEquals('"""","""",""""')
function ser:adaptive-string-escaping-map-params() {
    serialize(('"', xs:untypedAtomic('"'), xs:anyURI('"')), ser:opt-map-with-separator(","))
};

declare
    %test:assertEquals('"en","en","en","en","en"')
function ser:adaptive-xs-strings() {
    serialize($ser:strings, ser:opt-xml-with-separator(","))
};

declare
    %test:assertEquals('"en","en","en","en","en"')
function ser:adaptive-xs-strings-map-params() {
    serialize($ser:strings, ser:opt-map-with-separator(","))
};

declare
    %test:args("true")
    %test:assertXPath("contains($result, '-//OASIS//DTD DITA BookMap//EN') and contains($result, 'bookmap.dtd')")
    %test:args("false")
    %test:assertXPath("not(contains($result, '-//OASIS//DTD DITA BookMap//EN')) and not(contains($result, 'bookmap.dtd'))")
function ser:exist-output-doctype-QName($value as xs:boolean) {
    serialize(doc($ser:collection || "/test-with-doctype.xml"),
        map { xs:QName("exist:output-doctype") : $value })
};

declare
    %test:args("true")
    %test:assertXPath("contains($result, '-//OASIS//DTD DITA BookMap//EN') and contains($result, 'bookmap.dtd')")
    %test:args("false")
    %test:assertXPath("not(contains($result, '-//OASIS//DTD DITA BookMap//EN')) and not(contains($result, 'bookmap.dtd'))")
function ser:exist-output-doctype-string($value as xs:boolean) {
    serialize(doc($ser:collection || "/test-with-doctype.xml"),
        map { "exist:output-doctype" : $value })
};

declare
    %test:args("true")
    %test:assertXPath("contains($result, 'comment')")
    %test:args("false")
    %test:assertXPath("contains($result, 'include')")
function ser:exist-expand-xinclude-QName($value as xs:boolean) {
    serialize($ser:xi-doc,
        map { xs:QName("exist:expand-xincludes"): $value })
};

declare
    %test:args("true")
    %test:assertXPath("contains($result, 'comment')")
    %test:args("false")
    %test:assertXPath("contains($result, 'include')")
function ser:exist-expand-xinclude-string($value as xs:boolean) {
    serialize($ser:xi-doc,
        map { "exist:expand-xincludes": $value })
};

declare
    %test:args("all")
    %test:assertEquals('<?pi?><elem xmlns:exist="http://exist.sourceforge.net/NS/exist" exist:id="2" exist:source="test.xml" a="abc"><!--comment--><b exist:id="2.3">123</b></elem>')
    %test:args("element")
    %test:assertEquals('<?pi?><elem xmlns:exist="http://exist.sourceforge.net/NS/exist" exist:id="2" exist:source="test.xml" a="abc"><!--comment--><b>123</b></elem>')
    %test:args("none")
    %test:assertXPath("not(contains($result, 'exist:id'))")
function ser:exist-add-exist-id-QName($value as xs:string) {
    serialize(doc($ser:collection || "/test.xml"),
        map { xs:QName("exist:add-exist-id"): $value })
};

declare
    %test:args("all")
    %test:assertEquals('<?pi?><elem xmlns:exist="http://exist.sourceforge.net/NS/exist" exist:id="2" exist:source="test.xml" a="abc"><!--comment--><b exist:id="2.3">123</b></elem>')
    %test:args("element")
    %test:assertEquals('<?pi?><elem xmlns:exist="http://exist.sourceforge.net/NS/exist" exist:id="2" exist:source="test.xml" a="abc"><!--comment--><b>123</b></elem>')
    %test:args("none")
    %test:assertXPath("not(contains($result, 'exist:id'))")
function ser:exist-add-exist-id-string($value as xs:string) {
    serialize(doc($ser:collection || "/test.xml"),
        map { "exist:add-exist-id": $value })
};

declare
    %test:args("functionName")
    %test:assertEquals('functionName({"author":["John Doe","Robert Smith"]})')
    %test:args("anotherName")
    %test:assertEquals('anotherName({"author":["John Doe","Robert Smith"]})')
function ser:exist-jsonp-QName($value as xs:string) {
    serialize($ser:in-memory-book,
        map {
            "method": "json",
            "media-type": "application/json",
            xs:QName("exist:jsonp"): $value
        }
    )
};

declare
    %test:args("functionName")
    %test:assertEquals('functionName({"author":["John Doe","Robert Smith"]})')
    %test:args("anotherName")
    %test:assertEquals('anotherName({"author":["John Doe","Robert Smith"]})')
function ser:exist-jsonp-string($value as xs:string) {
    serialize($ser:in-memory-book,
        map {
            "method": "json",
            "media-type": "application/json",
            "exist:jsonp": $value
        }
    )
};

declare
    %test:args("true")
    %test:assertEquals('processed')
    %test:args("false")
    %test:assertXPath("contains($result, 'stylesheet')")
function ser:exist-process-xsl-pi-QName($value as xs:boolean) {
    serialize(doc($ser:collection || "/test-xsl.xml"),
        map { xs:QName("exist:process-xsl-pi"): $value })
};

declare
    %test:args("true")
    %test:assertEquals('processed')
    %test:args("false")
    %test:assertXPath("contains($result, 'stylesheet')")
function ser:exist-process-xsl-pi-string($value as xs:boolean) {
    serialize(doc($ser:collection || "/test-xsl.xml"),
        map { "exist:process-xsl-pi": $value })
};

declare
    %test:args("text")
    %test:assertEquals("1--2")
    %test:args("html")
    %test:assertEquals("1--2")
    %test:args("xhtml")
    %test:assertEquals("1--2")
    %test:args("xml")
    %test:assertEquals("1--2")
    %test:args("adaptive")
    %test:assertEquals("1--2")
function ser:item-separator-with-method($method as xs:string) {
    serialize((1, 2),
        <output:serialization-parameters>
            <output:method>{$method}</output:method>
            <output:item-separator>--</output:item-separator>
        </output:serialization-parameters>
    )
};

declare
    %test:assertEquals("1|2|3|4|5|6|7|8|9|10")
function ser:serialize-xml-033() {
    let $params :=
        <output:serialization-parameters>
            <output:method value="xml"/>
            <output:item-separator value="|"/>
        </output:serialization-parameters>
    return serialize(1 to 10, $params)
};

declare
    %test:assertEquals("1|2|3|4|5|6|7|8|9|10")
function ser:serialize-xml-133() {
    serialize(1 to 10, map { "method": "xml", "item-separator": "|" })
};

declare
    %test:assertEquals("1==2==3==4")
function ser:serialize-xml-034() {
    serialize(1 to 4,
        <output:serialization-parameters>
            <output:method value="xml"/>
            <output:omit-xml-declaration value="yes"/>
            <output:item-separator value="=="/>
        </output:serialization-parameters>
    )
};

declare
    %test:assertEquals("1==2==3==4")
function ser:serialize-xml-134() {
    serialize((1 to 4) ! text { . },
        map {
            "method": "xml",
            "omit-xml-declaration": true(),
            "item-separator": "=="
        }
    )
};

declare
    %test:assertEquals('<!DOCTYPE html><option selected></option>')
function ser:serialize-html-5-boolean-attribute-names() {
    serialize(<option selected="selected"/>, $ser:opt-map-html5)
};

declare
    %test:assertEquals('<!DOCTYPE html><br>')
function ser:serialize-html-5-empty-tags() {
    serialize(<br/>, $ser:opt-map-html5)
};

(: test for https://github.com/eXist-db/exist/issues/4736 :)
declare
    %test:assertEquals('<!DOCTYPE html>&#10;<html>&#10;    <body>&#10;        <p>hi</p>&#10;    </body>&#10;</html>')
function ser:serialize-html-5-with-indent() {
    serialize(<html><body><p>hi</p></body></html>,
        map{ "method": "html", "version": "5.0", "indent": true() })
};

declare
    %test:assertEquals('<!DOCTYPE html><html><body><style>ul > li { color:red; }</style><script>if (a < b) foo()</script></body></html>')
function ser:serialize-html-5-raw-text-elements-body() {
    <html>
        <body>
            <style><![CDATA[ul > li { color:red; }]]></style>
            <script><![CDATA[if (a < b) foo()]]></script>
        </body>
    </html>
   => serialize($ser:opt-map-html5)
};

declare
    %test:assertEquals('<!DOCTYPE html><html><head><style>ul > li { color:red; }</style><script>if (a < b) foo()</script></head><body></body></html>')
function ser:serialize-html-5-raw-text-elements-head() {
    <html>
        <head>
            <style><![CDATA[ul > li { color:red; }]]></style>
            <script><![CDATA[if (a < b) foo()]]></script>
        </head>
        <body></body>
    </html>
   => serialize($ser:opt-map-html5)
};

declare
    %test:assertEquals('<!DOCTYPE html><html><head><title>XML &amp;gt; JSON</title></head><body><textarea>if (a &amp;lt; b) foo()</textarea></body></html>')
function ser:serialize-html-5-needs-escape-elements() {
    <html>
        <head>
            <title><![CDATA[XML > JSON]]></title>
        </head>
        <body>
            <textarea><![CDATA[if (a < b) foo()]]></textarea>
        </body>
    </html>
    => serialize($ser:opt-map-html5)
};

(: test for https://github.com/eXist-db/exist/issues/4702 :)
declare
    %test:assertEquals("<a>foo</a> <b>bar</b>")
function ser:sequence-of-nodes() {
    serialize((<a>foo</a>, <b>bar</b>))
};

declare
    %test:assertEquals("[|]")
function ser:sequence-skip-empty-text-node() {
    serialize((<a>[</a>, <a> </a>, <a>]</a>)/text(), map { "item-separator": "|" })
};

declare
    %test:assertEquals("||")
function ser:sequence-dont-skip-empty-string() {
    serialize(("", "", ""), map { "item-separator": "|" })
};

declare
    %test:assertEquals("foo")
function ser:skip-empty-no-separator() {
    serialize((<a>foo</a>, <b></b>)/text(), map {"item-separator": "!" })
};

declare
    %test:assertEquals("")
function ser:empty-array-serializes-to-empty-string() {
    serialize([])
};

declare
    %test:assertEquals("")
function ser:array-with-members-serializes-to-empty-string() {
    serialize(["", ()])
};

declare
    %test:assertEquals("")
function ser:sequence-of-empty-arrays-serializes-to-empty-string() {
    serialize(([],[]), map { "item-separator": "|" })
};

declare
    %test:assertEquals("1|2")
function ser:item-separator-applies-to-array-members() {
    serialize([1,2], map { "item-separator": "|" })
};
