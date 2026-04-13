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
 : XQSuite tests for match highlighting behaviour on ngram index (util:expand highlight-matches).
 : Refactored from matchHighlighting_ngram_Tests.xml (TestSet).
 : Tests whether matches are highlighted correctly for ngram:contains. Repeated for matches
 : in elements and attributes, and for each setting of highlight-matches: none, attributes,
 : elements, both. Only qname-based indexes are used.
 :
 : @author Ron Van den Branden
 :)
module namespace mhng="http://exist-db.org/xquery/ngram/match-highlighting/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace ngram="http://exist-db.org/xquery/ngram";

(:~
 : Collection config: ngram on el, @att.
 :)
declare variable $mhng:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <ngram qname="el"/>
            <ngram qname="@att"/>
            <ngram qname="p"/>
        </index>
    </collection>;

(:~
 : Test document.
 :)
declare variable $mhng:XML as document-node() :=
    document {
        <test>
            <el att="val"><a><b>one</b></a><c>two tree</c></el>
            <p n="1">無名<c n="、"></c>天地之始</p>
            <p n="2">無名天地之始</p>
            <p>Test p 3</p>
        </test>
    };

(:~
 : Expected: no highlighting.
 :)
declare variable $mhng:EXPECTED_NONE as element(el) :=
    <el att="val"><a><b>one</b></a><c>two tree</c></el>;

declare variable $mhng:COLLECTION_NAME := "match-highlighting-ngram";
declare variable $mhng:COLLECTION := "/db/" || $mhng:COLLECTION_NAME;

(:~
 : setUp: create config hierarchy, collection, store doc, reindex.
 :)
declare
    %test:setUp
function mhng:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $mhng:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $mhng:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $mhng:COLLECTION_NAME, "collection.xconf", $mhng:XCONF),
      xmldb:store($mhng:COLLECTION, "test.xml", $mhng:XML),
      xmldb:reindex($mhng:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function mhng:tearDown() {
    xmldb:remove($mhng:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $mhng:COLLECTION_NAME)
};

(:~
 : [ngram] element match, match-highlighting=none.
 :)
declare
    %test:assertTrue
function mhng:element-match-highlight-none() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(., 'tree')]/util:expand(., 'highlight-matches=none')
    return deep-equal($result, $mhng:EXPECTED_NONE)
};

(:~
 : [ngram] element match, match-highlighting=attributes.
 :)
declare
    %test:pending("previously ignored; match-highlighting=attributes")
    %test:assertTrue
function mhng:element-match-highlight-attributes() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(., 'tree')]/util:expand(., 'highlight-matches=attributes')
    return deep-equal($result, $mhng:EXPECTED_NONE)
};

(:~
 : [ngram] element match, match-highlighting=elements.
 :)
declare
    %test:assertTrue
function mhng:element-match-highlight-elements() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(., 'tree')]/util:expand(., 'highlight-matches=elements')
    return deep-equal($result, <el att="val"><a><b>one</b></a><c>two <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">tree</exist:match></c></el>)
};

(:~
 : [ngram] element match, match-highlighting=both. (Previously ignored in XML; now runs.)
 :)
declare
    %test:assertTrue
function mhng:element-match-highlight-both() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(., 'tree')]/util:expand(., 'highlight-matches=both')
    return deep-equal($result, <el att="val"><a><b>one</b></a><c>two <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">tree</exist:match></c></el>)
};

(:~
 : [ngram] attribute match, match-highlighting=none.
 :)
declare
    %test:assertTrue
function mhng:attribute-match-highlight-none() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(@att, 'val')]/util:expand(., 'highlight-matches=none')
    return deep-equal($result, $mhng:EXPECTED_NONE)
};

(:~
 : [ngram] attribute match, match-highlighting=attributes.
 :)
declare
    %test:pending("previously ignored; match-highlighting=attributes")
    %test:assertTrue
function mhng:attribute-match-highlight-attributes() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(@att, 'val')]/util:expand(., 'highlight-matches=attributes')
    return deep-equal($result, <el att="|||val|||"><a><b>one</b></a><c>two tree</c></el>)
};

(:~
 : [ngram] attribute match, match-highlighting=elements.
 :)
declare
    %test:assertTrue
function mhng:attribute-match-highlight-elements() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(@att, 'val')]/util:expand(., 'highlight-matches=elements')
    return deep-equal($result, $mhng:EXPECTED_NONE)
};

(:~
 : [ngram] attribute match, match-highlighting=both.
 :)
declare
    %test:pending("previously ignored; match-highlighting=both")
    %test:assertTrue
function mhng:attribute-match-highlight-both() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//el[ngram:contains(@att, 'val')]/util:expand(., 'highlight-matches=both')
    return deep-equal($result, <el att="|||val|||"><a><b>one</b></a><c>two tree</c></el>)
};

(: --- GitHub #4222: non-continuous text across empty elements --- :)
(: Hit count for query "名天" should match two p elements :)
declare
    %test:assertEquals(2)
function mhng:issue4222-hit-count() {
    count(doc($mhng:COLLECTION || "/test.xml")//p[ngram:contains(., '名天')])
};

(: Match count: util:expand on ngram hits returns exist:match elements :)
declare
    %test:assertEquals(2)
function mhng:issue4222-match-count-non-continuous-text() {
    let $result := doc($mhng:COLLECTION || "/test.xml")//p[ngram:contains(., '名天')]
    let $hits := util:expand($result)
    return count($hits//exist:match)
};
