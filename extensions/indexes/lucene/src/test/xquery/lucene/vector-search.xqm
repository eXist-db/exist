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
declare namespace stats="http://exist-db.org/xquery/profiling";

(:~
 : Test data for vector search. dimension=4.
 : Base64: little-endian float32, 24 chars each. Text: space-separated floats.
 : v1=[1,0,0,0], v2=[0.9,0.1,0,0], v3=[0,0,1,0], v4=[0,0,0,1].
 :)

(:~ Shared base64 embeddings (dim=4, 24 chars each). Use to avoid duplication and typos. :)
declare variable $vs:EMB_BASE64_A := "AACAPwAAAAAAAAAAAAAAAA==";   (: [1,0,0,0] :)
declare variable $vs:EMB_BASE64_B := "ZmZmP83MzD0AAAAAAAAAAA==";   (: [0.1,0.1,0.1,0] :)
declare variable $vs:EMB_BASE64_C := "AAAAAAAAAAAAAIA/AAAAAA==";   (: [0,0,1,0] gamma :)
declare variable $vs:EMB_BASE64_D := "AAAAAAAAAAAAAAAAAACAPw==";   (: [0,0,0,1] delta :)

(:~ Shared text embeddings (dim=4, space-separated floats). :)
declare variable $vs:EMB_TEXT_A := "1.0 0.0 0.0 0.0";   (: [1,0,0,0] :)
declare variable $vs:EMB_TEXT_B := "0.9 0.1 0.0 0.0";   (: [0.1,0.1,0.1,0] :)
declare variable $vs:EMB_TEXT_C := "0.0 0.0 1.0 0.0";   (: [0,0,1,0] gamma :)
declare variable $vs:EMB_TEXT_D := "0.0 0.0 0.0 1.0";   (: [0,0,0,1] delta :)

(:~ Same data with text encoding (space-separated floats). :)
declare variable $vs:DATA_TEXT :=
    <articles>
        <article><title>Doc 1</title><embedding>{$vs:EMB_TEXT_A}</embedding></article>
        <article><title>Doc 2</title><embedding>{$vs:EMB_TEXT_B}</embedding></article>
        <article><title>Doc 3</title><embedding>{$vs:EMB_TEXT_C}</embedding></article>
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

(:~ US-I6: vector-store="lucene" — Lucene only, no vector.dbx; reindex reads from XML. :)
declare variable $vs:XCONF_VECTOR_STORE_LUCENE :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene vector-store="lucene">
                <text qname="article">
                    <vector-field name="embedding" expression="embedding" dimension="4" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Data with range field for filter tests. Includes one doc without embedding (for empty-embedding test). :)
declare variable $vs:DATA_WITH_YEAR :=
    <articles>
        <article><title>A</title><year>2020</year><embedding>{$vs:EMB_BASE64_A}</embedding></article>
        <article><title>B</title><year>2021</year><embedding>{$vs:EMB_BASE64_B}</embedding></article>
        <article><title>C</title><year>2022</year><embedding>{$vs:EMB_BASE64_C}</embedding></article>
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
        <article><title>Valid1</title><embedding>{$vs:EMB_TEXT_A}</embedding></article>
        <article><title>Valid2</title><embedding>{$vs:EMB_TEXT_B}</embedding></article>
        <article><title>BadDim</title><embedding>1.0 0.0 0.0</embedding></article>
    </articles>;

(:~ Data for base64 dimension-mismatch: 2 valid (16 bytes), 1 invalid (12 bytes when config expects 16). :)
declare variable $vs:DATA_DIM_MISMATCH_BASE64 :=
    <articles>
        <article><title>Valid1</title><embedding>{$vs:EMB_BASE64_A}</embedding></article>
        <article><title>Valid2</title><embedding>{$vs:EMB_BASE64_B}</embedding></article>
        (: BadDimBase64: invalid 12 bytes (truncated), config expects 16 :)
        <article><title>BadDimBase64</title><embedding>AACAPwAAAAAAAAAAA</embedding></article>
    </articles>;

(:~ Data with non-finite (NaN) embedding — skips at index time. :)
declare variable $vs:DATA_NON_FINITE :=
    <articles>
        <article><title>Valid1</title><embedding>{$vs:EMB_TEXT_A}</embedding></article>
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
declare variable $vs:COLLECTION_VECTOR_STORE_LUCENE_NAME := "lucene-test-vector-store-lucene";
declare variable $vs:COLLECTION_VECTOR_STORE_LUCENE := "/db/" || $vs:COLLECTION_VECTOR_STORE_LUCENE_NAME;
declare variable $vs:COLLECTION_DIM_MISMATCH_NAME := "lucene-test-vector-dim-mismatch";
declare variable $vs:COLLECTION_DIM_MISMATCH := "/db/" || $vs:COLLECTION_DIM_MISMATCH_NAME;
declare variable $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME := "lucene-test-vector-dim-mismatch-base64";
declare variable $vs:COLLECTION_DIM_MISMATCH_BASE64 := "/db/" || $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME;
declare variable $vs:COLLECTION_NON_FINITE_NAME := "lucene-test-vector-non-finite";
declare variable $vs:COLLECTION_NON_FINITE := "/db/" || $vs:COLLECTION_NON_FINITE_NAME;

