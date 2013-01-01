xquery version "3.0";

module namespace ser="http://exist-db.org/xquery/test/serialize";

declare namespace test="http://exist-db.org/xquery/xqsuite";

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