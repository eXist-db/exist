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
 :)

(:~
 : vector:embed with empty model raises error.
 :)
declare
    %test:assertError
function ve:vector-embed-empty-model-errors() {
    vector:embed("hello", "")
};

(:~
 : vector:embed with invalid model-path raises error.
 :)
declare
    %test:assertError
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