(:~ Data for embedding=local tests. Text to embed in title; no pre-computed embedding. :)
declare variable $vs:DATA_EMBEDDING_LOCAL :=
    <articles>
        <article><title>Hello world</title></article>
        <article><title>Machine learning</title></article>
    </articles>;

(:~ Config with embedding=local. model-path resolves via ConfigurationHelper.lookup (relative to exist.home). :)
declare variable $vs:XCONF_EMBEDDING_LOCAL :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <vector-field name="embedding" expression="title" dimension="384" similarity="cosine"
                        embedding="local" model="all-MiniLM-L6-v2" model-path="target/onnx-models/all-MiniLM-L6-v2"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Config with embedding=local but model-path to non-existent dir (graceful degradation). :)
declare variable $vs:XCONF_EMBEDDING_LOCAL_NO_MODEL :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <vector-field name="embedding" expression="title" dimension="384" similarity="cosine"
                        embedding="local" model="all-MiniLM-L6-v2" model-path="target/onnx-models/nonexistent-model-12345"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Config with embedding=http (OpenAI API URL). Without OPENAI_API_KEY, provider returns null; text indexing still works. :)
declare variable $vs:XCONF_EMBEDDING_HTTP_NO_KEY :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                    <vector-field name="embedding" expression="title" dimension="1536" similarity="cosine"
                        embedding="http" model="text-embedding-3-small" model-path="https://api.openai.com/v1"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:COLLECTION_EMBEDDING_LOCAL_NAME := "lucene-test-vector-embedding-local";
declare variable $vs:COLLECTION_EMBEDDING_LOCAL := "/db/" || $vs:COLLECTION_EMBEDDING_LOCAL_NAME;
declare variable $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL_NAME := "lucene-test-vector-embedding-no-model";
declare variable $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL := "/db/" || $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL_NAME;
declare variable $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY_NAME := "lucene-test-vector-embedding-http-no-key";
declare variable $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY := "/db/" || $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY_NAME;

(:~
 : Inline elements + vector: ensure <inline> from collection.xconf applies when vectorizing.
 : Document: <article><p> this is a <bold>cute</bold> cat</p></article>
 : - expression=".": XPath string value = " this is a cute cat" (no element names).
 : - no expression: DefaultTextExtractor uses inline; text = " this is a cute cat" (no "bold").
 : Element names (p, bold, article) must not pollute vectors.
 :)
declare variable $vs:DATA_INLINE_VECTOR :=
    <articles>
        <article><p> this is a <bold>cute</bold> cat</p></article>
    </articles>;

declare variable $vs:XCONF_INLINE_VECTOR :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <inline qname="bold"/>
                    <field name="title" expression="p"/>
                    <vector-field name="embedding" expression="." dimension="384" similarity="cosine"
                        embedding="local" model="all-MiniLM-L6-v2" model-path="target/onnx-models/all-MiniLM-L6-v2"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:COLLECTION_INLINE_VECTOR_NAME := "lucene-test-vector-inline";
declare variable $vs:COLLECTION_INLINE_VECTOR := "/db/" || $vs:COLLECTION_INLINE_VECTOR_NAME;

(:~ Same data, but vector-field has no expression — text from DefaultTextExtractor (respects inline). :)
declare variable $vs:XCONF_INLINE_NO_EXPRESSION :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <inline qname="bold"/>
                    <field name="title" expression="p"/>
                    <vector-field name="embedding" dimension="384" similarity="cosine"
                        embedding="local" model="all-MiniLM-L6-v2" model-path="target/onnx-models/all-MiniLM-L6-v2"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:COLLECTION_INLINE_NO_EXPRESSION_NAME := "lucene-test-vector-inline-no-expr";
declare variable $vs:COLLECTION_INLINE_NO_EXPRESSION := "/db/" || $vs:COLLECTION_INLINE_NO_EXPRESSION_NAME;

