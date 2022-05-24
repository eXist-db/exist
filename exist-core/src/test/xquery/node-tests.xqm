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

module namespace nt = "http://exist-db.org/xquery/test/node-tests";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";


declare %private function nt:f-document-node($a as document-node()) {
  $a//text()
};

declare %private function nt:f-document-node-with-document-element($a as document-node(element())) {
  $a//text()
};

declare %private function nt:f-document-node-with-named-document-element($a as document-node(element(a))) {
  $a//text()
};


declare
    %test:assertEmpty
function nt:test-document-node-valid-1() {
  nt:f-document-node(document { () })
};

declare
    %test:assertEquals("is not b")
function nt:test-document-node-valid-2() {
  nt:f-document-node(document {<a>is not b</a>})
};

declare
    %test:assertError("XPTY0004")
function nt:test-document-node-invalid() {
  nt:f-document-node(<a>is not b</a>)
};

declare
    %test:assertEquals("is not b")
function nt:test-document-node-with-document-element-valid() {
  nt:f-document-node-with-document-element(document {<a>is not b</a>})
};

declare
    %test:pending("BaseX returns an empty-sequence, but Saxon raises the error XPTY0004... which should it be?")
    %test:assertError("XPTY0004")
function nt:test-document-node-with-document-element-invalid-1() {
  nt:f-document-node-with-document-element(document { () })
};

declare
    %test:pending("BaseX returns an empty-sequence, but Saxon raises the error XPTY0004... which should it be?")
    %test:assertError("XPTY0004")
function nt:test-document-node-with-document-element-invalid-2() {
  nt:f-document-node-with-document-element(document {<!-- comment -->})
};

declare
    %test:assertEquals("is not b")
function nt:test-document-node-with-named-document-element-valid() {
  nt:f-document-node-with-named-document-element(document {<a>is not b</a>})
};

declare
    %test:assertError("XPTY0004")
function nt:test-document-node-with-named-document-element-invalid-1() {
  nt:f-document-node-with-named-document-element(document { () })
};

declare
    %test:assertError("XPTY0004")
function nt:test-document-node-with-named-document-element-invalid-2() {
  nt:f-document-node-with-named-document-element(document {<!-- comment -->})
};

declare
    %test:assertError("XPTY0004")
function nt:test-document-node-with-named-document-element-invalid-3() {
  nt:f-document-node-with-named-document-element(document {<b>is b</b>})
};
