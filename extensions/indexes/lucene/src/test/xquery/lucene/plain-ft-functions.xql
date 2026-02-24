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
 : XQSuite tests for non-XML data query (ft:index, ft:search on binary).
 : Refactored from plain-ft-functions.xml (TestSet). nonXML data query tests.
 :
 : @author Dannes Wessels
 :)
module namespace pftf="http://exist-db.org/xquery/lucene/plain-ft-functions/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

(:~
 : Empty collection config for /db.
 :)
declare variable $pftf:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index/>
    </collection>;

declare variable $pftf:COLLECTION_BINARY := "/db/lucene-test/plain-ft-functions";

(:~
 : setUp: create /db/system/config/db, store collection.xconf, create data collection, store 4 text files.
 :)
declare
    %test:setUp
function pftf:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:store("/db/system/config/db", "collection.xconf", $pftf:XCONF),
      xmldb:create-collection("/db", "lucene-test"),
      xmldb:create-collection("/db/lucene-test", "plain-ft-functions"),
      xmldb:store($pftf:COLLECTION_BINARY, "data1.txt", "AAAAAA", "text/plain"),
      xmldb:store($pftf:COLLECTION_BINARY, "data2.txt", "BBBBBB", "text/plain"),
      xmldb:store($pftf:COLLECTION_BINARY, "data3.txt", "CCCCCC", "text/plain"),
      xmldb:store($pftf:COLLECTION_BINARY, "data4.txt", "DDDDDD", "text/plain") )
};

(:~
 : tearDown: remove only our data collection. Do not remove /db/system/config/db/collection.xconf
 : (our empty /db index config) to avoid affecting other tests that may rely on /db config state.
 :)
declare
    %test:tearDown
function pftf:tearDown() {
    xmldb:remove($pftf:COLLECTION_BINARY)
};

(:~
 : Create Index for stored documents.
 :)
declare
    %test:assertEmpty
function pftf:create-index() {
    ( ft:index($pftf:COLLECTION_BINARY || "/data1.txt", <doc><field name="title" store="yes">text</field><field name="para">some text</field></doc>),
      ft:index($pftf:COLLECTION_BINARY || "/data2.txt", <doc><field name="title" store="yes">more text</field><field name="para">even more text</field></doc>),
      ft:index($pftf:COLLECTION_BINARY || "/data3.txt", <doc><field name="title" store="yes">foobar title</field><field name="para">even more foobar</field></doc>),
      ft:index($pftf:COLLECTION_BINARY || "/data4.txt", <doc><field name="title" store="yes">another foobar title</field><field name="para">foobaar even more foobar</field></doc>) )
};

(:~
 : Test Index 1 - search title.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data1.txt /db/lucene-test/plain-ft-functions/data2.txt")
function pftf:search-title-text() {
    string-join(data(ft:search($pftf:COLLECTION_BINARY || "/", "title:text")//@uri), ' ')
};

(:~
 : Test Index 1a - search on different level.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data1.txt /db/lucene-test/plain-ft-functions/data2.txt")
function pftf:search-title-text-db() {
    string-join(data(ft:search("/db/", "title:text")//@uri), ' ')
};

(:~
 : Test Index 2 - search title foobar.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data3.txt /db/lucene-test/plain-ft-functions/data4.txt")
function pftf:search-title-foobar() {
    string-join(data(ft:search($pftf:COLLECTION_BINARY || "/", "title:foobar")//@uri), ' ')
};

(:~
 : Test Index 2a - search title foobar on single doc.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data3.txt")
function pftf:search-title-foobar-single() {
    string-join(data(ft:search($pftf:COLLECTION_BINARY || "/data3.txt", "title:foobar")//@uri), ' ')
};

(:~
 : Test Index 2b - search title foobar on two paths.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data3.txt /db/lucene-test/plain-ft-functions/data4.txt")
function pftf:search-title-foobar-two-paths() {
    string-join(data(ft:search(($pftf:COLLECTION_BINARY || "/data3.txt", $pftf:COLLECTION_BINARY || "/data4.txt"), "title:foobar")//@uri), ' ')
};

(:~
 : Test Index 3 - search paragraph foobaar.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data4.txt")
function pftf:search-para-foobaar() {
    string-join(data(ft:search($pftf:COLLECTION_BINARY || "/", "para:foobaar")//@uri), ' ')
};

(:~
 : Test Index 3a - search on non-existing collection.
 :)
declare
    %test:assertEquals("")
function pftf:search-para-non-existing-collection() {
    string-join(data(ft:search("/db/lucene-test-nonexistent/", "para:foobaar")//@uri), ' ')
};

(:~
 : Test Index 3b - one existing one non-existing collection.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data4.txt")
function pftf:search-para-mixed-collections() {
    string-join(data(ft:search(("/db/lucene-test-nonexistent/", $pftf:COLLECTION_BINARY || "/"), "para:foobaar")//@uri), ' ')
};

(:~
 : Test Index 3c - two times the same collection.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data4.txt")
function pftf:search-para-same-collection-twice() {
    string-join(data(ft:search(($pftf:COLLECTION_BINARY || "/", $pftf:COLLECTION_BINARY || "/"), "para:foobaar")//@uri), ' ')
};

(:~
 : Test Index 4 - expect one result.
 :)
declare
    %test:assertEquals("/db/lucene-test/plain-ft-functions/data4.txt")
function pftf:search-para-collection-and-doc() {
    string-join(data(ft:search(($pftf:COLLECTION_BINARY || "/", $pftf:COLLECTION_BINARY || "/data4.txt"), "para:foobaar")//@uri), ' ')
};

(:~
 : Test Index 5 - retrieving values (field with exist:match).
 :)
declare
    %test:assertTrue
function pftf:search-retrieve-values() {
    let $result := ft:search($pftf:COLLECTION_BINARY || "/", 'title:"another foobar"', "title")//field
    return deep-equal($result, <field name="title"><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">another foobar</exist:match> title</field>)
};

(:~
 : Test Index 6 - get-field.
 :)
declare
    %test:assertEquals("another foobar title")
function pftf:get-field() {
    ft:get-field($pftf:COLLECTION_BINARY || "/data4.txt", "title")
};

(:~
 : Test Index 7 - search term range.
 :)
declare
    %test:assertEquals(3)
function pftf:search-term-range() {
    count(ft:search($pftf:COLLECTION_BINARY || "/", "para:[even TO foobaar]")//@uri)
};
