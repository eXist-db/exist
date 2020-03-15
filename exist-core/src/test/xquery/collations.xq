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

module namespace collations = "http://exist-db.org/xquery/test/collations";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare variable $collations:TEST-DOC-1 := document {
    <entry>
        <a>xxx</a>
        <b/>
    </entry>
};

declare
    %test:setUp
function collations:setup() {
    xmldb:create-collection("/db", "collations-test"),
    xmldb:store("/db/collations-test", "test.xml", $collations:TEST-DOC-1)
};

declare
    %test:tearDown
function collations:cleanup() {
    xmldb:remove("/db/collations-test")
};

declare
    %test:assertEquals("<a>xxx</a>")
function collations:non-empty-string-contains() {
    doc("/db/collations-test/test.xml")//a[contains(.,'x',"?lang=en-US")]
};

declare
    %test:assertEmpty
function collations:empty-string-contains() {
    doc("/db/collations-test/test.xml")//b[contains(.,'x',"?lang=en-US")]
};

declare
    %test:assertEquals("<a>xxx</a>")
function collations:non-empty-string-starts-with() {
    doc("/db/collations-test/test.xml")//a[starts-with(.,'x',"?lang=en-US")]
};

 declare
    %test:assertEmpty
function collations:empty-string-starts-with() {
    doc("/db/collations-test/test.xml")//b[starts-with(.,'x',"?lang=en-US")]
};

declare
    %test:assertEquals("<a>xxx</a>")
function collations:non-empty-string-ends-with() {
    doc("/db/collations-test/test.xml")//a[ends-with(.,'x',"?lang=en-US")]
};

 declare
    %test:assertEmpty
function collations:empty-string-ends-with() {
    doc("/db/collations-test/test.xml")//b[ends-with(.,'x',"?lang=en-US")]
};

declare
    %test:assertEquals("")
    function collations:substring-after-empty-string() {
        substring-after("", "test", "?lang=en-US")
};

declare
    %test:assertEquals("")
    function collations:substring-before-empty-string() {
        substring-before("", "test", "?lang=en-US")
};

declare
    %test:assertEquals("")
    function collations:substring-after-empty-sequence() {
        substring-after((), "test", "?lang=en-US")
};

declare
    %test:assertEquals("")
    function collations:substring-before-empty-sequence() {
        substring-before((), "test", "?lang=en-US")
};