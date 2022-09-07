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

declare variable $testSerialize:atomic := document {
    <atomic:root xmlns:atomic="http://www.w3.org/XQueryTest" xmlns:foo="http://www.example.com/foo"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <atomic:anyURI>http://www.example.com</atomic:anyURI>
      <atomic:NCName atomic:attr="aNCname">aNCname</atomic:NCName>
      <atomic:QName atomic:attr="foo:aQname">foo:aQname</atomic:QName>
    </atomic:root>
};

declare variable $testSerialize:params-007 :=
              <output:serialization-parameters
                   xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
                <output:method value="xml"/>
                <output:indent value="yes"/>
                <output:xindent value="yes" xmlns:vendor="http://vendor.example.com/"/>
              </output:serialization-parameters>;

declare variable $testSerialize:params-007a :=
              <output:serialization-parameters
                   xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
                <output:method value="xml"/>
                <output:indent value="yes"/>
                <vendor:xindent value="yes" xmlns:vendor="http://vendor.example.com/"/>
              </output:serialization-parameters>;

declare
    %test:assertTrue
function testSerialize:serialize-xml-107() {
    let $result := serialize($testSerialize:atomic,map {
        "method" : "xml",
        "indent" : true(),
        "xindent" : true()
    })
    return contains($result,'atomic')
};

(: an invalid param in the standard ("output") namespace is an error :)
declare
    %test:assertError("err:SEPM0017")
function testSerialize:serialize-xml-007() {
    let $result := serialize($testSerialize:atomic,$testSerialize:params-007)
    return contains($result,'atomic')
};

(: an invalid param in a nonstandard ("vendor") namespace is NOT an error - just ignored :)
declare
    %test:assertTrue
function testSerialize:serialize-xml-007a() {
    let $result := serialize($testSerialize:atomic,$testSerialize:params-007a)
    return contains($result,'atomic')
};

