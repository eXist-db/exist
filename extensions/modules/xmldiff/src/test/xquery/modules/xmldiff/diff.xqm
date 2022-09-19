(:
 : Copyright (C) 2014, Evolved Binary Ltd
 :
 : This file was originally ported from FusionDB to eXist-db by
 : Evolved Binary, for the benefit of the eXist-db Open Source community.
 : Only the ported code as it appears in this file, at the time that
 : it was contributed to eXist-db, was re-licensed under The GNU
 : Lesser General Public License v2.1 only for use in eXist-db.
 :
 : This license grant applies only to a snapshot of the code as it
 : appeared when ported, it does not offer or infer any rights to either
 : updates of this source code or access to the original source code.
 :
 : The GNU Lesser General Public License v2.1 only license follows.
 :
 : ---------------------------------------------------------------------
 :
 : Copyright (C) 2014, Evolved Binary Ltd
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; version 2.1.
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

module namespace xt = "http://exist-db.org/xquery/xmldiff/test/diff";

import module namespace xmldb = "http://exist-db.org/xquery/xmldb";
import module namespace xmldiff = "http://exist-db.org/xquery/xmldiff";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $xt:TEST_COLLECTION_NAME := "test-xmldiff-compare";
declare variable $xt:TEST_COLLECTION := "/db/" || $xt:TEST_COLLECTION_NAME;

declare variable $xt:TEST_DOC1_NAME := "doc1.xml";
declare variable $xt:TEST_DOC1_PATH := $xt:TEST_COLLECTION || "/" || $xt:TEST_DOC1_NAME;
declare variable $xt:TEST_DOC1_CONTENT := document { <template><a x="1"/></template> };

declare variable $xt:TEST_DOC2_NAME := "doc2.xml";
declare variable $xt:TEST_DOC2_PATH := $xt:TEST_COLLECTION || "/" || $xt:TEST_DOC2_NAME;
declare variable $xt:TEST_DOC2_CONTENT := document { <template><a x="1"/></template> };

declare variable $xt:TEST_DOC3_NAME := "doc3.xml";
declare variable $xt:TEST_DOC3_PATH := $xt:TEST_COLLECTION || "/" || $xt:TEST_DOC3_NAME;
declare variable $xt:TEST_DOC3_CONTENT := document { <template><b x="2"/></template> };


declare
    %test:setUp
function xt:setup() {
    xmldb:create-collection("/db", $xt:TEST_COLLECTION_NAME),
    xmldb:store($xt:TEST_COLLECTION, $xt:TEST_DOC1_NAME, $xt:TEST_DOC1_CONTENT),
    xmldb:store($xt:TEST_COLLECTION, $xt:TEST_DOC2_NAME, $xt:TEST_DOC2_CONTENT),
    xmldb:store($xt:TEST_COLLECTION, $xt:TEST_DOC3_NAME, $xt:TEST_DOC3_CONTENT)
};

declare
    %test:tearDown
function xt:cleanup() {
    xmldb:remove($xt:TEST_COLLECTION)
};

declare
    %test:assertTrue
function xt:persistent-same-content-same-docs() {
    let $a := fn:doc($xt:TEST_DOC1_PATH)
    let $b := fn:doc($xt:TEST_DOC1_PATH)
    return
        xmldiff:diff($a, $b)?equivalent
};

declare
    %test:assertTrue
function xt:persistent-same-content-different-docs() {
    let $a := fn:doc($xt:TEST_DOC1_PATH)
    let $b := fn:doc($xt:TEST_DOC2_PATH)
    return
        xmldiff:diff($a, $b)?equivalent
};

declare
    %test:assertEquals('false', 1, "Expected element tag name 'a' but was 'b'")
function xt:persistent-different-content-docs() {
    let $a := fn:doc($xt:TEST_DOC1_PATH)
    let $b := fn:doc($xt:TEST_DOC3_PATH)
    return
        let $result := xmldiff:diff($a, $b)
        return
            ($result?equivalent, $result?position, fn:substring-before($result?message, " -"))
};