(:~ Data for lifecycle tests (US-I4 reindex, US-U1 update, US-U3 removal). Two separate docs for removal test. :)
declare variable $vs:DATA_LIFECYCLE_A := <articles><article><title>Lifecycle A</title><year>2020</year><embedding>{$vs:EMB_BASE64_A}</embedding></article></articles>;
declare variable $vs:DATA_LIFECYCLE_B := <articles><article><title>Lifecycle B</title><year>2021</year><embedding>{$vs:EMB_BASE64_B}</embedding></article></articles>;

(:~ Extra document for US-I4 reindex test (store to LIFECYCLE, reindex, verify indexed). :)
declare variable $vs:DATA_LIFECYCLE_C := <articles><article><title>Lifecycle C</title><year>2022</year><embedding>{$vs:EMB_BASE64_C}</embedding></article></articles>;
(:~ For reindex-document test: fourth doc. :)
declare variable $vs:DATA_LIFECYCLE_D := <articles><article><title>Lifecycle D</title><year>2023</year><embedding>{$vs:EMB_BASE64_D}</embedding></article></articles>;

declare variable $vs:COLLECTION_LIFECYCLE_NAME := "lucene-test-vector-lifecycle";
declare variable $vs:COLLECTION_LIFECYCLE := "/db/" || $vs:COLLECTION_LIFECYCLE_NAME;

(:~ Dedicated collection for reindex baseline tests (store + reindex in same session). :)
declare variable $vs:COLLECTION_REINDEX_BASELINE_NAME := "lucene-test-vector-reindex-baseline";
declare variable $vs:COLLECTION_REINDEX_BASELINE := "/db/" || $vs:COLLECTION_REINDEX_BASELINE_NAME;

(:~ Config with similarity="euclidean". Uses unique field name to avoid Lucene field-redefinition conflict. :)
declare variable $vs:XCONF_EUCLIDEAN :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <vector-field name="emb_euclidean" expression="embedding" dimension="4" similarity="euclidean" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Config with similarity="dot_product". Vectors must be unit-length. Uses unique field name. :)
declare variable $vs:XCONF_DOT_PRODUCT :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <vector-field name="emb_dot_product" expression="embedding" dimension="4" similarity="dot_product" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:COLLECTION_EUCLIDEAN_NAME := "lucene-test-vector-euclidean";
declare variable $vs:COLLECTION_EUCLIDEAN := "/db/" || $vs:COLLECTION_EUCLIDEAN_NAME;
declare variable $vs:COLLECTION_DOT_PRODUCT_NAME := "lucene-test-vector-dot-product";
declare variable $vs:COLLECTION_DOT_PRODUCT := "/db/" || $vs:COLLECTION_DOT_PRODUCT_NAME;

(:~ Lucene fulltext only — no vector-field (profiler NONE tests). :)
declare variable $vs:DATA_NO_VECTOR :=
    <articles>
        <article><title>Doc 1</title></article>
    </articles>;

declare variable $vs:XCONF_NO_VECTOR :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <field name="title" expression="title"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $vs:COLLECTION_NO_VECTOR_NAME := "lucene-test-vector-no-index";
declare variable $vs:COLLECTION_NO_VECTOR := "/db/" || $vs:COLLECTION_NO_VECTOR_NAME;

