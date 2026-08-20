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
 : Tests for xmldb:store() with a binary (xs:base64Binary) content value.
 :
 : Regression for the case where a binary content value is stored under an XML-type
 : mime (explicit mime="application/xml" or inferred from an .xml resource name):
 : the store used to fail with java.lang.NullPointerException
 : ("Cannot invoke \"String.length()\" because \"<parameter1>\" is null") because the
 : binary was bound to an XML resource that had no character/byte stream to parse.
 : The bytes should instead be parsed as an XML document.
 :)
module namespace sbt = "http://exist-db.org/xquery/test/xmldb-store-binary";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace util = "http://exist-db.org/xquery/util";

declare variable $sbt:collection-name := "xmldb-store-binary-test";
declare variable $sbt:collection := "/db/" || $sbt:collection-name;

declare
    %test:setUp
function sbt:setup() as empty-sequence() {
    let $_ := xmldb:create-collection("/db", $sbt:collection-name)
    return ()
};

declare
    %test:tearDown
function sbt:tear-down() as empty-sequence() {
    let $_ := xmldb:remove($sbt:collection)
    return ()
};

(: binary content + explicit application/xml mime -> parsed and stored as XML :)
declare
    %test:assertEquals("1")
function sbt:binary-with-xml-mime-is-parsed() {
    let $bin := util:string-to-binary("<doc><a>1</a></doc>")
    let $stored := xmldb:store($sbt:collection, "explicit.xml", $bin, "application/xml")
    return doc($stored)/doc/a/string()
};

(: binary content, mime inferred from the .xml resource name -> parsed and stored as XML :)
declare
    %test:assertEquals("1")
function sbt:binary-with-inferred-xml-mime-is-parsed() {
    let $bin := util:string-to-binary("<doc><a>1</a></doc>")
    let $stored := xmldb:store($sbt:collection, "inferred.xml", $bin)
    return doc($stored)/doc/a/string()
};

(: the parsed document is a real XML document, not a binary resource :)
declare
    %test:assertEquals("false")
function sbt:binary-with-xml-mime-is-not-binary() {
    let $bin := util:string-to-binary("<doc><a>1</a></doc>")
    let $stored := xmldb:store($sbt:collection, "as-xml.xml", $bin, "application/xml")
    return string(util:binary-doc-available($stored))
};

(: XML encoding declaration in the bytes is honored (parser reads it from the stream) :)
declare
    %test:assertEquals("ä")
function sbt:binary-with-xml-mime-honors-encoding() {
    let $bin := util:string-to-binary("<?xml version='1.0' encoding='UTF-8'?><doc>&#xE4;</doc>")
    let $stored := xmldb:store($sbt:collection, "encoded.xml", $bin, "application/xml")
    return doc($stored)/doc/string()
};

(: control: binary content + a binary mime is still stored byte-for-byte as a binary resource :)
declare
    %test:assertEquals("true")
function sbt:binary-with-binary-mime-stays-binary() {
    let $bin := util:string-to-binary("<doc><a>1</a></doc>")
    let $stored := xmldb:store($sbt:collection, "raw.bin", $bin, "application/octet-stream")
    return string(util:binary-doc-available($stored))
};

(: malformed XML bytes under an XML mime fail with a clean store/parse error, NOT an NPE :)
declare
    %test:assertError("storing document")
function sbt:binary-malformed-xml-mime-reports-parse-error() {
    let $bin := util:string-to-binary("not well-formed <")
    return xmldb:store($sbt:collection, "broken.xml", $bin, "application/xml")
};
