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
 : A self-axis kind or wildcard test in a predicate selects the same nodes of
 : a stored document as of a constructed one.
 :)
module namespace sap="http://exist-db.org/xquery/test/self-axis-predicates";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $sap:XML := document {
    <root><!--c--><b/>text<?p d?><c/></root>
};

declare
    %test:setUp
function sap:setup() {
    xmldb:create-collection("/db", "test-self-axis-predicates"),
    xmldb:store("/db/test-self-axis-predicates", "test.xml", $sap:XML)
};

declare
    %test:tearDown
function sap:tearDown() {
    xmldb:remove("/db/test-self-axis-predicates")
};

declare %private function sap:db() as element(root) {
    doc("/db/test-self-axis-predicates/test.xml")/root
};

(:~ the kind of each node, in document order :)
declare %private function sap:kinds($nodes as node()*) as xs:string* {
    for $n in $nodes
    return
        typeswitch ($n)
            case element() return "element:" || name($n)
            case comment() return "comment"
            case text() return "text"
            case processing-instruction() return "pi"
            default return "other"
};

declare
    %test:assertEquals("element:b", "element:c")
function sap:element-mem() {
    sap:kinds($sap:XML/root/node()[self::*])
};

declare
    %test:assertEquals("element:b", "element:c")
function sap:element-db() {
    sap:kinds(sap:db()/node()[self::*])
};

declare
    %test:assertEquals("comment", "text", "pi")
function sap:not-element-db() {
    sap:kinds(sap:db()/node()[not(self::*)])
};

declare
    %test:assertEquals("comment")
function sap:comment-db() {
    sap:kinds(sap:db()/node()[self::comment()])
};

declare
    %test:assertEquals("text")
function sap:text-db() {
    sap:kinds(sap:db()/node()[self::text()])
};

declare
    %test:assertEquals("pi")
function sap:processing-instruction-db() {
    sap:kinds(sap:db()/node()[self::processing-instruction()])
};

declare
    %test:assertEquals("element:b")
function sap:named-element-db() {
    sap:kinds(sap:db()/node()[self::b])
};

declare
    %test:assertEquals(5)
function sap:any-node-db() {
    count(sap:db()/node()[self::node()])
};

declare
    %test:assertEquals("false", "true", "false", "false", "true")
function sap:each-node-db() {
    for $n in sap:db()/node()
    return string(exists($n[self::*]))
};
