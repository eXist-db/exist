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

(:~ XQSuite tests for xmldb:reindex with optional mode parameter (all, fulltext, vector). @see plans/lucene10-semantic-vector-search-design.md :)
module namespace t="http://exist-db.org/testsuite/reindex";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $t:COLLECTION_NAME := "reindex-mode-test";
declare variable $t:COLLECTION := "/db/" || $t:COLLECTION_NAME;

declare variable $t:XML := document { <root><p>needle in haystack</p></root> };
declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene><text qname="p"/></lucene>
        </index>
    </collection>;

declare
    %test:setUp
function t:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $t:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $t:COLLECTION_NAME),
      xmldb:store($t:COLLECTION, "test.xml", $t:XML),
      xmldb:store("/db/system/config/db/" || $t:COLLECTION_NAME, "collection.xconf", $t:xconf),
      xmldb:reindex($t:COLLECTION) )
};

declare
    %test:tearDown
function t:tearDown() {
    xmldb:remove($t:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $t:COLLECTION_NAME)
};

declare
    %test:assertTrue
function t:reindex-collection-1arg() {
    xmldb:reindex($t:COLLECTION)
};

declare
    %test:assertTrue
function t:reindex-collection-with-mode-all() {
    xmldb:reindex($t:COLLECTION, "all")
};

declare
    %test:assertTrue
function t:reindex-collection-with-mode-fulltext() {
    xmldb:reindex($t:COLLECTION, "fulltext")
};

declare
    %test:assertTrue
function t:reindex-collection-with-mode-vector() {
    xmldb:reindex($t:COLLECTION, "vector")
};

declare
    %test:assertTrue
function t:reindex-document-2args() {
    xmldb:reindex($t:COLLECTION, "test.xml")
};

declare
    %test:assertTrue
function t:reindex-document-with-mode-all() {
    xmldb:reindex($t:COLLECTION, "test.xml", "all")
};

declare
    %test:assertTrue
function t:reindex-document-with-mode-fulltext() {
    xmldb:reindex($t:COLLECTION, "test.xml", "fulltext")
};

declare
    %test:assertTrue
function t:reindex-document-with-mode-vector() {
    xmldb:reindex($t:COLLECTION, "test.xml", "vector")
};
