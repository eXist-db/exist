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

module namespace testSerialize="http://exist-db.org/xquery/test/function_serialize";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $testSerialize:serialize-032-src := document {
  <root>
      <title>A document</title>
      <p>A paragraph containing a character $ which should be mapped to a different one</p>
  </root>
};

declare variable $testSerialize:atomic := document {
    <atomic:root xmlns:atomic="http://www.w3.org/XQueryTest" xmlns:foo="http://www.example.com/foo"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <atomic:anyURI>http://www.example.com</atomic:anyURI>
      <atomic:NCName atomic:attr="aNCname">aNCname</atomic:NCName>
      <atomic:QName atomic:attr="foo:aQname">foo:aQname</atomic:QName>
    </atomic:root>
};

declare
    %test:assertEquals("<root><title>A document</title><p>A paragraph containing a character £ which should be mapped to a different one</p></root>")
function testSerialize:use_character_maps() {
    let $result := serialize($testSerialize:serialize-032-src,
    map {
                                                                "allow-duplicate-names" : true(),
                                                                "use-character-maps" : map { "$":"£", "*":"^^"}
                                                                })
    return $result
};

declare
    %test:assertTrue
function testSerialize:use_character_maps-032-params-as-map() {
    let $params := map { "use-character-maps" : map { "N":"£", "Q":"$$"} }
    let $result := serialize($testSerialize:atomic, $params)
    return contains($result, "foo:a$$name")
};
