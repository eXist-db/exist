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
xquery version "3.0";

(:~ Additional tests for document-node() :)
module namespace dn = "http://exist-db.org/xquery/test/document-node";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $dn:TEST_COLLECTION_NAME := "test-document-node";
declare variable $dn:TEST_COLLECTION := "/db/" || $dn:TEST_COLLECTION_NAME;
declare variable $dn:TEST_DOC_NAME := "test1.xml";
declare variable $dn:TEST_DOC := $dn:TEST_COLLECTION || "/" || $dn:TEST_DOC_NAME;

declare
    %test:setUp
function dn:setup() {
    xmldb:create-collection("/db", $dn:TEST_COLLECTION_NAME),
    xmldb:store($dn:TEST_COLLECTION, $dn:TEST_DOC_NAME, <template/>)
};

declare
    %test:tearDown
function dn:cleanup() {
    xmldb:remove($dn:TEST_COLLECTION)
};

declare
    %test:assertEquals("<template/>")
function dn:persistent-document-node() {
    let $d as document-node() := doc($dn:TEST_DOC)
    return
    	$d
};

declare
    %test:assertEquals("<template/>")
function dn:persistent-document-node-element() {
    let $d as document-node(element()) := doc($dn:TEST_DOC)
    return
    	$d
};

declare
    %test:assertEquals("<template/>")
function dn:persistent-document-node-element-wildcard() {
    let $d as document-node(element(*)) := doc($dn:TEST_DOC)
    return
    	$d
};

declare
    %test:assertEquals("<template/>")
function dn:persistent-document-node-element-name() {
    let $d as document-node(element(template)) := doc($dn:TEST_DOC)
    return
    	$d
};

declare
    %test:assertError("err:XPTY0004")
function dn:persistent-document-node-element-wrong-name() {
    let $d as document-node(element(wrong)) := doc($dn:TEST_DOC)
    return
    	$d
};

declare
    %test:assertTrue
function dn:memtree-document-node() {
    document { element template {} } instance of document-node()
};

declare
    %test:assertTrue
function dn:memtree-document-node-element() {
    document { element template {} } instance of document-node(element())
};

declare
    %test:assertTrue
function dn:memtree-document-node-element-wildcard() {
    document { element template {} } instance of document-node(element(*))
};

declare
    %test:assertTrue
function dn:memtree-document-node-element-name() {
    document { element template {} } instance of document-node(element(template))
};

declare
    %test:assertFalse
function dn:memtree-document-node-element-wrong-name() {
    document { element template {} } instance of document-node(element(wrong))
};
