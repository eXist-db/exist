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

(:~
 : Test fn:unparsed-text and friends
 :)
module namespace upt="http://exist-db.org/xquery/test/unparsed-text";

import module namespace system="http://exist-db.org/xquery/system";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare
    %test:setUp
function upt:store() {
    let $col := xmldb:create-collection("/db", "test-unparsed-text")
    return (
        xmldb:store($col, "test.txt", "Hello&#10;world!&#xD;Hello&#xD;&#xA;world!", "application/octet-stream"),
        xmldb:store($col, "test.xml", "<p>Hello world!</p>", "application/xml")
    )
};

declare
    %test:tearDown
function upt:cleanup() {
    xmldb:remove("/db/test-unparsed-text")
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertXPath("contains($result, 'eXist')")
function upt:unparsed-text-from-url() {
    unparsed-text("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/README")
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertXPath("contains($result, '----')")
function upt:unparsed-text-lines-from-url() {
    unparsed-text-lines("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/README")[2]
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertEquals(6)
function upt:unparsed-text-lines-from-url-count() {
    count(unparsed-text-lines("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/README"))
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertXPath("contains($result, 'eXist')")
function upt:unparsed-text-from-url-encoding() {
    unparsed-text("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/README", "UTF-8")
};

declare
    %test:assertEquals("Hello&#10;world!&#xD;Hello&#xD;&#xA;world!")
function upt:unparsed-text-from-db() {
    unparsed-text("/db/test-unparsed-text/test.txt")
};

declare
    %test:assertEquals("Hello&#10;world!&#xD;Hello&#xD;&#xA;world!")
function upt:unparsed-text-from-db-full() {
    unparsed-text("xmldb:exist:///db/test-unparsed-text/test.txt")
};

declare
    %test:assertEquals("Hello", "world!", "Hello", "world!")
function upt:unparsed-text-lines-from-db() {
    unparsed-text-lines("/db/test-unparsed-text/test.txt")
};

declare
    %test:assertEquals("<p>Hello world!</p>")
function upt:unparsed-text-from-db-xml() {
    unparsed-text("/db/test-unparsed-text/test.xml")
};

declare
    %test:assertEquals(26436)
    %test:pending("Requires external file, should use a temp file which is setup by the test setup method")
function upt:unparsed-text-from-file-dba() {
    let $home := translate(system:get-exist-home(), "\", "/")
    let $url := ``[file://`{$home}`/LICENSE]``
    return
        string-length(unparsed-text($url))
};

declare
    %test:assertEquals(504)
    %test:pending("Requires external file, should use a temp file which is setup by the test setup method")
function upt:unparsed-text-lines-from-file-dba() {
    let $home := translate(system:get-exist-home(), "\", "/")
    let $url := ``[file://`{$home}`/LICENSE]``
    return
        count(unparsed-text-lines($url))
};

declare
    %test:assertError("FOUT1170")
function upt:unparsed-text-from-file-not-allowed() {
    system:as-user("guest", "guest", unparsed-text("file:///etc/passwd"))
};

declare
    %test:assertError("FOUT1170")
function upt:fragment-identifier() {
    unparsed-text-lines("http://www.example.org/#fragment")
};

declare
    %test:assertError("FOUT1170")
function upt:fragment-identifier-encoding() {
    unparsed-text("http://www.example.org/#fragment", "UTF-8")
};

declare
    %test:assertError("FOUT1170")
function upt:invalid-uri() {
    unparsed-text-lines("http://www.example.org/%gg")
};

declare
    %test:assertError("FOUT1170")
function upt:invalid-uri2() {
    unparsed-text-lines(":/")
};

declare
    %test:assumeInternetAccess("https://www.w3.org")
    %test:assertError("FOUT1170")
function upt:non-existent() {
    unparsed-text-lines("https://www.w3.org/fots/unparsed-text/does-not-exist.txt")
};

(:~
 : this test will fail as the server responds with a
 : permanent redirect that is neither followed nor
 : treated as an error
 : @see https://github.com/eXist-db/exist/issues/4542
 :)
declare
    %test:pending
    %test:assumeInternetAccess("https://www.w3.org")
    %test:assertError("FOUT1170")
function upt:non-existent-redirect() {
    unparsed-text-lines("http://www.w3.org/fots/unparsed-text/does-not-exist.txt")
};

declare
    %test:assertError("FOUT1170")
function upt:unsupported-scheme() {
    fn:unparsed-text-lines("surely-nobody-supports-this:/path.txt")
};

declare
    %test:assertError("FOUT1170")
function upt:windows-path() {
    unparsed-text-lines("C:\file-might-exist.txt", "UTF-8")
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertTrue
function upt:unparsed-text-available-from-url() {
    unparsed-text-available("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/README")
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertTrue
function upt:unparsed-text-available-from-url-encoding() {
    unparsed-text-available("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/README", "UTF-8")
};

declare
    %test:assumeInternetAccess("https://raw.githubusercontent.com")
    %test:assertFalse
function upt:unparsed-text-available-from-url-encoding-no-content() {
    unparsed-text-available("https://raw.githubusercontent.com/eXist-db/exist/develop/exist-core/src/test/xquery/READMEaaaa", "UTF-8")
};

declare 
    %test:assertTrue
function upt:unparsed-text-available-from-db() {
    unparsed-text-available("/db/test-unparsed-text/test.txt")
};

declare 
    %test:assertTrue
function upt:unparsed-text-available-from-db-full() {
    unparsed-text-available("xmldb:exist:///db/test-unparsed-text/test.txt")
};

declare 
    %test:assertTrue
function upt:unparsed-text-available-from-db-xml() {
    unparsed-text-available("/db/test-unparsed-text/test.xml")
};

declare 
    %test:assertFalse
function upt:unparsed-text-available-fragment-identifier() {
    unparsed-text-available("http://www.example.org/#fragment")
};

declare 
    %test:assertFalse
function upt:unparsed-text-available-fragment-identifier-encoding() {
    unparsed-text-available("http://www.example.org/#fragment", "UTF-8")
};

declare 
    %test:assertFalse
function upt:unparsed-text-available-invalid-uri() {
    unparsed-text-available("http://www.example.org/%gg")
};

declare 
    %test:assertFalse
function upt:unparsed-text-available-invalid-uri2() {
    unparsed-text-available(":/")
};

declare
    %test:assumeInternetAccess("https://www.w3.org")
    %test:assertFalse
function upt:unparsed-text-available-non-existent() {
    unparsed-text-available("https://www.w3.org/fots/unparsed-text/does-not-exist.txt")
};

(:~
 : this test will fail as the server responds with a
 : permanent redirect that is neither followed nor
 : treated as an error
 : @see https://github.com/eXist-db/exist/issues/4542
 :)
declare
    %test:pending
    %test:assumeInternetAccess("https://www.w3.org")
    %test:assertFalse
function upt:unparsed-text-available-non-existent-redirect() {
    unparsed-text-available("http://www.w3.org/fots/unparsed-text/does-not-exist.txt")
};

declare 
    %test:assertFalse
function upt:unparsed-text-available-unsupported-scheme() {
    fn:unparsed-text-available("surely-nobody-supports-this:/path.txt")
};