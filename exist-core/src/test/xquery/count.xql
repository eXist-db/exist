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

(:~ Additional tests for the fn:count function :)
module namespace cnt="http://exist-db.org/xquery/test/count";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $cnt:TEST_COLLECTION_NAME := "test-count";
declare variable $cnt:TEST_COLLECTION := "/db/" || $cnt:TEST_COLLECTION_NAME;
declare variable $cnt:COLLECTION1_NAME := "test-count-1";
declare variable $cnt:COLLECTION2_NAME := "test-count-2";
declare variable $cnt:COLLECTION1 := $cnt:TEST_COLLECTION || "/" || $cnt:COLLECTION1_NAME;
declare variable $cnt:COLLECTION2 := $cnt:TEST_COLLECTION || "/" || $cnt:COLLECTION2_NAME;

declare
    %test:setUp
function cnt:setup() {
    xmldb:create-collection("/db", $cnt:TEST_COLLECTION_NAME),
    xmldb:create-collection($cnt:TEST_COLLECTION, $cnt:COLLECTION1_NAME),
    xmldb:store($cnt:COLLECTION1, "test1.xml", <test/>),
    xmldb:create-collection($cnt:TEST_COLLECTION, $cnt:COLLECTION2_NAME),
    xmldb:store($cnt:COLLECTION2, "test2xml", <test/>)
};

declare 
    %test:tearDown
function cnt:cleanup() {
    xmldb:remove($cnt:TEST_COLLECTION)
};

declare 
    %test:assertEquals(1, 1)
function cnt:arg-self-on-stored() {
    (collection($cnt:COLLECTION1)/*, collection($cnt:COLLECTION2)/*)/count(.)
};

declare 
    %test:assertEquals(1, 1, 1)
function cnt:arg-self-on-constructed() {
    (<a/>, <b/>, <c/>)/count(.)
};
