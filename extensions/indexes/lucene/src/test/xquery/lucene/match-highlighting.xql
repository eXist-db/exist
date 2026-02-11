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
 : XQSuite tests for match highlighting behaviour on Lucene FT (util:expand highlight-matches).
 : Refactored from matchHighlighting_ftquery_Tests.xml (TestSet).
 : These tests verify that matches are highlighted correctly. They are repeated for matches
 : in both elements and attributes, and for each setting of highlight-matches: none,
 : attributes, elements, both.
 : Note: the kind of index definition (qname / path) does not seem to affect this
 : behaviour, so for clarity only qname-based indexes are included.
 :
 : @author Ron Van den Branden
 :)
module namespace mhlt="http://exist-db.org/xquery/lucene/match-highlighting/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: el, @att.
 :)
declare variable $mhlt:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text qname="el"/>
                <text qname="@att"/>
            </lucene>
        </index>
    </collection>;

(:~
 : Test document.
 :)
declare variable $mhlt:XML as document-node() :=
    document {
        <test>
            <el att="val"><a><b>one</b></a><c>two tree</c></el>
        </test>
    };

declare variable $mhlt:COLLECTION_NAME := "match-highlighting";
declare variable $mhlt:COLLECTION := "/db/" || $mhlt:COLLECTION_NAME;

(:~
 : Expected: no highlighting.
 :)
declare variable $mhlt:EXPECTED_NONE as element(el) :=
    <el att="val"><a><b>one</b></a><c>two tree</c></el>;

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function mhlt:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $mhlt:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $mhlt:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $mhlt:COLLECTION_NAME, "collection.xconf", $mhlt:XCONF),
      xmldb:store($mhlt:COLLECTION, "test.xml", $mhlt:XML),
      xmldb:reindex($mhlt:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function mhlt:tearDown() {
    xmldb:remove($mhlt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $mhlt:COLLECTION_NAME)
};

(:~
 : [lucene FT] element match, match-highlighting=none.
 :)
declare
    %test:assertTrue
function mhlt:element-match-highlight-none() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(., 'tree')]/util:expand(., 'highlight-matches=none')
    return deep-equal($result, $mhlt:EXPECTED_NONE)
};

(:~
 : [lucene FT] element match, match-highlighting=attributes.
 :)
declare
    %test:pending("previously ignored; match-highlighting=attributes")
    %test:assertTrue
function mhlt:element-match-highlight-attributes() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(., 'tree')]/util:expand(., 'highlight-matches=attributes')
    return deep-equal($result, $mhlt:EXPECTED_NONE)
};

(:~
 : [lucene FT] element match, match-highlighting=elements.
 :)
declare
    %test:pending("previously ignored; match-highlighting=elements")
    %test:assertTrue
function mhlt:element-match-highlight-elements() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(., 'tree')]/util:expand(., 'highlight-matches=elements')
    return deep-equal($result, <el att="val"><a><b>one</b></a><c>two <exist:match xmlns:exist="http://exist-db.org/xquery/util/expand">tree</exist:match></c></el>)
};

(:~
 : [lucene FT] element match, match-highlighting=both.
 :)
declare
    %test:pending("previously ignored; match-highlighting=both")
    %test:assertTrue
function mhlt:element-match-highlight-both() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(., 'tree')]/util:expand(., 'highlight-matches=both')
    return deep-equal($result, <el att="val"><a><b>one</b></a><c>two <exist:match xmlns:exist="http://exist-db.org/xquery/util/expand">tree</exist:match></c></el>)
};

(:~
 : [lucene FT] attribute match, match-highlighting=none.
 :)
declare
    %test:assertTrue
function mhlt:attribute-match-highlight-none() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(@att, 'val')]/util:expand(., 'highlight-matches=none')
    return deep-equal($result, $mhlt:EXPECTED_NONE)
};

(:~
 : [lucene FT] attribute match, match-highlighting=attributes.
 :)
declare
    %test:pending("previously ignored; match-highlighting=attributes")
    %test:assertTrue
function mhlt:attribute-match-highlight-attributes() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(@att, 'val')]/util:expand(., 'highlight-matches=attributes')
    return deep-equal($result, <el att="|||val|||"><a><b>one</b></a><c>two tree</c></el>)
};

(:~
 : [lucene FT] attribute match, match-highlighting=elements.
 :)
declare
    %test:assertTrue
function mhlt:attribute-match-highlight-elements() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(@att, 'val')]/util:expand(., 'highlight-matches=elements')
    return deep-equal($result, $mhlt:EXPECTED_NONE)
};

(:~
 : [lucene FT] attribute match, match-highlighting=both.
 :)
declare
    %test:pending("previously ignored; match-highlighting=both")
    %test:assertTrue
function mhlt:attribute-match-highlight-both() {
    let $result := doc($mhlt:COLLECTION || "/test.xml")//el[ft:query(@att, 'val')]/util:expand(., 'highlight-matches=both')
    return deep-equal($result, <el att="|||val|||"><a><b>one</b></a><c>two tree</c></el>)
};
