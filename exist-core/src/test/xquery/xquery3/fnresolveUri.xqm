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

module namespace testResolveURI="http://exist-db.org/xquery/test/fnResolveURI";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("https:/one/two/boo")
function testResolveURI:resolve-path() {
    fn:resolve-uri("boo", "https:///one/two/three")
};

declare
    %test:assertEquals("https:/one/two/three#boo")
function testResolveURI:resolve-frag() {
    fn:resolve-uri("#boo", "https:///one/two/three")
};

declare
    %test:assertEquals("https://host.com#boo")
function testResolveURI:resolve-frag-only() {
    fn:resolve-uri("#boo", "https://host.com")
};

declare
    %test:assertError("err:FORG0002") (: non-existent path, invalid arg :)
function testResolveURI:resolve-frag-only-nopath() {
    fn:resolve-uri("#boo", "https://")
};

declare
    %test:assertEquals("https:/#boo") (: why does the // disappear ? :)
function testResolveURI:resolve-frag-only-abspath() {
    fn:resolve-uri("#boo", "https:///")
};

declare
    %test:assertEquals("https://host.com/#boo")
function testResolveURI:resolve-frag-only2() {
    fn:resolve-uri("#boo", "https://host.com/")
};

declare
    %test:assertEquals("https:/one/two/four#boo")
function testResolveURI:resolve-path-frag() {
    fn:resolve-uri("four#boo", "https:///one/two/three")
};

declare
    %test:assertEquals("https:/one/two/three/four#boo")
function testResolveURI:resolve-path-frag2() {
    fn:resolve-uri("four#boo", "https:///one/two/three/")
};

declare
    %test:assertEquals("https://one/two/boo")
function testResolveURI:resolve-rel() {
    fn:resolve-uri("boo", "https://one/two/three")
};

declare
    %test:assertEquals("https://one/two/boo.xml")
function testResolveURI:resolve-rel-file() {
    fn:resolve-uri("boo.xml", "https://one/two/three")
};

declare
    %test:assertEquals("xmldb:exist://one/two/boo.xml")
function testResolveURI:resolve-rel-file-x() {
    fn:resolve-uri("boo.xml", "xmldb:exist://one/two/three")
};

declare
    %test:assertEquals("https://alpha/beta/gamma")
function testResolveURI:resolve-abs() {
    fn:resolve-uri("https://alpha/beta/gamma", "xmldb:exist://one/two/three")
};

declare
    %test:assertEquals("xmldb:exist://alpha/beta/gamma")
function testResolveURI:resolve-abs2() {
    fn:resolve-uri("xmldb:exist://alpha/beta/gamma", "https://one/two/three")
};

declare
    %test:assertEquals("xmldb://alpha/beta/gamma")
function testResolveURI:resolve-abs3() {
    fn:resolve-uri("xmldb://alpha/beta/gamma", "xmldb:exist://one/two/three")
};

declare
    %test:assertEquals("exist://alpha/beta/gamma")
function testResolveURI:resolve-abs4() {
    fn:resolve-uri("exist://alpha/beta/gamma", "xmldb:exist://one/two/three")
};

declare
    %test:assertEquals("//alpha/beta/gamma")
function testResolveURI:resolve-rel-rel() {
    fn:resolve-uri("//alpha/beta/gamma", "//one/two/three")
};