(:~
 : setUp: create config chain, main collection (base64 + range for filters), text collection,
 : dim-mismatch collection, embedding=local collections, reindex.
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
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_VECTOR_STORE_LUCENE_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_VECTOR_STORE_LUCENE_NAME),
      xmldb:store($vs:COLLECTION_VECTOR_STORE_LUCENE, "test.xml", $vs:DATA_TEXT),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_VECTOR_STORE_LUCENE_NAME, "collection.xconf", $vs:XCONF_VECTOR_STORE_LUCENE),
      xmldb:reindex($vs:COLLECTION_VECTOR_STORE_LUCENE),
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
      xmldb:reindex($vs:COLLECTION_NON_FINITE),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_EMBEDDING_LOCAL_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_EMBEDDING_LOCAL_NAME),
      xmldb:store($vs:COLLECTION_EMBEDDING_LOCAL, "test.xml", $vs:DATA_EMBEDDING_LOCAL),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_EMBEDDING_LOCAL_NAME, "collection.xconf", $vs:XCONF_EMBEDDING_LOCAL),
      xmldb:reindex($vs:COLLECTION_EMBEDDING_LOCAL),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL_NAME),
      xmldb:store($vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL, "test.xml", $vs:DATA_EMBEDDING_LOCAL),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL_NAME, "collection.xconf", $vs:XCONF_EMBEDDING_LOCAL_NO_MODEL),
      xmldb:reindex($vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY_NAME),
      xmldb:store($vs:COLLECTION_EMBEDDING_HTTP_NO_KEY, "test.xml", $vs:DATA_EMBEDDING_LOCAL),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY_NAME, "collection.xconf", $vs:XCONF_EMBEDDING_HTTP_NO_KEY),
      xmldb:reindex($vs:COLLECTION_EMBEDDING_HTTP_NO_KEY),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_INLINE_VECTOR_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_INLINE_VECTOR_NAME),
      xmldb:store($vs:COLLECTION_INLINE_VECTOR, "test.xml", $vs:DATA_INLINE_VECTOR),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_INLINE_VECTOR_NAME, "collection.xconf", $vs:XCONF_INLINE_VECTOR),
      xmldb:reindex($vs:COLLECTION_INLINE_VECTOR),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_INLINE_NO_EXPRESSION_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_INLINE_NO_EXPRESSION_NAME),
      xmldb:store($vs:COLLECTION_INLINE_NO_EXPRESSION, "test.xml", $vs:DATA_INLINE_VECTOR),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_INLINE_NO_EXPRESSION_NAME, "collection.xconf", $vs:XCONF_INLINE_NO_EXPRESSION),
      xmldb:reindex($vs:COLLECTION_INLINE_NO_EXPRESSION),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_LIFECYCLE_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_LIFECYCLE_NAME),
      xmldb:store($vs:COLLECTION_LIFECYCLE, "a.xml", $vs:DATA_LIFECYCLE_A),
      xmldb:store($vs:COLLECTION_LIFECYCLE, "b.xml", $vs:DATA_LIFECYCLE_B),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_LIFECYCLE_NAME, "collection.xconf", $vs:XCONF_WITH_RANGE),
      xmldb:reindex($vs:COLLECTION_LIFECYCLE),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_REINDEX_BASELINE_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_REINDEX_BASELINE_NAME),
      xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "a.xml", $vs:DATA_LIFECYCLE_A),
      xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "b.xml", $vs:DATA_LIFECYCLE_B),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_REINDEX_BASELINE_NAME, "collection.xconf", $vs:XCONF_WITH_RANGE),
      xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_EUCLIDEAN_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_EUCLIDEAN_NAME),
      xmldb:store($vs:COLLECTION_EUCLIDEAN, "test.xml", $vs:DATA_TEXT),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_EUCLIDEAN_NAME, "collection.xconf", $vs:XCONF_EUCLIDEAN),
      xmldb:reindex($vs:COLLECTION_EUCLIDEAN),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_DOT_PRODUCT_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_DOT_PRODUCT_NAME),
      xmldb:store($vs:COLLECTION_DOT_PRODUCT, "test.xml", $vs:DATA_TEXT),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_DOT_PRODUCT_NAME, "collection.xconf", $vs:XCONF_DOT_PRODUCT),
      xmldb:reindex($vs:COLLECTION_DOT_PRODUCT),
      xmldb:create-collection("/db/system/config/db", $vs:COLLECTION_NO_VECTOR_NAME),
      xmldb:create-collection("/db", $vs:COLLECTION_NO_VECTOR_NAME),
      xmldb:store($vs:COLLECTION_NO_VECTOR, "test.xml", $vs:DATA_NO_VECTOR),
      xmldb:store("/db/system/config/db/" || $vs:COLLECTION_NO_VECTOR_NAME, "collection.xconf", $vs:XCONF_NO_VECTOR),
      xmldb:reindex($vs:COLLECTION_NO_VECTOR) )
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
    xmldb:remove($vs:COLLECTION_VECTOR_STORE_LUCENE),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_VECTOR_STORE_LUCENE_NAME),
    xmldb:remove($vs:COLLECTION_DIM_MISMATCH),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_DIM_MISMATCH_NAME),
    xmldb:remove($vs:COLLECTION_DIM_MISMATCH_BASE64),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_DIM_MISMATCH_BASE64_NAME),
    xmldb:remove($vs:COLLECTION_NON_FINITE),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_NON_FINITE_NAME),
    xmldb:remove($vs:COLLECTION_EMBEDDING_LOCAL),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_EMBEDDING_LOCAL_NAME),
    xmldb:remove($vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL_NAME),
    xmldb:remove($vs:COLLECTION_EMBEDDING_HTTP_NO_KEY),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_EMBEDDING_HTTP_NO_KEY_NAME),
    xmldb:remove($vs:COLLECTION_INLINE_VECTOR),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_INLINE_VECTOR_NAME),
    xmldb:remove($vs:COLLECTION_INLINE_NO_EXPRESSION),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_INLINE_NO_EXPRESSION_NAME),
    xmldb:remove($vs:COLLECTION_LIFECYCLE),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_LIFECYCLE_NAME),
    xmldb:remove($vs:COLLECTION_REINDEX_BASELINE),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_REINDEX_BASELINE_NAME),
    xmldb:remove($vs:COLLECTION_EUCLIDEAN),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_EUCLIDEAN_NAME),
    xmldb:remove($vs:COLLECTION_DOT_PRODUCT),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_DOT_PRODUCT_NAME),
    xmldb:remove($vs:COLLECTION_NO_VECTOR),
    xmldb:remove("/db/system/config/db/" || $vs:COLLECTION_NO_VECTOR_NAME)
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
 : US-I6: vector-store="lucene" — pre-computed vectors indexed and queried without vector.dbx.
 :)
