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
 : XQSuite regression tests for ngram index with non-continuous text (empty elements).
 : When a match spans across empty elements (e.g. "名天" in "無名" + empty &lt;c/&gt; + "天地之始"),
 : util:expand should return one match per element hit, not one per text node.
 :
 : @see https://github.com/eXist-db/exist/issues/4222
 :)
module namespace i4222="http://exist-db.org/xquery/ngram/issue-4222/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

(:~
 : Collection config: ngram on p.
 :)
declare variable $i4222:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <ngram qname="p"/>
        </index>
    </collection>;

(:~
 : Test document. p1 has "名天" spanning empty &lt;c/&gt;; p2 has "名天" continuous.
 : Query "名天" should match both; util:expand should return 2 exist:match elements.
 :)
declare variable $i4222:XML as document-node() :=
    document {
        <root>
            <pb/>
            <p n="1">無名<c n="、"></c>天地之始</p>
            <pb/>
            <p n="2">無名天地之始</p>
            <pb/>
            <p>Test p 3</p>
        </root>
    };

declare variable $i4222:COLLECTION_NAME := "issue-4222-ngram";
declare variable $i4222:COLLECTION := "/db/" || $i4222:COLLECTION_NAME;

(:~
 : setUp: create config hierarchy, collection, store doc, reindex.
 :)
declare
    %test:setUp
function i4222:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4222:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i4222:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i4222:COLLECTION_NAME, "collection.xconf", $i4222:XCONF),
      xmldb:store($i4222:COLLECTION, "test.xml", $i4222:XML),
      xmldb:reindex($i4222:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function i4222:tearDown() {
    xmldb:remove($i4222:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i4222:COLLECTION_NAME)
};

(:~
 : Hit count: query "名天" should return 2 paragraph elements (p1 and p2).
 : @return count of matching p elements
 :)
declare
    %test:assertEquals(2)
function i4222:hit-count() {
    let $doc := doc($i4222:COLLECTION || "/test.xml")
    let $query := "名天"
    return count($doc//p[ngram:contains(., $query)])
};

(:~
 : Match count: util:expand on ngram hits returns exist:match elements.
 : When a match spans an empty element (p1: "名" + &lt;c/&gt; + "天"), we keep one match
 : wrapping the empty element; p2 has one match. Total: 2 (issue #4222).
 :
 : @see https://github.com/eXist-db/exist/issues/4222
 :)
declare
    %test:assertEquals(2)
function i4222:match-count-non-continuous-text() {
    let $doc := doc($i4222:COLLECTION || "/test.xml")
    let $query := "名天"
    let $result := $doc//p[ngram:contains(., $query)]
    let $hits := util:expand($result)
    return count($hits//exist:match)
};
