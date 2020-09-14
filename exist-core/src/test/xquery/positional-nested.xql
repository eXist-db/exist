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

module namespace npt="http://exist-db.org/test/nested-positional-predicate";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $npt:TEST_COLLECTION_NAME := "test-positional-nested";
declare variable $npt:TEST_COLLECTION_URI := "/db/" || $npt:TEST_COLLECTION_NAME;
declare variable $npt:TEST_DOC_NAME := "test.xml";
declare variable $npt:TEST_DOC_URI := $npt:TEST_COLLECTION_URI || "/" || $npt:TEST_DOC_NAME;

declare variable $npt:DATA :=
    document {
        <xml>
            <a>
                <b>B1</b>
            </a>
            <a>
                <b>B2</b>
                <c>correct</c>
            </a>
            <a>
                <b>B3</b>
                <c>wrong</c>
            </a>
        </xml>
    };

declare
    %test:setUp
function npt:setup() {
    xmldb:create-collection("/db", $npt:TEST_COLLECTION_NAME),
    xmldb:store($npt:TEST_COLLECTION_URI, $npt:TEST_DOC_NAME, $npt:DATA)
};

declare
    %test:tearDown
function npt:cleanup() {
    xmldb:remove($npt:TEST_COLLECTION_URI)
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-memory-1() {
    $npt:DATA//c[../preceding-sibling::a]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-memory-2() {
    $npt:DATA//c[parent::node()/preceding-sibling::a]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-memory-3() {
    $npt:DATA//c/..[preceding-sibling::a]/c
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-memory-4() {
    $npt:DATA//c/parent::node()[preceding-sibling::a]/c
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-database-1() {
    doc($npt:TEST_DOC_URI)//c[../preceding-sibling::a]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-database-2() {
    doc($npt:TEST_DOC_URI)//c[parent::node()/preceding-sibling::a]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-database-3() {
    doc($npt:TEST_DOC_URI)//c/..[preceding-sibling::a]/c
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-database-4() {
    doc($npt:TEST_DOC_URI)//c/parent::node()[preceding-sibling::a]/c
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-memory-predicate() {
    $npt:DATA//c[../preceding-sibling::a[1]]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-database-predicate() {
    doc($npt:TEST_DOC_URI)//c[../preceding-sibling::a[1]]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-memory-position() {
    $npt:DATA//c[../preceding-sibling::a[position() eq 1]]
};

declare
    %test:assertEquals("<c>correct</c>", "<c>wrong</c>")
function npt:in-database-position() {
    doc($npt:TEST_DOC_URI)//c[../preceding-sibling::a[position() eq 1]]
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-memory-predicate-and-path() {
    $npt:DATA//c[../preceding-sibling::a[1]/b = 'B1']
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-database-predicate-and-path() {
    doc($npt:TEST_DOC_URI)//c[../preceding-sibling::a[1]/b = 'B1']
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-memory-position-and-path() {
    $npt:DATA//c[../preceding-sibling::a[position() eq 1]/b = 'B1']
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-database-position-and-path() {
    doc($npt:TEST_DOC_URI)//c[../preceding-sibling::a[position() eq 1]/b = 'B1']
};