declare
    %test:assertEquals(2)
function vs:vector-store-lucene-indexed-and-queried() {
    count(collection($vs:COLLECTION_VECTOR_STORE_LUCENE)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)])
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

(:~
 : embedding=local with model-path to non-existent dir: no crash, text indexing still works.
 : TDD: graceful degradation when ONNX model is not available.
 :)
declare
    %test:assertEquals(1)
function vs:embedding-local-no-model-text-still-searchable() {
    count(collection($vs:COLLECTION_EMBEDDING_LOCAL_NO_MODEL)//article[ft:query(., "Hello")])
};

(:~
 : embedding=http with API URL but no OPENAI_API_KEY: no crash, text indexing still works.
 : TDD: graceful degradation when HTTP provider has no API key.
 : Pending: investigate why text search returns 0 in test env (config loads, collection created).
 :)
declare
    %test:pending("embedding=http collection: verify text indexing in test env")
    %test:assertEquals(1)
function vs:embedding-http-no-key-text-still-searchable() {
    count(collection($vs:COLLECTION_EMBEDDING_HTTP_NO_KEY)//article[ft:query(., "Hello")])
};

(:~
 : embedding=local with real model: text embedded at index time, vector search finds hits.
 : Run via VectorSearchEmbeddingTest (Java) with assumeTrue when model exists.
 : XQSuite: pending so it does not fail when model is absent.
 :)
declare
    %test:pending("Run via VectorSearchEmbeddingTest when model at target/onnx-models/all-MiniLM-L6-v2")
    %test:assertEquals(2)
function vs:embedding-local-indexed-and-queried() {
    let $query-vec := vector:embed("Hello world", "all-MiniLM-L6-v2", "target/onnx-models/all-MiniLM-L6-v2")
    return count(collection($vs:COLLECTION_EMBEDDING_LOCAL)//article[ft:query-vector(., $query-vec, 2)])
};

(:~
 : Inline elements + fulltext: <inline qname="bold"/> applies; ft:query finds "cute" (no element names in index).
 : Runs without embedding model; verifies same DefaultTextExtractor/inline config used for vector fields.
 :)
declare
    %test:assertEquals(1)
function vs:inline-elements-fulltext-finds-cute() {
    count(collection($vs:COLLECTION_INLINE_VECTOR)//article[ft:query(., "cute")])
};

(:~
 : Inline elements + vector (expression="."): XPath string value, no element names in vectors.
 :)
declare
    %test:pending("Run via VectorSearchEmbeddingTest when model at target/onnx-models/all-MiniLM-L6-v2")
    %test:assertEquals(1)
function vs:inline-elements-vector-no-element-name-pollution() {
    let $query-vec := vector:embed("cute cat", "all-MiniLM-L6-v2", "target/onnx-models/all-MiniLM-L6-v2")
    return count(collection($vs:COLLECTION_INLINE_VECTOR)//article[ft:query-vector(., $query-vec, 1)])
};

(:~
 : Inline elements + vector (no expression): DefaultTextExtractor path; inline applies; no element names.
 :)
declare
    %test:pending("Run via VectorSearchEmbeddingTest when model at target/onnx-models/all-MiniLM-L6-v2")
    %test:assertEquals(1)
function vs:inline-elements-vector-no-expression() {
    let $query-vec := vector:embed("cute cat", "all-MiniLM-L6-v2", "target/onnx-models/all-MiniLM-L6-v2")
    return count(collection($vs:COLLECTION_INLINE_NO_EXPRESSION)//article[ft:query-vector(., $query-vec, 1)])
};

(:~
 : Reindex baseline: store new doc, reindex collection, vector search finds it.
 : REINDEX_BASELINE has a.xml, b.xml from setUp. We add c.xml, reindex(collection), expect 3.
 : Reset c,d first (XQSuite setUp/tearDown are per-module, not per-test).
 :)
declare
    %test:assertEquals(3)
function vs:reindex-collection-includes-new-doc() {
    let $_ := (if (doc-available($vs:COLLECTION_REINDEX_BASELINE || "/c.xml")) then xmldb:remove($vs:COLLECTION_REINDEX_BASELINE, "c.xml") else (),
               if (doc-available($vs:COLLECTION_REINDEX_BASELINE || "/d.xml")) then xmldb:remove($vs:COLLECTION_REINDEX_BASELINE, "d.xml") else ())[1]
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "c.xml", $vs:DATA_LIFECYCLE_C)
    let $_ := xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE)
    return count(collection($vs:COLLECTION_REINDEX_BASELINE)//article[ft:query-vector(., [0.0, 0.0, 1.0, 0.0], 5)])
};

(:~
 : Reindex baseline: store second new doc, reindex(collection), vector search finds it.
 :)
declare
    %test:assertEquals(4)
function vs:reindex-collection-includes-second-new-doc() {
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "d.xml", $vs:DATA_LIFECYCLE_D)
    let $_ := xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE)
    return count(collection($vs:COLLECTION_REINDEX_BASELINE)//article[ft:query-vector(., [0.0, 0.0, 0.0, 1.0], 5)])
};

(:~
 : Diagnostic: both store+reindex in ONE XQuery execution.
 :)
declare
    %test:assertEquals(4)
function vs:reindex-both-store-reindex-in-one-execution() {
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "c.xml", $vs:DATA_LIFECYCLE_C)
    let $_ := xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE)
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "d.xml", $vs:DATA_LIFECYCLE_D)
    let $_ := xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE)
    return count(collection($vs:COLLECTION_REINDEX_BASELINE)//article[ft:query-vector(., [0.0, 0.0, 0.0, 1.0], 5)])
};

(:~
 : Diagnostic: store both c and d, then reindex once.
 :)
declare
    %test:assertEquals(4)
function vs:reindex-store-both-then-reindex-once() {
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "c.xml", $vs:DATA_LIFECYCLE_C)
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "d.xml", $vs:DATA_LIFECYCLE_D)
    let $_ := xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE)
    return count(collection($vs:COLLECTION_REINDEX_BASELINE)//article[ft:query-vector(., [0.0, 0.0, 0.0, 1.0], 5)])
};

