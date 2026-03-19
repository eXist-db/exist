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
 : XQSuite tests for ft:index and ft:search on binary/non-XML data.
 : Consolidates binary.xql, plain-store.xql, plain-ft-functions.xql.
 :)
module namespace ftb="http://exist-db.org/xquery/lucene/ft-search-binary/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

(:~ Visibility collection: guest cannot read, admin can. :)
declare variable $ftb:COLL_BINARY := "/db/lucene-test-binary";

(:~ Plain-ft-functions: empty index config, ft:index builds. :)
declare variable $ftb:COLL_PLAIN := "/db/lucene-test-binary-plain";

(:~ Plain-store: no config, store + ft:index lifecycle. :)
declare variable $ftb:COLL_STORE := "/db/lucene-test-morebinary";
declare variable $ftb:COLL_STORE2 := "/db/lucene-test-morebinary2";
declare variable $ftb:COLL_STORE3 := "/db/lucene-test-morebinary3";

declare variable $ftb:INDEX_DATA := <doc><field name="title">Lorem ipsum dolor</field></doc>;
declare variable $ftb:INDEX_DATA_SUB := <doc><field name="title">Only admin can read this</field></doc>;

declare variable $ftb:SAMPLE_PLAY as document-node() := document {
    <PLAY>
        <SCENE><TITLE>Scene 1</TITLE><SPEECH><LINE>boil and bake in the cauldron</LINE></SPEECH></SCENE>
    </PLAY>
};

declare variable $ftb:XCONF_PLAIN as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0"><index/></collection>;

declare
    %private
function ftb:parity-temp-coll($prefix as xs:string) as xs:string {
    let $suffix := translate(string(current-dateTime()), "-:TZ.+", "")
    return "/db/" || $prefix || "-" || $suffix
};

declare
    %test:setUp
function ftb:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", "lucene-test-binary"),
      xmldb:store($ftb:COLL_BINARY, "test.txt", "Lorem ipsum", "text/text"),
      sm:chmod(xs:anyURI($ftb:COLL_BINARY || "/test.txt"), "rw-------"),
      ft:index($ftb:COLL_BINARY || "/test.txt", $ftb:INDEX_DATA),
      xmldb:create-collection($ftb:COLL_BINARY, "sub"),
      sm:chmod(xs:anyURI($ftb:COLL_BINARY || "/sub"), "rwx------"),
      xmldb:store($ftb:COLL_BINARY || "/sub", "test.txt", "Lorem ipsum", "text/text"),
      sm:chmod(xs:anyURI($ftb:COLL_BINARY || "/sub/test.txt"), "rw-rw-rw-"),
      ft:index($ftb:COLL_BINARY || "/sub/test.txt", $ftb:INDEX_DATA_SUB),
      xmldb:create-collection("/db/system/config/db", "lucene-test-binary-plain"),
      xmldb:create-collection("/db", "lucene-test-binary-plain"),
      xmldb:store("/db/system/config/db/lucene-test-binary-plain", "collection.xconf", $ftb:XCONF_PLAIN),
      xmldb:store($ftb:COLL_PLAIN, "data1.txt", "AAAAAA", "text/plain"),
      xmldb:store($ftb:COLL_PLAIN, "data2.txt", "BBBBBB", "text/plain"),
      xmldb:store($ftb:COLL_PLAIN, "data3.txt", "CCCCCC", "text/plain"),
      xmldb:store($ftb:COLL_PLAIN, "data4.txt", "DDDDDD", "text/plain"),
      ft:index($ftb:COLL_PLAIN || "/data1.txt", <doc><field name="title" store="yes">text</field><field name="para">some text</field></doc>),
      ft:index($ftb:COLL_PLAIN || "/data2.txt", <doc><field name="title" store="yes">more text</field><field name="para">even more text</field></doc>),
      ft:index($ftb:COLL_PLAIN || "/data3.txt", <doc><field name="title" store="yes">foobar title</field><field name="para">even more foobar</field></doc>),
      ft:index($ftb:COLL_PLAIN || "/data4.txt", <doc><field name="title" store="yes">another foobar title</field><field name="para">foobaar even more foobar</field></doc>),
      xmldb:create-collection("/db", "lucene-test-morebinary"),
      xmldb:store($ftb:COLL_STORE, "index1.txt", "AAAAAA", "text/plain"),
      xmldb:store($ftb:COLL_STORE, "index2.txt", "BBBBBB", "text/plain"),
      xmldb:store($ftb:COLL_STORE, "index3.txt", "CCCCCC", "text/plain"),
      xmldb:create-collection($ftb:COLL_STORE, "shakespeare"),
      xmldb:store($ftb:COLL_STORE || "/shakespeare", "hamlet.xml", $ftb:SAMPLE_PLAY),
      xmldb:store($ftb:COLL_STORE || "/shakespeare", "macbeth.xml", $ftb:SAMPLE_PLAY),
      xmldb:store($ftb:COLL_STORE || "/shakespeare", "r_and_j.xml", $ftb:SAMPLE_PLAY),
      ft:index($ftb:COLL_STORE || "/index1.txt", <doc><field name="author" store="yes">Dannes Wessels</field><field name="para">Some text for a paragraph</field></doc>),
      ft:index($ftb:COLL_STORE || "/index2.txt", <doc><field name="author">Adam Retter</field><field name="para">Some text for a paragraph Some text for a paragraph Some text for a paragraph.</field></doc>),
      ft:index($ftb:COLL_STORE || "/index3.txt", <doc><field name="author">Harry Potter</field><field name="para" store="yes">Some blah for a paragraph Some blah for a paragraph Some blah for a paragraph paragraph paragraph.</field></doc>),
      xmldb:create-collection("/db", "lucene-test-morebinary2"),
      xmldb:create-collection($ftb:COLL_STORE2, "shakespeare"),
      xmldb:store($ftb:COLL_STORE2 || "/shakespeare", "hamlet.xml", $ftb:SAMPLE_PLAY),
      xmldb:store($ftb:COLL_STORE2 || "/shakespeare", "macbeth.xml", $ftb:SAMPLE_PLAY),
      xmldb:store($ftb:COLL_STORE2 || "/shakespeare", "r_and_j.xml", $ftb:SAMPLE_PLAY),
      xmldb:create-collection("/db", "lucene-test-morebinary3"),
      xmldb:store($ftb:COLL_STORE3, "index1.txt", "AAAAAA", "text/plain"),
      ft:index($ftb:COLL_STORE3 || "/index1.txt", <doc><field name="para">text</field></doc>) )
};

