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

module namespace vs="http://exist-db.org/xquery/vector-search/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Test data for vector search. dimension=4, base64 little-endian float32.
 : v1=[1,0,0,0], v2=[0.9,0.1,0,0], v3=[0,0,1,0].
 :)
 
(:~ Same data with text encoding (space-separated floats). :)
declare variable $vs:DATA_TEXT :=
    <articles>
        <article><title>Doc 1</title><embedding>1.0 0.0 0.0 0.0</embedding></article>
        <article><title>Doc 2</title><embedding>0.9 0.1 0.0 0.0</embedding></article>
        <article><title>Doc 3</title><embedding>0.0 0.0 1.0 0.0</embedding></article>
    </articles>;

(:~ Collection config with vector-field (text encoding). :)
declare variable $vs:XCONF_TEXT :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <vector-field name="embedding" expression="embedding" dimension="4" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Data with range field for filter tests. Includes one doc without embedding (for empty-embedding test). :)
declare variable $vs:DATA_WITH_YEAR :=
    <articles>
        <article><title>A</title><year>2020</year><embedding>AACAPwAAAAAAAAAAAAAAAA==</embedding></article>
        <article><title>B</title><year>2021</year><embedding>ZmZmP83MzD0AAAAAAAAAAA==</embedding></article>
        <article><title>C</title><year>2022</year><embedding>AAAAAAAAAAAAAIA/AAAAAA==</embedding></article>
        <article><title>NoEmbed</title><year>2023</year></article>
    </articles>;

declare variable $vs:XCONF_WITH_RANGE :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <field name="year" expression="year" type="xs:integer"/>
                    <facet dimension="year" expression="year"/>
                    <vector-field name="embedding" expression="embedding" dimension="4" similarity="cosine" encoding="base64"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Data for dimension-mismatch test: 2 valid (4 dims), 1 invalid (3 dims when config expects 4). :)
declare variable $vs:DATA_DIM_MISMATCH :=
    <articles>
        <article><title>Valid1</title><embedding>1.0 0.0 0.0 0.0</embedding></article>
        <article><title>Valid2</title><embedding>0.9 0.1 0.0 0.0</embedding></article>
        <article><title>BadDim</title><embedding>1.0 0.0 0.0</embedding></article>
    </articles>;

(:~ Data for base64 dimension-mismatch: 2 valid (16 bytes), 1 invalid (12 bytes when config expects 16). :)
declare variable $vs:DATA_DIM_MISMATCH_BASE64 :=
    <articles>
        <article><title>Valid1</title><embedding>AACAPwAAAAAAAAAAAAAAAA==</embedding></article>
        <article><title>Valid2</title><embedding>ZmZmP83MzD0AAAAAAAAAAA==</embedding></article>
        <article><title>BadDimBase64</title><embedding>AACAPwAAAAAAAAAAA</embedding></article>
    </articles>;

(:~ Data with non-finite (NaN) embedding — skips at index time. :)
declare variable $vs:DATA_NON_FINITE :=
    <articles>
        <article><title>Valid1</title><embedding>1.0 0.0 0.0 0.0</embedding></article>
        <article><title>NonFinite</title><embedding>1.0 0.0 NaN 0.0</embedding></article>
    </articles>;

declare variable $vs:XCONF_DIM_MISMATCH :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <vector-field name="embedding" expression="embedding" dimension="4" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:XCONF_DIM_MISMATCH_BASE64 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <vector-field name="embedding" expression="embedding" dimension="4" similarity="cosine" encoding="base64"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:XCONF_NON_FINITE :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <vector-field name="embedding" expression="embedding" dimension="4" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:COLLECTION_NAME := "lucene-test-vector-search";
declare variable $vs:COLLECTION := "/db/" || $vs:COLLECTION_NAME;
declare variable $vs:COLLECTION_TEXT_NAME := "lucene-test-vector-search-text";
declare variable $vs:COLLECTION_TEXT := "/db/" || $vs:COLLECTION_TEXT_NAME;
declare variable $vs:COLLECTION_DIM_MISMATCH_NAME := "lucene-test-vector-dim-mismatch";
declare variable $vs:COLLECTION_DIM_MISMATCH := "/db/" || $vs:COLLECTION_DIM_MISMATCH_NAME;
declare variable $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME := "lucene-test-vector-dim-mismatch-base64";
declare variable $vs:COLLECTION_DIM_MISMATCH_BASE64 := "/db/" || $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME;
declare variable $vs:COLLECTION_NON_FINITE_NAME := "lucene-test-vector-non-finite";
declare variable $vs:COLLECTION_NON_FINITE := "/db/" || $vs:COLLECTION_NON_FINITE_NAME;

(:~
 : setUp: create config chain, main collection (base64 + range for filters), text collection,
 : dim-mismatch collection, reindex.
 :)
declare
    %test:setUp