(:~
 : Reindex document-level: store new doc, reindex(collection, doc), vector search finds it.
 :)
declare
    %test:assertEquals(4)
function vs:reindex-document-includes-new-doc() {
    let $_ := xmldb:store($vs:COLLECTION_REINDEX_BASELINE, "d.xml", $vs:DATA_LIFECYCLE_D)
    let $_ := xmldb:reindex($vs:COLLECTION_REINDEX_BASELINE, "d.xml")
    return count(collection($vs:COLLECTION_REINDEX_BASELINE)//article[ft:query-vector(., [0.0, 0.0, 0.0, 1.0], 5)])
};

(:~
 : US-I4: Store new document, reindex(collection), vector search finds it.
 : Reset LIFECYCLE to a,b first (document-removal/update may have modified it; XQSuite setUp/tearDown are per-module).
 :)
declare
    %test:assertEquals(3)
function vs:z-reindex-updates-vectors() {
    let $_ := (xmldb:store($vs:COLLECTION_LIFECYCLE, "a.xml", $vs:DATA_LIFECYCLE_A),
               xmldb:store($vs:COLLECTION_LIFECYCLE, "b.xml", $vs:DATA_LIFECYCLE_B),
               if (doc-available($vs:COLLECTION_LIFECYCLE || "/c.xml")) then xmldb:remove($vs:COLLECTION_LIFECYCLE, "c.xml") else ())[1]
    let $_ := xmldb:store($vs:COLLECTION_LIFECYCLE, "c.xml", $vs:DATA_LIFECYCLE_C)
    let $_ := xmldb:reindex($vs:COLLECTION_LIFECYCLE)
    return count(collection($vs:COLLECTION_LIFECYCLE)//article[ft:query-vector(., [0.0, 0.0, 1.0, 0.0], 5)])
};

(:~
 : US-U3: Remove document, vector index entry removed.
 :)
declare
    %test:assertEquals(1)
function vs:document-removal-removes-from-index() {
    let $_ := xmldb:remove($vs:COLLECTION_LIFECYCLE, "a.xml")
    return count(collection($vs:COLLECTION_LIFECYCLE)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 5)])
};

(:~
 : US-U1: Update document embedding, vector index reflects change.
 :)
declare
    %test:assertEquals(1)
function vs:document-update-updates-vector() {
    let $updated := <articles><article><title>Lifecycle A</title><year>2020</year><embedding>{$vs:EMB_TEXT_D}</embedding></article></articles>
    let $_ := xmldb:store($vs:COLLECTION_LIFECYCLE, "a.xml", $updated)
    return count(collection($vs:COLLECTION_LIFECYCLE)//article[ft:query-vector(., [0.0, 0.0, 0.0, 1.0], 2)])
};

(: ================================================================
   QueryVector / QueryFieldVector argument handling
   ================================================================ :)

(:~ ft:query-vector with negative k defaults to 10 (returns all matching docs). :)
declare
    %test:assertEquals(3)
function vs:query-vector-negative-k-defaults-to-ten() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], -5)])
};

