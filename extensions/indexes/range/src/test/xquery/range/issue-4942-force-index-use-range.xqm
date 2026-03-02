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
 : XQSuite regression test for GitHub #4942: (# exist:force-index-use #) with
 : range index expressions. Previously threw "Can not use index" even when the
 : index was used (Monex showed correct usage). Tests variable-in-path and
 : direct path forms.
 :
 : @see https://github.com/eXist-db/exist/issues/4942
 :)
module namespace i4942 = "http://exist-db.org/xquery/range/issue-4942/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $i4942:COLLECTION := "i4942-force-index";

declare variable $i4942:DATA as document-node() :=
    document {
        <root><a ID="123"/></root>
    };

declare variable $i4942:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range><create qname="@ID" type="xs:string"/></range>
        </index>
    </collection>;

declare
    %test:setUp
function i4942:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4942:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4942:COLLECTION),
      xmldb:store("/db/" || $i4942:COLLECTION, "test.xml", $i4942:DATA),
      xmldb:store("/db/system/config/db/" || $i4942:COLLECTION, "collection.xconf", $i4942:XCONF),
      xmldb:reindex("/db/" || $i4942:COLLECTION) )
};

declare
    %test:tearDown
function i4942:tearDown() {
    xmldb:remove("/db/" || $i4942:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i4942:COLLECTION)
};

(: Without pragma: baseline should pass. :)
declare
    %test:assertTrue
function i4942:test-without-force-index-use() {
    let $test-data := collection("/db/" || $i4942:COLLECTION)
    for $result in $test-data//root
    return
        count($result/a[@ID = "123"]) eq 1
};

(: With pragma and variable in path. :)
declare
    %test:assertTrue
function i4942:test-with-pragma-variable() {
    let $test-data := collection("/db/" || $i4942:COLLECTION)
    for $result in $test-data//root
    return
        (# exist:force-index-use #) { count($result/a[@ID = "123"]) } eq 1
};

(: With pragma, direct path (no variable). :)
declare
    %test:assertTrue
function i4942:test-with-pragma-direct() {
    (# exist:force-index-use #) { count(collection("/db/" || $i4942:COLLECTION)//root/a[@ID = "123"]) } eq 1
};

(: Without pragma, direct path: baseline. :)
declare
    %test:assertTrue
function i4942:test-direct-without-pragma() {
    count(collection("/db/" || $i4942:COLLECTION)//root/a[@ID = "123"]) eq 1
};
