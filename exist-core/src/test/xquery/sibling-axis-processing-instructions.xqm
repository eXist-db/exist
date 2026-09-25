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
 : Processing-instruction tests on the preceding-sibling and following-sibling
 : axes select the same nodes in a stored document as in a constructed one.
 :)
module namespace sapi="http://exist-db.org/xquery/test/sibling-axis-processing-instructions";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $sapi:XML := document {
    processing-instruction top { "t" },
    <root><?x 1?><b/><?y 2?><c/><?x 3?></root>
};

declare
    %test:setUp
function sapi:setup() {
    xmldb:create-collection("/db", "test-sibling-axis-pi"),
    xmldb:store("/db/test-sibling-axis-pi", "test.xml", $sapi:XML)
};

declare
    %test:tearDown
function sapi:tearDown() {
    xmldb:remove("/db/test-sibling-axis-pi")
};

declare %private function sapi:db() as document-node() {
    doc("/db/test-sibling-axis-pi/test.xml")
};

declare
    %test:assertEquals("1", "2")
function sapi:preceding-sibling-mem() {
    $sapi:XML/root/c/preceding-sibling::processing-instruction() ! string()
};

declare
    %test:assertEquals("1", "2")
function sapi:preceding-sibling-db() {
    sapi:db()/root/c/preceding-sibling::processing-instruction() ! string()
};

declare
    %test:assertEquals("2", "3")
function sapi:following-sibling-db() {
    sapi:db()/root/b/following-sibling::processing-instruction() ! string()
};

declare
    %test:assertEquals("1")
function sapi:preceding-sibling-named-db() {
    sapi:db()/root/c/preceding-sibling::processing-instruction(x) ! string()
};

declare
    %test:assertEquals("3")
function sapi:following-sibling-named-db() {
    sapi:db()/root/b/following-sibling::processing-instruction("x") ! string()
};

declare
    %test:assertEquals("c")
function sapi:in-predicate-db() {
    sapi:db()/root/*[preceding-sibling::processing-instruction(y)] ! name()
};

declare
    %test:assertEquals(1)
function sapi:document-level-db() {
    count(sapi:db()/root/preceding-sibling::processing-instruction())
};
