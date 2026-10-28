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
 : Named attribute steps on stored nodes, for each kind of context node set that
 : the attribute axis is evaluated on. See https://github.com/eXist-db/exist/issues/6895
 :)
module namespace asp="http://exist-db.org/xquery/test/attribute-step-persistent";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace x="http://example.org/x";

declare variable $asp:XML := document {
<doc xml:lang="en" n="d">
    <body xml:space="preserve" n="b">
        <item n="1">
            <w>a</w>
            <w xml:lang="fr">b</w>
        </item>
        <item n="2" x:k="v" k="plain">
            <w>c</w>
        </item>
    </body>
</doc>
};

declare variable $asp:COLLECTION_NAME := "test-attribute-step-persistent";
declare variable $asp:COLLECTION := "/db/" || $asp:COLLECTION_NAME;
declare variable $asp:DOC := $asp:COLLECTION || "/test.xml";

declare
    %test:setUp
function asp:setup() {
    xmldb:create-collection("/db", $asp:COLLECTION_NAME),
    xmldb:store($asp:COLLECTION, "test.xml", $asp:XML)
};

declare
    %test:tearDown
function asp:tearDown() {
    xmldb:remove($asp:COLLECTION)
};

declare
    %test:assertEquals("1", "2")
function asp:own-attribute() {
    doc($asp:DOC)//item/@n ! string()
};

declare
    %test:assertEquals(0)
function asp:absent-attribute() {
    count(doc($asp:DOC)//w/@n)
};

declare
    %test:assertEquals("1", "1", "2")
function asp:parent-attribute() {
    for $w in doc($asp:DOC)//w
    return string($w/../@n)
};

declare
    %test:assertEquals("preserve", "preserve", "preserve")
function asp:ancestor-attribute() {
    for $w in doc($asp:DOC)//w
    return string($w/ancestor::body/@xml:space)
};

declare
    %test:assertEquals("en", "fr", "en")
function asp:nearest-ancestor-or-self-attribute() {
    for $w in doc($asp:DOC)//w
    return string($w/ancestor-or-self::*[@xml:lang][1]/@xml:lang)
};

declare
    %test:assertEquals("2")
function asp:attribute-in-predicate() {
    doc($asp:DOC)//item[@x:k]/@n ! string()
};

declare
    %test:assertEquals("b")
function asp:attribute-value-in-predicate() {
    doc($asp:DOC)//w[@xml:lang = "fr"] ! string()
};

declare
    %test:assertEquals("v", "plain")
function asp:namespaced-and-plain-names() {
    let $item := doc($asp:DOC)//item[2]
    return ($item/@x:k ! string(), $item/@k ! string())
};

declare
    %test:assertEquals("d", "b", "1")
function asp:reverse-order-context() {
    (doc($asp:DOC)//w)[1]/ancestor::*/@n ! string()
};

declare
    %test:assertEquals("true", "false", "true")
function asp:fn-lang() {
    for $w in doc($asp:DOC)//w
    return string(lang("en", $w))
};
