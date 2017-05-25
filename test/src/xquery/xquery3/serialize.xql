xquery version "3.1";

module namespace ser="http://exist-db.org/xquery/test/serialize";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $ser:adaptive-opts :=
    <output:serialization-parameters
           xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
        <output:method value="adaptive"/>
      </output:serialization-parameters>;
      
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
    let $params := 
      <output:serialization-parameters
           xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
        <output:method value="xml"/>
        <output:indent value="yes"/>
      </output:serialization-parameters>
    return serialize($ser:atomic, $params)
};

declare 
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-no-method() {
    let $params := 
        <output:serialization-parameters xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
            <output:indent value="yes"/>
        </output:serialization-parameters>
    return serialize($ser:atomic, $params)
};

declare 
    %test:assertXPath("contains($result,'atomic')")
function ser:serialize-empty-params() {
    let $params := ()
    return serialize($ser:atomic, $params)
};

declare 
    %test:assertEquals("aaa bbb")
function ser:serialize-atomic() {
    let $nodes := ("aaa", "bbb")
    return
        serialize($nodes)
};

declare
    %test:assertEquals("")
function ser:serialize-empty-sequence() {
    let $nodes := ()
    return
        serialize($nodes)
};

declare 
    %test:args(1234)
    %test:assertEquals('"1234"')
    %test:args('Hello "world"!')
    %test:assertEquals('"Hello ""world""!"')
function ser:adaptive-simple-atomic($atomic as xs:anyAtomicType) {
    serialize($atomic, $ser:adaptive-opts)
};

declare 
    %test:assertEquals("fn:substring#3
math:pi#0
fn:exists#1
Q{http://exist-db.org/xquery/lucene}query#2
(anonymous-function)#1
Q{http://exist-db.org/xquery/test/serialize}adaptive-simple-atomic#1")
function ser:adaptive-function-item() {
    let $input := (
        substring#3,
        math:pi#0,
        Q{http://www.w3.org/2005/xpath-functions}exists#1,
        ft:query#2,
        function ($a) { $a },
        ser:adaptive-simple-atomic#1
    )
    return
        serialize($input, $ser:adaptive-opts)
};

declare 
    %test:assertEquals('<?pi?><elem a="abc"><!--comment--><b>123</b></elem>
<elem a="abc"><!--comment--><b>123</b></elem>
a="abc"
<!--comment-->')
function ser:adaptive-xml() {
    let $input := ($ser:test-xml, $ser:test-xml/elem, $ser:test-xml/elem/@a, $ser:test-xml/elem/comment())
    return
        serialize($input, $ser:adaptive-opts)
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
        serialize($input, $ser:adaptive-opts)
};

declare 
    %test:assertEquals('xmlns:foo="http://exist-db.org/foo"')
function ser:adaptive-xml-namespace() {
    let $input := namespace foo { "http://exist-db.org/foo" }
    return
        serialize($input, $ser:adaptive-opts)
};

declare
    %test:assertEquals("Q{http://exist.sourceforge.net/NS/exist}test")
function ser:adaptive-qname() {
    serialize(xs:QName("exist:test"), $ser:adaptive-opts)
};

declare 
    %test:assertEquals('[(1,2,3),"hello ""world""!",true(),false()]')
function ser:adaptive-array() {
    let $input := [(1 to 3), 'hello "world"!', true(), 1 = 0]
    return
        serialize($input, $ser:adaptive-opts)
};

declare 
    %test:assertEquals('map{"k1":(1,2,3),"k2":map{"k3":a="abc","k4":[1,2]}}')
function ser:adaptive-map() {
    let $input := map {
        "k1": 1 to 3,
        "k2": map { "k3": $ser:test-xml/elem/@a, "k4": array { 1 to 2 } }
    }
    return
        serialize($input, $ser:adaptive-opts)
};

declare 
    %test:assertEquals('1.0e0
3.141592653589793e0
2.543e1')
function ser:adaptive-double() {
    let $input := (xs:double(1), xs:double(math:pi()), xs:double(2.543e1))
    return
        serialize($input, $ser:adaptive-opts)
};