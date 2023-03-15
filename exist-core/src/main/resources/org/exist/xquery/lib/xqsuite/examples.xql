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

module namespace t="http://exist-db.org/xquery/test/examples";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "test.xql";

declare variable $t:TEST_COLLECTION := "xqunit";

declare variable $t:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:mods="http://www.loc.gov/mods/v3">
            <lucene>
                <text qname="SPEECH"/>
            </lucene>
        </index>
    </collection>;
    
declare %test:setUp function t:setUp() {
    let $configColl := test:mkcol("/db/system/config/db/", $t:TEST_COLLECTION)
    let $collection := xmldb:create-collection("/db", $t:TEST_COLLECTION)
    return (
        xmldb:store("/db/system/config/db/" || $t:TEST_COLLECTION, "collection.xconf", $t:XCONF),
        xmldb:store-files-from-pattern("/db/" || $t:TEST_COLLECTION, "samples/shakespeare", "*.xml")
    )
};

declare %test:tearDown function t:shutdown() {
    xmldb:remove("/db/" || $t:TEST_COLLECTION)
};

declare 
    %test:assertEquals(1)
function t:lucene-query() {
    count(collection("/db/" || $t:TEST_COLLECTION)//SPEECH[ft:query(., "+fenny +snake")])
};

declare %test:assertEquals("<SPEAKER>MACBETH</SPEAKER><SPEAKER>Second Witch</SPEAKER>")
function t:speakers() {
    collection("/db/" || $t:TEST_COLLECTION)//SPEECH[ft:query(., "fenny snake")]/SPEAKER
};

declare %test:assertXPath("$result[. = 'MACBETH']")
function t:speakers2() {
    collection("/db/" || $t:TEST_COLLECTION)//SPEECH[ft:query(., "fenny snake")]/SPEAKER
};

declare %test:assertExists %test:assertEquals(500)
function t:integer() {
    500
};

declare %test:assertEmpty function t:empty() {
    ()
};

declare %test:assertEmpty function t:empty2() {
    2
};

declare %test:assertXPath("/name[. = 'Item2']")
function t:xpath() {
    <item>
        <name>Item1</name>
    </item>
};

declare
    %test:arg("error", "Bad Request")
    %test:assertXPath("$result/self::html")
    %test:assertXPath("$result/head[title = 'Bad Request']")
    %test:assertXPath("$result/body/p[@class = 'ErrorMessage']")
    %test:assertXPath("/self::html")
    %test:assertXPath("/head[title = 'Bad Request']")
    %test:assertXPath("/body/p[@class = 'ErrorMessage']")
function local:default-element-namespace(
    $error as xs:string
) as node() {
    <html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
        <head>
            <title>{$error}</title>
        </head>
        <body>
            <p class='ErrorMessage'><b>Message: </b>{$error}</p>
        </body>
    </html>
};

declare
    %test:assertXPath("/self::x")
    %test:assertXPath("exists($result/*:y)")
    %test:assertXPath("not($result/y)")
    %test:assertXPath("$result/Q{bar}y")
function local:multiple-default-element-namespaces() as node() {
    <x xmlns="foo">
        <y xmlns="bar"/>
    </x>
};