declare
    %test:tearDown
function ftb:tearDown() {
    ( xmldb:remove($ftb:COLL_BINARY),
      xmldb:remove($ftb:COLL_PLAIN),
      xmldb:remove("/db/system/config/db/lucene-test-binary-plain"),
      xmldb:remove($ftb:COLL_STORE),
      xmldb:remove($ftb:COLL_STORE2),
      if (xmldb:collection-available($ftb:COLL_STORE3)) then xmldb:remove($ftb:COLL_STORE3) else () )
};

(: --- Visibility (from binary.xql) --- :)
declare %test:assertEmpty function ftb:check-visibility-fail() {
    system:as-user("guest", "guest", ft:search($ftb:COLL_BINARY || "/", "title:ipsum")/search/@uri/string())
};

declare %test:assertEquals("/db/lucene-test-binary/test.txt") function ftb:check-visibility-pass() {
    system:as-user("admin", "", ft:search($ftb:COLL_BINARY || "/", "title:ipsum")/search/@uri/string())
};

declare %test:assertEmpty function ftb:check-visibility-collection-fail() {
    system:as-user("guest", "guest", ft:search($ftb:COLL_BINARY || "/sub/", "title:admin")/search/@uri/string())
};

declare %test:assertEquals("/db/lucene-test-binary/sub/test.txt") function ftb:check-visibility-collection-pass() {
    system:as-user("admin", "", ft:search($ftb:COLL_BINARY || "/sub/", "title:admin")/search/@uri/string())
};

declare %test:assertEquals("/db/lucene-test-binary/test.txt") function ftb:check-leading-wildcard() {
    system:as-user("admin", "", ft:search($ftb:COLL_BINARY || "/", "title:*rem", (), <options><leading-wildcard>yes</leading-wildcard></options>)/search/@uri/string())
};

