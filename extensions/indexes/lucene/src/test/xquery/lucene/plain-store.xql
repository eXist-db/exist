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
 : XQSuite tests for non-XML data indexing (ft:index on binary + XML).
 : Refactored from plain-store.xml (TestSet). nonXML data indexing tests.
 : Uses self-contained sample documents; no classpath resources.
 :
 : @author Dannes Wessels
 :)
module namespace pstor="http://exist-db.org/xquery/lucene/plain-store/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

(:~
 : Self-contained sample play (SCENE/SPEECH) for ft:index tests. Contains "boil and bake"
 : so speech phrase and term searches match. Used as hamlet.xml, macbeth.xml, r_and_j.xml.
 :)
declare variable $pstor:SAMPLE_PLAY as document-node() :=
    document {
        <PLAY>
            <SCENE>
                <TITLE>Scene 1</TITLE>
                <SPEECH><LINE>boil and bake in the cauldron</LINE></SPEECH>
            </SCENE>
        </PLAY>
    };

(:~
 : setUp: create lucene-test-morebinary, lucene-test-morebinary2, lucene-test-morebinary3; store text files and sample XML.
 : No ft:index here — Store index 1/2/3 build the index; query tests run after and depend on it.
 :)
declare
    %test:setUp
function pstor:setUp() {
    ( xmldb:create-collection("/db", "lucene-test-morebinary"),
      xmldb:store("/db/lucene-test-morebinary", "index1.txt", "AAAAAA", "text/plain"),
      xmldb:store("/db/lucene-test-morebinary", "index2.txt", "BBBBBB", "text/plain"),
      xmldb:store("/db/lucene-test-morebinary", "index3.txt", "CCCCCC", "text/plain"),
      xmldb:create-collection("/db/lucene-test-morebinary", "shakespeare"),
      xmldb:store("/db/lucene-test-morebinary/shakespeare", "hamlet.xml", $pstor:SAMPLE_PLAY),
      xmldb:store("/db/lucene-test-morebinary/shakespeare", "macbeth.xml", $pstor:SAMPLE_PLAY),
      xmldb:store("/db/lucene-test-morebinary/shakespeare", "r_and_j.xml", $pstor:SAMPLE_PLAY),
      xmldb:create-collection("/db", "lucene-test-morebinary2"),
      xmldb:create-collection("/db/lucene-test-morebinary2", "shakespeare"),
      xmldb:store("/db/lucene-test-morebinary2/shakespeare", "hamlet.xml", $pstor:SAMPLE_PLAY),
      xmldb:store("/db/lucene-test-morebinary2/shakespeare", "macbeth.xml", $pstor:SAMPLE_PLAY),
      xmldb:store("/db/lucene-test-morebinary2/shakespeare", "r_and_j.xml", $pstor:SAMPLE_PLAY),
      xmldb:create-collection("/db", "lucene-test-morebinary3"),
      xmldb:create-collection("/db/lucene-test-morebinary3", "shakespeare"),
      xmldb:store("/db/lucene-test-morebinary3/shakespeare", "hamlet.xml", $pstor:SAMPLE_PLAY),
      xmldb:store("/db/lucene-test-morebinary3/shakespeare", "macbeth.xml", $pstor:SAMPLE_PLAY),
      xmldb:store("/db/lucene-test-morebinary3/shakespeare", "r_and_j.xml", $pstor:SAMPLE_PLAY) )
};

(:~
 : tearDown: remove lucene-test-morebinary, lucene-test-morebinary2, lucene-test-morebinary3 (ignore missing for lucene-test-morebinary3).
 :)
declare
    %test:tearDown
function pstor:tearDown() {
    ( xmldb:remove("/db/lucene-test-morebinary"),
      xmldb:remove("/db/lucene-test-morebinary2"),
      if (xmldb:collection-available("/db/lucene-test-morebinary3")) then xmldb:remove("/db/lucene-test-morebinary3") else () )
};

(:~
 : Store index document 1. Builds the searchable index for index1.txt; later tests depend on it.
 : Named a-* so it runs first.
 :)
declare
    %test:assertEmpty
function pstor:a-store-index-1() {
    ft:index("/db/lucene-test-morebinary/index1.txt", <doc><field name="author" store="yes">Dannes Wessels</field><field name="para">Some text for a paragraph</field></doc>)
};

