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
 : Parsed nodes keep the prefixes their source has. The module declares a prefix (tei)
 : for a namespace its input uses as the default: that prefix must not be given to the
 : parsed elements, nor show in their serialization or in a document stored from them.
 : See https://github.com/eXist-db/exist/issues/5790
 :)
module namespace pxp = "http://exist-db.org/xquery/test/parse-xml-prefixes";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace tei = "http://www.tei-c.org/ns/1.0";

declare variable $pxp:COLLECTION := "parse-xml-prefixes";

declare variable $pxp:DEFAULT := '<a xmlns="http://www.tei-c.org/ns/1.0"><b/></a>';

declare
    %test:setUp
function pxp:setup() {
    xmldb:create-collection("/db", $pxp:COLLECTION)
};

declare
    %test:tearDown
function pxp:teardown() {
    xmldb:remove("/db/" || $pxp:COLLECTION)
};

declare
    %test:assertEquals("|")
function pxp:parse-xml-default-namespace-unprefixed() {
    let $a := parse-xml($pxp:DEFAULT)/*
    return string(prefix-from-QName(node-name($a))) || "|" || string(prefix-from-QName(node-name($a/*)))
};

declare
    %test:assertEquals('<a xmlns="http://www.tei-c.org/ns/1.0"><b/></a>')
function pxp:parse-xml-default-namespace-serialized() {
    serialize(parse-xml($pxp:DEFAULT)/*)
};

declare
    %test:assertEquals('t|t|<t:a xmlns:t="http://www.tei-c.org/ns/1.0"><t:b/></t:a>')
function pxp:parse-xml-source-prefix-kept() {
    let $a := parse-xml('<t:a xmlns:t="http://www.tei-c.org/ns/1.0"><t:b/></t:a>')/*
    return string-join((string(prefix-from-QName(node-name($a))), string(prefix-from-QName(node-name($a/*))), serialize($a)), "|")
};

declare
    %test:assertEquals('<hi xmlns="http://www.tei-c.org/ns/1.0">testing</hi>')
function pxp:parse-xml-fragment-default-namespace() {
    serialize(parse-xml-fragment('<hi xmlns="http://www.tei-c.org/ns/1.0">testing</hi>')/*)
};

declare
    %test:assertEquals("a|b")
function pxp:parsed-nodes-still-match-declared-prefix() {
    (: the module's own prefix still finds them: it names the namespace, not the node :)
    let $a := parse-xml($pxp:DEFAULT)
    return string-join(($a/tei:a/local-name(), $a/tei:a/tei:b/local-name()), "|")
};

declare
    %test:assertEquals('<a xmlns="http://www.tei-c.org/ns/1.0"><b/></a>|')
function pxp:stored-from-parsed-unprefixed() {
    let $stored := xmldb:store("/db/" || $pxp:COLLECTION, "default.xml", parse-xml($pxp:DEFAULT))
    let $a := doc($stored)/*
    return serialize($a) || "|" || string(prefix-from-QName(node-name($a/*)))
};

declare
    %test:assertEquals('<a xmlns="http://www.tei-c.org/ns/1.0"><b/><c/></a>')
function pxp:inserted-from-parsed-unprefixed() {
    let $stored := xmldb:store("/db/" || $pxp:COLLECTION, "insert.xml", parse-xml($pxp:DEFAULT))
    let $_ := update insert parse-xml('<c xmlns="http://www.tei-c.org/ns/1.0"/>')/* into doc($stored)/tei:a
    return serialize(doc($stored)/*)
};