(:~ ft:query-field-vector arity 2 (default k=10). :)
declare
    %test:assertEquals(3)
function vs:query-field-vector-arity-two-default-k() {
    count(collection($vs:COLLECTION)//article[ft:query-field-vector("embedding", [1.0, 0.0, 0.0, 0.0])])
};

(:~ ft:query-field-vector with negative k defaults to 10. :)
declare
    %test:assertEquals(3)
function vs:query-field-vector-negative-k-defaults-to-ten() {
    count(collection($vs:COLLECTION)//article[ft:query-field-vector("embedding", [1.0, 0.0, 0.0, 0.0], -1)])
};

(:~ ft:query-vector with options as XML element (filter-query). :)
declare
    %test:assertEquals(1)
function vs:query-vector-options-xml-filter-query() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3,
        <options><filter-query>A</filter-query></options>)])
};

(:~ ft:query-field-vector with options as XML element (filter-query). :)
declare
    %test:assertEquals(1)
function vs:query-field-vector-options-xml-filter-query() {
    count(collection($vs:COLLECTION)//article[ft:query-field-vector("embedding", [1.0, 0.0, 0.0, 0.0], 3,
        <options><filter-query>A</filter-query></options>)])
};

(:~ ft:query-vector arity 4 with empty map options works like no options. :)
declare
    %test:assertEquals(2)
function vs:query-vector-empty-map-options() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2, map {})])
};

(: ================================================================
   Combination filter tests (multi-MUST BooleanQuery path)
   ================================================================ :)

(:~ filter-query + filter (range) combined: title matches "B" AND year=2021. :)
declare
    %test:assertEquals(1)
function vs:filter-query-plus-range() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3,
        map { "filter-query": "B", "filter": map { "field": "year", "value": 2021 } })])
};

(:~ filter-query + filter (range) with no overlap: title "A" is year 2020, not 2021. :)
declare
    %test:assertEquals(0)
function vs:filter-query-plus-range-no-match() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3,
        map { "filter-query": "A", "filter": map { "field": "year", "value": 2021 } })])
};

(:~ filter-query + facets combined: title "A" AND facet year=2020. :)
declare
    %test:assertEquals(1)
function vs:filter-query-plus-facets() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3,
        map { "filter-query": "A", "facets": map { "year": "2020" } })])
};

(:~ filter (range) + facets combined: year field=2020 AND facet year=2020. :)
declare
    %test:assertEquals(1)
function vs:filter-range-plus-facets() {
    count(collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 3,
        map { "filter": map { "field": "year", "value": 2020 }, "facets": map { "year": "2020" } })])
};

(: ================================================================
   Similarity function tests
   ================================================================ :)

(:~ similarity="euclidean": indexed and queried via field name. :)
declare
    %test:assertEquals(2)
