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

module namespace id = "http://exist-db.org/test/securitymanager/id";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace mod1 = "http://module1";
declare namespace sm = "http://exist-db.org/xquery/securitymanager";

declare variable $id:TEST_COLLECTION_NAME := "test-id";
declare variable $id:TEST_COLLECTION_PATH := "/db/test-id";
declare variable $id:TEST_MODULE_NAME := "mod1.xqm";

declare
    %test:setUp
function id:setup() {
    xmldb:create-collection("/db", $id:TEST_COLLECTION_NAME),
    xmldb:store($id:TEST_COLLECTION_PATH, $id:TEST_MODULE_NAME, 'xquery version "3.0";

module namespace mod1 = "http://module1";

declare function mod1:function1() {
    <mod1>{sm:id()}</mod1>
};
    ', "application/xquery")
};

declare
    %test:tearDown
function id:cleanup() {
    xmldb:remove($id:TEST_COLLECTION_PATH)
};

declare
    %test:assertEquals(1)
function id:from-load-module() {
    let $mod1-fn := fn:load-xquery-module("http://module1", map {
        "location-hints": "xmldb:exist://" || $id:TEST_COLLECTION_PATH || "/" || $id:TEST_MODULE_NAME
    })?functions(xs:QName("mod1:function1"))?0
    return
    	fn:count($mod1-fn()//sm:username)
};

declare
    %test:assertEquals(1)
function id:from-inspect-module-functions() {
    let $mod1-fn := inspect:module-functions(xs:anyURI("xmldb:exist://" || $id:TEST_COLLECTION_PATH || "/" || $id:TEST_MODULE_NAME))[1]
    return
        fn:count($mod1-fn()//sm:username)
};
