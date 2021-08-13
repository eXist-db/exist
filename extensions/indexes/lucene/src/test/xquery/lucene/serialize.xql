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

declare variable $ser:test-xml-collection-xconf := document {
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="b"/>
            </lucene>
        </index>
    </collection>
};
declare variable $ser:collection-name := "serialization-test";

declare variable $ser:collection := "/db/" || $ser:collection-name;

declare
    %test:setUp
function ser:setup() {
    xmldb:create-collection("/db", "system"),
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $ser:collection-name),
    xmldb:store("/db/system/config/db/serialization-test", "collection.xconf", $ser:test-xml-collection-xconf),
    xmldb:create-collection("/db", $ser:collection-name),
    xmldb:store($ser:collection, "test.xml", $ser:test-xml)
};

declare
    %test:tearDown
function ser:teardown() {
    xmldb:remove($ser:collection)
};

declare
    %test:assertXPath("contains($result, 'exist:id')")
function ser:exist-add-exist-id-all() {
    let $doc := doc($ser:collection || "/test.xml")
    return fn:serialize($doc, map { xs:QName("exist:add-exist-id"): "all" })
};

declare
    %test:assertXPath("contains($result, 'exist:id')")
function ser:exist-add-exist-id-element() {
    let $doc := doc($ser:collection || "/test.xml")
    return fn:serialize($doc, map { xs:QName("exist:add-exist-id"): "element" })
};

declare
     %test:assertXPath("not(contains($result, 'exist:id'))")
function ser:exist-add-exist-id-none() {
    let $doc := doc($ser:collection || "/test.xml")
    return fn:serialize($doc, map { xs:QName("exist:add-exist-id"): "none" })
};

declare
     %test:assertXPath("contains($result, 'exist:match')")
function ser:exist-highlight-matches-both() {
    let $doc := doc($ser:collection || "/test.xml")
    for $hit in $doc//b[ft:query(., "123")]
    return fn:serialize($hit, map { xs:QName("exist:highlight-matches"): "both" })
};

declare
     %test:assertXPath("contains($result, 'exist:match')")
function ser:exist-highlight-matches-elements() {
    let $doc := doc($ser:collection || "/test.xml")
    for $hit in $doc//b[ft:query(., "123")]
    return fn:serialize($hit, map { xs:QName("exist:highlight-matches"): "elements" })
};

declare
     %test:assertXPath("contains($result, 'exist:match')")
function ser:exist-highlight-matches-attributes() {
    let $doc := doc($ser:collection || "/test.xml")
    for $hit in $doc//b[ft:query(., "123")]
    return fn:serialize($hit, map { xs:QName("exist:highlight-matches"): "attributes" })
};

declare
     %test:assertXPath("not(contains($result, 'exist:match'))")
function ser:exist-highlight-matches-none() {
    let $doc := doc($ser:collection || "/test.xml")
    for $hit in $doc//b[ft:query(., "123")]
    return fn:serialize($hit, map { xs:QName("exist:highlight-matches"): "none" })
};