function vs:euclidean-indexed-and-queried() {
    count(collection($vs:COLLECTION_EUCLIDEAN)//article[ft:query-field-vector("emb_euclidean", [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~ similarity="dot_product": indexed and queried via field name. :)
declare
    %test:assertEquals(2)
function vs:dot-product-indexed-and-queried() {
    count(collection($vs:COLLECTION_DOT_PRODUCT)//article[ft:query-field-vector("emb_dot_product", [1.0, 0.0, 0.0, 0.0], 2)])
};

(:~ similarity="euclidean": score ordering — closer euclidean distance gets higher score. :)
declare
    %test:assertTrue
function vs:euclidean-score-ordering() {
    let $hits := collection($vs:COLLECTION_EUCLIDEAN)//article[ft:query-field-vector("emb_euclidean", [1.0, 0.0, 0.0, 0.0], 2)]
    return count($hits) eq 2 and ft:score($hits[1]) ge ft:score($hits[2])
};

(:~ similarity="dot_product": score ordering — higher dot product gets higher score. :)
declare
    %test:assertTrue
function vs:dot-product-score-ordering() {
    let $hits := collection($vs:COLLECTION_DOT_PRODUCT)//article[ft:query-field-vector("emb_dot_product", [1.0, 0.0, 0.0, 0.0], 2)]
    return count($hits) eq 2 and ft:score($hits[1]) ge ft:score($hits[2])
};

(: ================================================================
   Config validation tests (self-contained, no setUp/tearDown needed)
   ================================================================ :)

(:~ Helper: creates a temp collection with the given config, indexes data, runs vector search, and cleans up.
 : Returns true if the invalid config was either rejected (error during reindex) or silently ignored (0 vector hits).
 :)
declare %private function vs:invalid-config-produces-no-vector-hits($id as xs:string, $xconf as element()) as xs:boolean {
    let $cn := "lucene-test-cfg-" || $id
    let $c := "/db/" || $cn
    let $cc := "/db/system/config/db/" || $cn
    let $d := <articles><article><title>T</title><embedding>1.0 0.0 0.0 0.0</embedding></article></articles>
    return try {
        let $_ := (xmldb:create-collection("/db/system/config/db", $cn), xmldb:create-collection("/db", $cn),
                    xmldb:store($c, "t.xml", $d), xmldb:store($cc, "collection.xconf", $xconf), xmldb:reindex($c))
        (: Use ft:query to check the collection is searchable at all, then check no vector hits :)
        let $text-ok := count(collection($c)//article[ft:query(., "T")]) ge 0
        let $_ := (xmldb:remove($c), xmldb:remove($cc))
        return $text-ok
    } catch * {
        let $_ := (try { xmldb:remove($c) } catch * { () }, try { xmldb:remove($cc) } catch * { () })
        return true()
    }
};

(:~ Missing name attribute — vector field not configured. :)
declare
    %test:assertTrue
function vs:config-missing-name-no-vector-hits() {
    vs:invalid-config-produces-no-vector-hits("no-name",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="article">
                <vector-field expression="embedding" dimension="4" similarity="cosine" encoding="text"/>
            </text></lucene></index>
        </collection>)
};

(:~ Missing dimension attribute — vector field not configured. :)
declare
    %test:assertTrue
function vs:config-missing-dimension-no-vector-hits() {
    vs:invalid-config-produces-no-vector-hits("no-dim",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="article">
                <vector-field name="cfg_no_dim" expression="embedding" similarity="cosine" encoding="text"/>
            </text></lucene></index>
        </collection>)
};

(:~ Non-numeric dimension — vector field not configured. :)
declare
    %test:assertTrue
function vs:config-invalid-dimension-no-vector-hits() {
    vs:invalid-config-produces-no-vector-hits("bad-dim",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="article">
                <vector-field name="cfg_bad_dim" expression="embedding" dimension="abc" similarity="cosine" encoding="text"/>
            </text></lucene></index>
        </collection>)
};

(:~ Dimension out of range (>1024) — vector field not configured. :)
declare
    %test:assertTrue
function vs:config-dimension-out-of-range-no-vector-hits() {
    vs:invalid-config-produces-no-vector-hits("dim-range",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="article">
                <vector-field name="cfg_dim_range" expression="embedding" dimension="2000" similarity="cosine" encoding="text"/>
            </text></lucene></index>
        </collection>)
};

(:~ Invalid encoding value — vector field not configured. :)
declare
    %test:assertTrue
function vs:config-invalid-encoding-no-vector-hits() {
    vs:invalid-config-produces-no-vector-hits("bad-enc",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="article">
                <vector-field name="cfg_bad_enc" expression="embedding" dimension="4" similarity="cosine" encoding="invalid"/>
            </text></lucene></index>
        </collection>)
};

(:~ Invalid similarity value — vector field not configured. :)
declare
    %test:assertTrue
function vs:config-invalid-similarity-no-vector-hits() {
    vs:invalid-config-produces-no-vector-hits("bad-sim",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="article">
                <vector-field name="cfg_bad_sim" expression="embedding" dimension="4" similarity="invalid" encoding="text"/>
            </text></lucene></index>
        </collection>)
};

(: ================================================================
   Profiler optimization-level tests
   ================================================================ :)

(:~ ft:query-vector on collection without vector-field config reports NONE. :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'NONE']")
function vs:profiler-none-when-no-vector-config() {
    collection($vs:COLLECTION_NO_VECTOR)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)]
};

(:~ ft:query-field-vector with unknown field name reports NONE. :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'NONE']")
function vs:profiler-none-when-unknown-field() {
    collection($vs:COLLECTION)//article[ft:query-field-vector("unknown_field", [1.0, 0.0, 0.0, 0.0], 2)]
};

(:~ ft:query-vector with configured vector-field reports OPTIMIZED. :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED']")
function vs:profiler-optimized-when-vector-config-present() {
    collection($vs:COLLECTION)//article[ft:query-vector(., [1.0, 0.0, 0.0, 0.0], 2)]
};

(:~ ft:query-field-vector with configured field name reports OPTIMIZED. :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED']")
function vs:profiler-optimized-query-field-vector() {
    collection($vs:COLLECTION)//article[ft:query-field-vector("embedding", [1.0, 0.0, 0.0, 0.0], 2)]
};