(:~
 : Store index document 2. Builds the index for index2.txt.
 : Named b-* so it runs after a-store-index-1.
 :)
declare
    %test:assertEmpty
function pstor:b-store-index-2() {
    ft:index("/db/lucene-test-morebinary/index2.txt", <doc><field name="author">Adam Retter</field><field name="para">Some text for a paragraph Some text for a paragraph Some text for a paragraph.</field></doc>)
};

(:~
 : Store index document 3. Builds the index for index3.txt.
 : Named c-* so it runs after b-store-index-2.
 :)
declare
    %test:assertEmpty
function pstor:c-store-index-3() {
    ft:index("/db/lucene-test-morebinary/index3.txt", <doc><field name="author">Harry Potter</field><field name="para" store="yes">Some blah for a paragraph Some blah for a paragraph Some blah for a paragraph paragraph paragraph.</field></doc>)
};

(:~
 : Query for text in para.
 :)
declare
    %test:assertEquals("/db/lucene-test-morebinary/index1.txt /db/lucene-test-morebinary/index2.txt")
function pstor:query-para-text() {
    string-join(for $uri in ft:search("/db/lucene-test-morebinary/", "para:text")//@uri order by $uri return string($uri), ' ')
};

(:~
 : Query for text in non-stored field.
 :)
declare
    %test:assertXPath("//search[@uri = '/db/lucene-test-morebinary/index3.txt']")
function pstor:query-non-stored-field() {
    ft:search("/db/lucene-test-morebinary/", "author:potter")
};

(:~
 : Query for text in stored field.
 :)
declare
    %test:assertXPath("//search/field")
function pstor:query-stored-field() {
    ft:search("/db/lucene-test-morebinary/", "author:dannes")
};

(:~
 : Validate scores.
 :)
declare
    %test:assertEquals("true true true")
function pstor:validate-scores() {
    let $results := ft:search("/db/lucene-test-morebinary/", "para:paragraph"),
        $score := for $s in $results//@score order by xs:double($s) descending return xs:double($s)
    return string-join(($score[1] > $score[2], $score[2] > $score[3], $score[1] > $score[3]), ' ')
};

(:~
 : Get content of stored field.
 :)
declare
    %test:assertTrue
function pstor:get-content-stored-field() {
    let $result := ft:search("/db/lucene-test-morebinary/", "para:blah")//field
    return deep-equal($result, <field name="para">Some <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">blah</exist:match> for a paragraph Some <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">blah</exist:match> for a paragraph Some <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">blah</exist:match> for a paragraph paragraph paragraph.</field>)
};

(:~
 : Index-data helper for Macbeth SCENE.
 :)
declare
    %private
function pstor:index-data($scene as element(SCENE)) as element(doc) {
    <doc>
        <field name="title">{ $scene/TITLE/text() }</field>
        { for $speech in $scene//SPEECH return <field name="speech">{ string-join($speech/*/text(), ' ') }</field> }
    </doc>
};

(:~
 : Add additional index to XML document.
 :)
declare
    %test:assertEquals(1)
function pstor:add-index-xml-document() {
    let $path := "/db/lucene-test-morebinary/shakespeare/macbeth.xml",
        $doc := doc($path),
        $index := for $scene in $doc//SCENE return ft:index($path, pstor:index-data($scene))
    return count(ft:search($path, 'speech:"boil and bake"')//search)
};

(:~
 : Query on parent collection should include subcollections (previously ignored).
 :)
declare
    %test:pending("previously ignored; query on parent collection")
    %test:assertEquals(1)
function pstor:query-parent-includes-subcollections() {
    count(ft:search("/db/lucene-test-morebinary/shakespeare", 'speech:"boil bake"')//field)
};

(:~
 : Remove single document.
 :)
declare
    %test:assertEquals(0)
function pstor:remove-single-document() {
    xmldb:remove("/db/lucene-test-morebinary2/shakespeare", "macbeth.xml"),
    count(ft:search("/db/lucene-test-morebinary2/shakespeare", 'speech:"boil bake"')//field)
};

(:~
 : Remove collection.
 :)
declare
    %test:assertEmpty
function pstor:remove-collection() {
    xmldb:remove("/db/lucene-test-morebinary3"),
    ft:search("/db/lucene-test-morebinary3", "para:text")//@uri
};