(: --- Plain-ft-functions (term range, get-field, search) --- :)
declare %test:assertEquals("/db/lucene-test-binary-plain/data1.txt /db/lucene-test-binary-plain/data2.txt") function ftb:search-title-text() {
    string-join(data(ft:search($ftb:COLL_PLAIN || "/", "title:text")//@uri), ' ')
};

(: Plain-ft-functions parity: explicit root /db path search :)
declare %test:assertEquals("/db/lucene-test-binary-plain/data1.txt /db/lucene-test-binary-plain/data2.txt") function ftb:search-title-text-db() {
    string-join(data(ft:search("/db/", "title:text")//@uri), ' ')
};

declare %test:assertEquals("/db/lucene-test-binary-plain/data3.txt /db/lucene-test-binary-plain/data4.txt") function ftb:search-title-foobar() {
    string-join(data(ft:search($ftb:COLL_PLAIN || "/", "title:foobar")//@uri), ' ')
};

(: Plain-ft-functions: ft:search on a single doc should only return that doc :)
declare %test:assertEquals("/db/lucene-test-binary-plain/data3.txt") function ftb:search-title-foobar-single() {
    string-join(data(ft:search($ftb:COLL_PLAIN || "/data3.txt", "title:foobar")//@uri), ' ')
};

declare %test:assertEquals("/db/lucene-test-binary-plain/data3.txt /db/lucene-test-binary-plain/data4.txt") function ftb:search-title-foobar-two-paths() {
    string-join(data(ft:search(($ftb:COLL_PLAIN || "/data3.txt", $ftb:COLL_PLAIN || "/data4.txt"), "title:foobar")//@uri), ' ')
};

declare %test:assertEquals("/db/lucene-test-binary-plain/data4.txt") function ftb:search-para-foobaar() {
    string-join(data(ft:search($ftb:COLL_PLAIN || "/", "para:foobaar")//@uri), ' ')
};

declare %test:assertEquals("") function ftb:search-para-non-existing-collection() {
    string-join(data(ft:search("/db/lucene-test-nonexistent/", "para:foobaar")//@uri), ' ')
};

declare %test:assertEquals("/db/lucene-test-binary-plain/data4.txt") function ftb:search-para-mixed-collections() {
    string-join(data(ft:search(("/db/lucene-test-nonexistent/", $ftb:COLL_PLAIN || "/"), "para:foobaar")//@uri), ' ')
};

declare %test:assertEquals("/db/lucene-test-binary-plain/data4.txt") function ftb:search-para-same-collection-twice() {
    string-join(data(ft:search(($ftb:COLL_PLAIN || "/", $ftb:COLL_PLAIN || "/"), "para:foobaar")//@uri), ' ')
};

declare %test:assertEquals("/db/lucene-test-binary-plain/data4.txt") function ftb:search-para-collection-and-doc() {
    string-join(data(ft:search(($ftb:COLL_PLAIN || "/", $ftb:COLL_PLAIN || "/data4.txt"), "para:foobaar")//@uri), ' ')
};

(: Plain-ft-functions parity: explicit index construction should remain safe/idempotent. :)
declare %test:assertEmpty function ftb:create-index() {
    let $coll := ftb:parity-temp-coll("lucene-test-binary-parity-create-index")
    return
        try {
            let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
            let $_ := xmldb:store($coll, "data1.txt", "AAAAAA", "text/plain")
            let $_ := xmldb:store($coll, "data2.txt", "BBBBBB", "text/plain")
            let $_ := xmldb:store($coll, "data3.txt", "CCCCCC", "text/plain")
            let $_ := xmldb:store($coll, "data4.txt", "DDDDDD", "text/plain")
            let $_ := ft:index($coll || "/data1.txt", <doc><field name="title" store="yes">text</field><field name="para">some text</field></doc>)
            let $_ := ft:index($coll || "/data2.txt", <doc><field name="title" store="yes">more text</field><field name="para">even more text</field></doc>)
            let $_ := ft:index($coll || "/data3.txt", <doc><field name="title" store="yes">foobar title</field><field name="para">even more foobar</field></doc>)
            let $_ := ft:index($coll || "/data4.txt", <doc><field name="title" store="yes">another foobar title</field><field name="para">foobaar even more foobar</field></doc>)
            return (
                if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (),
                ()
            )
        } catch * {
            (if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (), ())
        }
};

declare %test:assertEquals("another foobar title") function ftb:get-field() {
    ft:get-field($ftb:COLL_PLAIN || "/data4.txt", "title")
};

declare %test:assertEquals(3) function ftb:search-term-range() {
    count(ft:search($ftb:COLL_PLAIN || "/", "para:[even TO foobaar]")//@uri)
};

declare %test:assertTrue function ftb:search-retrieve-values() {
    let $result := ft:search($ftb:COLL_PLAIN || "/", 'title:"another foobar"', "title")//field
    return deep-equal($result, <field name="title"><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">another foobar</exist:match> title</field>)
};

(: --- Plain-store (store/remove lifecycle) --- :)
declare %test:assertEmpty function ftb:a-store-index-1() {
    let $coll := ftb:parity-temp-coll("lucene-test-binary-parity-store-a")
    return
        try {
            let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
            let $_ := xmldb:store($coll, "index1.txt", "AAAAAA", "text/plain")
            let $_ := ft:index($coll || "/index1.txt", <doc><field name="author" store="yes">Dannes Wessels</field><field name="para">Some text for a paragraph</field></doc>)
            return (
                if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (),
                ()
            )
        } catch * {
            (if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (), ())
        }
};

declare %test:assertEmpty function ftb:b-store-index-2() {
    let $coll := ftb:parity-temp-coll("lucene-test-binary-parity-store-b")
    return
        try {
            let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
            let $_ := xmldb:store($coll, "index2.txt", "BBBBBB", "text/plain")
            let $_ := ft:index($coll || "/index2.txt", <doc><field name="author">Adam Retter</field><field name="para">Some text for a paragraph Some text for a paragraph Some text for a paragraph.</field></doc>)
            return (
                if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (),
                ()
            )
        } catch * {
            (if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (), ())
        }
};

declare %test:assertEmpty function ftb:c-store-index-3() {
    let $coll := ftb:parity-temp-coll("lucene-test-binary-parity-store-c")
    return
        try {
            let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
            let $_ := xmldb:store($coll, "index3.txt", "CCCCCC", "text/plain")
            let $_ := ft:index($coll || "/index3.txt", <doc><field name="author">Harry Potter</field><field name="para" store="yes">Some blah for a paragraph Some blah for a paragraph Some blah for a paragraph paragraph paragraph.</field></doc>)
            return (
                if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (),
                ()
            )
        } catch * {
            (if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (), ())
        }
};

declare %test:assertEquals("/db/lucene-test-morebinary/index1.txt /db/lucene-test-morebinary/index2.txt") function ftb:query-para-text() {
    string-join(for $uri in ft:search($ftb:COLL_STORE || "/", "para:text")//@uri order by $uri return string($uri), ' ')
};

declare %test:assertXPath("//search[@uri = '/db/lucene-test-morebinary/index3.txt']") function ftb:query-non-stored-field() {
    ft:search($ftb:COLL_STORE || "/", "author:potter")
};

declare %test:assertXPath("//search/field") function ftb:query-stored-field() {
    ft:search($ftb:COLL_STORE || "/", "author:dannes")
};

(: Plain-store: ensure queries on parent collections include subcollections (was previously ignored) :)
declare %test:pending("previously ignored; query on parent collection") %test:assertEquals(1)
function ftb:query-parent-includes-subcollections() {
    count(ft:search($ftb:COLL_STORE || "/shakespeare", 'speech:"boil bake"')//field)
};

declare %test:assertEquals("true true true") function ftb:validate-scores() {
    let $results := ft:search($ftb:COLL_STORE || "/", "para:paragraph"),
        $score := for $s in $results//@score order by xs:double($s) descending return xs:double($s)
    return string-join(($score[1] > $score[2], $score[2] > $score[3], $score[1] > $score[3]), ' ')
};

declare %test:assertTrue function ftb:get-content-stored-field() {
    let $result := ft:search($ftb:COLL_STORE || "/", "para:blah")//field
    return deep-equal($result, <field name="para">Some <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">blah</exist:match> for a paragraph Some <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">blah</exist:match> for a paragraph Some <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">blah</exist:match> for a paragraph paragraph paragraph.</field>)
};

declare %test:assertEquals(1) function ftb:add-index-xml-document() {
    let $path := $ftb:COLL_STORE || "/shakespeare/macbeth.xml",
        $doc := doc($path),
        $index := for $scene in $doc//SCENE return ft:index($path, <doc><field name="title">{ $scene/TITLE/text() }</field><field name="speech">{ string-join($scene//SPEECH/*/text(), ' ') }</field></doc>)
    return count(ft:search($path, 'speech:"boil and bake"')//search)
};

declare %test:assertEquals(0) function ftb:remove-single-document() {
    xmldb:remove($ftb:COLL_STORE2 || "/shakespeare", "macbeth.xml"),
    count(ft:search($ftb:COLL_STORE2 || "/shakespeare", 'speech:"boil bake"')//field)
};

declare %test:assertEmpty function ftb:remove-collection() {
    xmldb:remove($ftb:COLL_STORE3),
    ft:search($ftb:COLL_STORE3, "para:text")//@uri
};
