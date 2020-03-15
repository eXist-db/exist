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
xquery version "3.0";

(:~
 : Some tests on features of the test suite itself.
 :)
module namespace t="http://exist-db.org/xquery/test/xqsuite";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare 
    %test:assertXPath("/name[. = 'Item1']")
function t:xpath() {
    <item>
        <name>Item1</name>
    </item>
};

declare 
    %test:assertXPath("/t:name[. = 'Item1']")
function t:xpath-with-namespace() {
    <t:item>
        <t:name>Item1</t:name>
    </t:item>
};

declare 
    %test:assertXPath("/t:name/x:id[. = 'abc']")
function t:xpath-with-different-namespaces() {
    <t:item>
        <t:name><x:id xmlns:x="http://test.com/x">abc</x:id></t:name>
    </t:item>
};

declare 
    %test:assertXPath("declare namespace f='http://foo.com'; $result//f:name[. = 'Item1']")
function t:xpath-with-default-namespace() {
    <item xmlns="http://foo.com">
        <name>Item1</name>
    </item>
};

declare 
    %test:assertXPath("string-length($result) = 5")
function t:xpath-atomic-value() {
    "Hello"
};