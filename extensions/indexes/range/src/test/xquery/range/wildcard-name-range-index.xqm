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
 : With a range index on an attribute, a step with a namespace wildcard, *:local, and a comparison
 : on that attribute must find the same elements as without the index.
 :)
module namespace wnri = "http://exist-db.org/xquery/test/wildcard-name-range-index";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace h = "http://www.w3.org/1999/xhtml";

declare variable $wnri:COLLECTION := "/db/wildcard-name-range-index";
declare variable $wnri:CONFIG := "/db/system/config" || $wnri:COLLECTION;

declare
    %test:setUp
function wnri:setup() {
    let $_ := xmldb:create-collection("/db", substring-after($wnri:COLLECTION, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($wnri:COLLECTION, "/db/"))
    let $_ := xmldb:store($wnri:CONFIG, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><range><create qname="@name" type="xs:string"/></range></index>
        </collection>)
    return xmldb:store($wnri:COLLECTION, "doc.xml",
        <html xmlns="http://www.w3.org/1999/xhtml">
            <head><meta name="dc:title" content="T"/><meta name="other" content="O"/></head>
            <body><div name="dc:title"/><note xmlns="http://www.tei-c.org/ns/1.0" name="dc:title"/></body>
        </html>)
};

declare
    %test:tearDown
function wnri:teardown() {
    xmldb:remove($wnri:COLLECTION),
    xmldb:remove($wnri:CONFIG)
};

declare function wnri:doc() {
    doc($wnri:COLLECTION || "/doc.xml")
};

declare %test:assertEquals(1) function wnri:namespace-wildcard-general-comparison() {
    let $doc := wnri:doc()
    return count($doc//*:meta[@name = "dc:title"])
};

declare %test:assertEquals(1) function wnri:namespace-wildcard-value-comparison() {
    let $doc := wnri:doc()
    return count($doc//*:meta[@name eq "dc:title"])
};

declare %test:assertEquals(1) function wnri:namespace-wildcard-child-step() {
    let $doc := wnri:doc()
    return count($doc/*:html/*:head/*:meta[@name = "dc:title"])
};

declare %test:assertEquals(1) function wnri:local-name-wildcard() {
    let $doc := wnri:doc()
    return count($doc//h:*[@name = "dc:title"][local-name() eq "meta"])
};

declare %test:assertEquals(3) function wnri:any-name() {
    let $doc := wnri:doc()
    return count($doc//*[@name = "dc:title"])
};

declare %test:assertEquals(1) function wnri:named() {
    let $doc := wnri:doc()
    return count($doc//h:meta[@name = "dc:title"])
};

declare %test:assertEquals(1) function wnri:namespace-wildcard-other-element() {
    let $doc := wnri:doc()
    return count($doc//*:div[@name = "dc:title"])
};

declare %test:assertEquals(2) function wnri:namespace-other-than-the-root() {
    let $doc := wnri:doc()
    return count($doc//h:*[@name = "dc:title"])
};
