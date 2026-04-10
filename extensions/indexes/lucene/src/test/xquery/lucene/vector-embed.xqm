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

module namespace ve="http://exist-db.org/xquery/vector-embed/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace vector="http://exist-db.org/xquery/vector";

(:~
 : Spec for vector:xxx functions (extensions/vector).
 : No setUp/tearDown — tests are self-contained.
 :
 : API keys: preferred method is to set OPENAI_API_KEY or COHERE_API_KEY as env vars
 : before starting eXist. To read env vars from XQuery (DBA only):
 :   fn:environment-variable("OPENAI_API_KEY")       (: DBA only — returns value or empty :)
 :   fn:available-environment-variables()             (: DBA only — list all env var names :)
 :)


(:~
 : vector:embed with empty model raises error.
 :)
declare
    %test:assertError("vector:EXVECTOR0004")
function ve:vector-embed-empty-model-errors() {
    vector:embed("hello", "")
};

(:~
 : vector:embed with invalid model-path raises error.
 :)
declare
    %test:assertError("vector:EXVECTOR0001")
function ve:vector-embed-invalid-path-errors() {
    vector:embed("hello", "all-MiniLM-L6-v2", "target/onnx-models/nonexistent-12345")
};

(:~
 : vector:embed with valid model returns array of dimension 384.
 : Pending when model not at target/onnx-models/all-MiniLM-L6-v2.
 :)
declare
    %test:pending("Run via VectorSearchEmbeddingTest when model available")
    %test:assertEquals(384)
function ve:vector-embed-returns-array() {
    array:size(vector:embed("Hello world", "all-MiniLM-L6-v2", "target/onnx-models/all-MiniLM-L6-v2"))
};

(:~
 : vector:embed-batch with empty model raises error.
 :)
declare
    %test:assertError("vector:EXVECTOR0004")
function ve:vector-embed-batch-empty-model-errors() {
    vector:embed-batch(("hello", "world"), "")
};

(:~
 : vector:embed-batch with invalid model-path raises error.
 :)
declare
    %test:assertError("vector:EXVECTOR0001")
function ve:vector-embed-batch-invalid-path-errors() {
    vector:embed-batch(("hello", "world"), "all-MiniLM-L6-v2", "target/onnx-models/nonexistent-12345")
};

(:~
 : vector:embed-batch with valid model returns array of arrays (one per input).
 : Pending when model not at target/onnx-models/all-MiniLM-L6-v2.
 :)
declare
    %test:pending("Run via VectorSearchEmbeddingTest when model available")
    %test:assertEquals(2, 384, 384)
function ve:vector-embed-batch-returns-array-of-arrays() {
    let $batch := vector:embed-batch(("Hello world", "Machine learning"), "all-MiniLM-L6-v2", "target/onnx-models/all-MiniLM-L6-v2")
    return (array:size($batch), array:size($batch(1)), array:size($batch(2)))
};

(:~
 : vector:embed with HTTP API URL (OpenAI) but no API key raises error.
 : Preferred method: set OPENAI_API_KEY (or COHERE_API_KEY) as env var before starting eXist.
 :)
declare
    %test:assertError
function ve:vector-embed-http-api-no-key-errors() {
    vector:embed("hello", "text-embedding-3-small", "https://api.openai.com/v1")
};

(:~
 : vector:embed with HTTP API URL and api-key param uses the key (errors from API if invalid).
 :)
declare
    %test:assertError
function ve:vector-embed-http-api-with-api-key-param-errors() {
    vector:embed("hello", "text-embedding-3-small", "https://api.openai.com/v1", "sk-invalid-key")
};

(:~
 : vector:embed with whitespace-only model raises error.
 :)
declare
    %test:assertError("vector:EXVECTOR0004")
function ve:vector-embed-whitespace-model-errors() {
    vector:embed("hello", "   ")
};

(:~
 : vector:embed-batch with whitespace-only model raises error.
 :)
declare
    %test:assertError("vector:EXVECTOR0004")
function ve:vector-embed-batch-whitespace-model-errors() {
    vector:embed-batch(("hello", "world"), "   ")
};

(:~
 : vector:models() returns sequence of known model names.
 :)
declare
    %test:assertTrue
function ve:vector-models-returns-known-models() {
    let $models := vector:models()
    return $models = "all-MiniLM-L6-v2" and $models = "text-embedding-3-small" and count($models) ge 10
};
