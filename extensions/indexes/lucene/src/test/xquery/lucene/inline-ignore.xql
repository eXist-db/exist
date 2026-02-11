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
 : XQSuite tests for Lucene configuration inline and ignore nodes.
 : Refactored from inline.xml (TestSet).
 :
 : @author Wolfgang Meier
 :)
module namespace inlg="http://exist-db.org/xquery/lucene/inline-ignore/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: size, p with inline b and ignore note.
 :)
declare variable $inlg:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="size"/>
                <text qname="p">
                    <inline qname="b"/>
                    <ignore qname="note"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~
 : Test document: size, p with inline b, p with ignored note.
 :)
declare variable $inlg:XML as document-node() :=
    document {
        <root>
            <size><width>12</width><height>8</height></size>
            <p>This is <b>un</b>clear.</p>
            <p>This is a paragraph<note>containing an inline note</note>.</p>
        </root>
    };

declare variable $inlg:COLLECTION_NAME := "inline-ignore";
declare variable $inlg:COLLECTION := "/db/" || $inlg:COLLECTION_NAME;

(:~
 : Expected: size for default processing.
 :)
declare variable $inlg:EXPECTED_DEFAULT as element(size) :=
    <size><width>12</width><height>8</height></size>;

(:~
 : Expected: p for inline node.
 :)
declare variable $inlg:EXPECTED_INLINE as element(p) :=
    <p>This is <b>un</b>clear.</p>;

(:~
 : Expected: p for ignored node match outside.
 :)
declare variable $inlg:EXPECTED_IGNORED_MATCH as element(p) :=
    <p>This is a paragraph<note>containing an inline note</note>.</p>;

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function inlg:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $inlg:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $inlg:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $inlg:COLLECTION_NAME, "collection.xconf", $inlg:XCONF),
      xmldb:store($inlg:COLLECTION, "text.xml", $inlg:XML),
      xmldb:reindex($inlg:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function inlg:tearDown() {
    xmldb:remove($inlg:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $inlg:COLLECTION_NAME)
};

(:~
 : Default processing.
 :)
declare
    %test:assertTrue
function inlg:default-processing() {
    let $result := doc($inlg:COLLECTION || "/text.xml")//size[ft:query(., '12')]
    return deep-equal($result, $inlg:EXPECTED_DEFAULT)
};

(:~
 : Inline node.
 :)
declare
    %test:assertTrue
function inlg:inline-node() {
    let $result := doc($inlg:COLLECTION || "/text.xml")//p[ft:query(., 'unclear')]
    return deep-equal($result, $inlg:EXPECTED_INLINE)
};

(:~
 : Inline node: no match.
 :)
declare
    %test:assertEmpty
function inlg:inline-node-no-match() {
    doc($inlg:COLLECTION || "/text.xml")//p[ft:query(., 'clear')]
};

(:~
 : Ignored node: match outside.
 :)
declare
    %test:assertTrue
function inlg:ignored-node-match-outside() {
    let $result := doc($inlg:COLLECTION || "/text.xml")//p[ft:query(., 'paragraph')]
    return deep-equal($result, $inlg:EXPECTED_IGNORED_MATCH)
};

(:~
 : Ignored node: no match.
 :)
declare
    %test:assertEmpty
function inlg:ignored-node-no-match() {
    doc($inlg:COLLECTION || "/text.xml")//p[ft:query(., 'inline')]
};