function vs:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_NAME),
      xmldb:store($vs:COLLECTION, "test.xml", $vs:DATA_WITH_YEAR),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_NAME, "collection.xconf", $vs:XCONF_WITH_RANGE),
      xmldb:reindex($vs:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_TEXT_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_TEXT_NAME),
      xmldb:store($vs:COLLECTION_TEXT, "test.xml", $vs:DATA_TEXT),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_TEXT_NAME, "collection.xconf", $vs:XCONF_TEXT),
      xmldb:reindex($vs:COLLECTION_TEXT),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_DIM_MISMATCH_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_DIM_MISMATCH_NAME),
      xmldb:store($vs:COLLECTION_DIM_MISMATCH, "test.xml", $vs:DATA_DIM_MISMATCH),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_DIM_MISMATCH_NAME, "collection.xconf", $vs:XCONF_DIM_MISMATCH),
      xmldb:reindex($vs:COLLECTION_DIM_MISMATCH),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME),
      xmldb:store($vs:COLLECTION_DIM_MISMATCH_BASE64, "test.xml", $vs:DATA_DIM_MISMATCH_BASE64),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME, "collection.xconf", $vs:XCONF_DIM_MISMATCH_BASE64),
      xmldb:reindex($vs:COLLECTION_DIM_MISMATCH_BASE64),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_NON_FINITE_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_NON_FINITE_NAME),
      xmldb:store($vs:COLLECTION_NON_FINITE, "test.xml", $vs:DATA_NON_FINITE),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_NON_FINITE_NAME, "collection.xconf", $vs:XCONF_NON_FINITE),
      xmldb:reindex($vs:COLLECTION_NON_FINITE) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function vs:tearDown() {
    xmldb:remove($vs:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_NAME),
    xmldb:remove($vs:COLLECTION_TEXT),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_TEXT_NAME),
    xmldb:remove($vs:COLLECTION_DIM_MISMATCH),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_DIM_MISMATCH_NAME),
    xmldb:remove($vs:COLLECTION_DIM_MISMATCH_BASE64),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME),
    xmldb:remove($vs:COLLECTION_NON_FINITE),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_NON_FINITE_NAME)
};

(:~
 : (1) base64 embedding indexed and queried.
 : Query vector [1,0,0,0] should return Doc 1 first (most similar), then Doc 2.
 : Predicate semantics match ft:query: count = number of articles in top-k.
 :)
declare
    %test:assertEquals(2)
function vs:base64-indexed-and-queried() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (2) text embedding indexed and queried.
 : Uses text encoding; same similarity semantics. Predicate matches ft:query.
 :)
declare
    %test:assertEquals(2)
function vs:text-embedding-indexed-and-queried() {
    count(collection($vs:COLLECTION_TEXT)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (3a) ft:query-vector arity 2 (default k).
 :)
declare
    %test:assertEquals(3)
function vs:query-vector-arity-two() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0])])
};

(:~
 : (3b) ft:query-vector arity 3 (explicit k).
 :)
declare
    %test:assertEquals(2)
function vs:query-vector-arity-three() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (4) ft:query-field-vector.
 : Same predicate semantics as ft:query-field; count = articles in top-k.
 :)
declare
    %test:assertEquals(2)
function vs:query-field-vector() {
    count(collection($vs:COLLECTION)//article[ft:query-field-vector("embedding", [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (5) filter-query (keyword).
 :)
declare
    %test:assertEquals(1)
function vs:filter-query-keyword() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3, map { "filter-query": "A" })])
};

(:~
 : (6) filter (range).
 :)
declare
    %test:assertEquals(1)
function vs:filter-range() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3, map { "filter": map { "field": "year", "value": 2020 } })])
};

(:~
 : (6b) filter-facets (facet drill-down).
 : Uses facets option to restrict to year=2020.
 :)
declare
    %test:assertEquals(1)
function vs:filter-facets() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3, map { "facets": map { "year": "2020" } })])
};

(:~
 : (7) empty/missing embedding skips field — document still indexed (text fields).
 : DATA_WITH_YEAR includes article with title "NoEmbed" and no embedding.
 :)
declare
    %test:assertEquals(1)
function vs:empty-embedding-skips-field() {
    count(collection($vs:COLLECTION)//article[ft:query(., "NoEmbed")])
};

(:~
 : (8) dimension mismatch skips at index time (log and continue).
 : DATA_DIM_MISMATCH has BadDim with 3-d embedding when config expects 4. Vector search returns 2.
 :)
declare
    %test:assertEquals(2)
function vs:dimension-mismatch-skips-at-index-time() {
    count(collection($vs:COLLECTION_DIM_MISMATCH)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (8b) BadDim document still indexed for text fields (title).
 :)
declare
    %test:assertEquals(1)
function vs:dimension-mismatch-doc-still-text-searchable() {
    count(collection($vs:COLLECTION_DIM_MISMATCH)//article[ft:query(., "BadDim")])
};

(:~
 : (8c) base64 dimension mismatch skips at index time.
 : 12 bytes (3 floats) when config expects 16 (4 floats).
 :)
declare
    %test:assertEquals(2)
function vs:dimension-mismatch-base64-skips-at-index-time() {
    count(collection($vs:COLLECTION_DIM_MISMATCH_BASE64)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (8d) non-finite (NaN) skips at index time — document still text-searchable.
 :)
declare
    %test:assertEquals(1)
function vs:non-finite-skips-at-index-time() {
    count(collection($vs:COLLECTION_NON_FINITE)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~
 : (8e) NonFinite document still indexed for text fields.
 :)
declare
    %test:assertEquals(1)
function vs:non-finite-doc-still-text-searchable() {
    count(collection($vs:COLLECTION_NON_FINITE)//article[ft:query(., "NonFinite")])
};

(:~
 : (9) empty node set returns empty sequence.
 :)
declare
    %test:assertEmpty
function vs:empty-node-set-returns-empty() {
    ft:query-vector((), [1.0, 0.0, 0.0, 0.0], 10)
};

(:~
 : ft:score for vector hits (higher = more similar).
 :)
declare
    %test:assertTrue
function vs:score-higher-for-more-similar() {
    let $hits := collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)]
    return count($hits) eq 2 and ft:score($hits[1]) ge ft:score($hits[2])
};
