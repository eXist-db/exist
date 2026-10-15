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
 : CDATA sections in the content of an element with a legacy range index, see
 : https://github.com/eXist-db/exist/issues/4825
 :
 : Storing a document in which a text node is followed by a CDATA section, inside an element with a
 : range index, failed with a NullPointerException. Each test stores its document from a string, since
 : only the parser produces CDATA sections, and checks the string value, the index key and a lookup.
 :)
module namespace cri="http://exist-db.org/xquery/test/cdata-range-index";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $cri:collection := "/db/cdata-range-index";
declare variable $cri:config-collection := "/db/system/config" || $cri:collection;

declare variable $cri:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <create qname="entry" type="xs:string"/>
        </index>
    </collection>;

declare
    %test:setUp
function cri:setup() {
    xmldb:create-collection("/db/system/config/db", "cdata-range-index"),
    xmldb:store($cri:config-collection, "collection.xconf", $cri:xconf),
    xmldb:create-collection("/db", "cdata-range-index")
};

declare
    %test:tearDown
function cri:teardown() {
    xmldb:remove($cri:collection),
    xmldb:remove($cri:config-collection)
};

(:~
 : Stores the document and returns the string value of its entry, the entry's range index keys, and the
 : number of entries a lookup by that string value finds.
 :)
declare %private function cri:store-and-describe($name as xs:string, $xml as xs:string) as item()* {
    let $path := xmldb:store($cri:collection, $name, $xml)
    let $entry := doc($path)//entry
    return (
        string($entry),
        string-join(util:index-keys($entry, "", function($key, $data) { $key }, 10), "|"),
        count(doc($path)//entry[. = string($entry)])
    )
};

declare
    %test:assertEquals("somethingItem", "somethingItem", 1)
function cri:text-then-cdata() {
    cri:store-and-describe("text-then-cdata.xml", "<entry>something<![CDATA[Item]]></entry>")
};

declare
    %test:assertEquals("something", "something", 1)
function cri:text-and-cdata-alternating() {
    cri:store-and-describe("alternating.xml", "<entry>so<![CDATA[me]]>th<![CDATA[in]]>g</entry>")
};

declare
    %test:assertEquals("asomethingItem", "asomethingItem", 1)
function cri:text-then-cdata-in-a-child() {
    cri:store-and-describe("in-a-child.xml", "<entry>a<b>something<![CDATA[Item]]></b></entry>")
};

declare
    %test:assertEquals("somethingItem", "somethingItem", 1)
function cri:cdata-then-text() {
    cri:store-and-describe("cdata-then-text.xml", "<entry><![CDATA[something]]>Item</entry>")
};

declare
    %test:assertEquals("somethingItem", "somethingItem", 1)
function cri:cdata-only() {
    cri:store-and-describe("cdata-only.xml", "<entry><![CDATA[somethingItem]]></entry>")
};
