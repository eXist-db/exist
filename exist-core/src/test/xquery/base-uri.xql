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
 : Test for base-uri
 :)
module namespace baseuri="http://exist-db.org/xquery/test/baseuri";

import module namespace test="http://exist-db.org/xquery/xqsuite"
                             at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $baseuri:DOCUMENT :=
    <top>
        <sub name="A" xml:base="http://example.org/a/">
            <data>A</data>
        </sub>
        <sub name="B" xml:base="http://example.org/b/">
            <data>B</data>
        </sub>
        <sub name="C" xml:base="http://example.org/c/">
            <subsub name="D" xml:base="http://example.org/d/">
                <data>D</data>
            </subsub>
            <subsub name="E" xml:base="e/">
                <data>E</data>
            </subsub>
            <subsub name="F" xml:base="/f/">
                <data>F</data>
            </subsub>
            <subsub name="G" xml:base="http://example.org/g/">
                <data>G</data>
            </subsub>
            <subsub name="H" xml:base="http://example.org/h/">
                <subsub name="I" xml:base="i/">
                    <data>I</data>
                </subsub>
            </subsub>
            <subsub name="Y" xml:base="">
                <data>Y</data>
            </subsub>
            <subsub name="Z">
                <data>Z</data>
            </subsub>
        </sub>
        <sub name="YY" xml:base="/yy">
            <data>YY</data>
        </sub>
        <sub name="ZZ" xml:base="zz">
            <data>ZZ</data>
        </sub>
    </top>;

declare variable $baseuri:db := "/db";
declare variable $baseuri:collection := "base-uri";
declare variable $baseuri:full_path_collection := $baseuri:db || "/" || $baseuri:collection;
declare variable $baseuri:document := "test.xml";
declare variable $baseuri:full_path_document :=  $baseuri:full_path_collection || "/" || $baseuri:document;

declare
    %test:setUp
function baseuri:store() {
    let $col := xmldb:create-collection($baseuri:db, $baseuri:collection)
    return
        (
            xmldb:store($col, $baseuri:document, $baseuri:DOCUMENT)
        )
};

declare
    %test:tearDown
function baseuri:cleanup() {
    xmldb:remove($baseuri:full_path_collection)
};


declare
    %test:assertEquals("-/db/base-uri/test.xml")
function baseuri:root() {
     $baseuri:DOCUMENT/base-uri() || "-" || base-uri(doc($baseuri:full_path_document))
};

declare
    %test:assertEquals("http://example.org/a/","http://example.org/b/","http://example.org/c/","/yy","zz")
function baseuri:sub1() {
     for $sub in $baseuri:DOCUMENT//sub
     return base-uri($sub)
};

declare
    %test:assertEquals("http://example.org/a/","http://example.org/b/","http://example.org/c/","/yy","/db/base-uri/zz")
function baseuri:sub2() {
     for $sub in doc($baseuri:full_path_document)//sub
     return base-uri($sub)
};

declare
    %test:assertEquals("http://example.org/d/","http://example.org/c/e","/f","http://example.org/g/","http://example.org/h/"
    ,"http://example.org/h/i","http://example.org/c/","http://example.org/c/")
function baseuri:subsub1() {
     for $sub in $baseuri:DOCUMENT//subsub
     return base-uri($sub)
};

declare
    %test:assertEquals("http://example.org/d/","http://example.org/c/e","/f","http://example.org/g/","http://example.org/h/"
    ,"http://example.org/h/i","http://example.org/c/","http://example.org/c/")
function baseuri:subsub2() {
     for $sub in doc($baseuri:full_path_document)//subsub
     return base-uri($sub)
};

(: some more tests :)
declare
    %test:assertEmpty
function baseuri:attribute_empty() {
     (attribute anAttribute{"attribute value"})/fn:base-uri()
};

declare
    %test:assertEmpty
function baseuri:pi_empty() {
     fn:base-uri(processing-instruction {"PItarget"} {"PIcontent"})
};

declare
    %test:assertEmpty
function baseuri:textnode_empty() {
     fn:base-uri(text {"A Text Node"})
};
