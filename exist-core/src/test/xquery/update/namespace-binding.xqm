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

module namespace unbt = "http://exist-db.org/xquery/update/namespace-binding-test";

import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace myns = "http://www.foo.com";

declare %private variable $unbt:test := document {
    <x xmlns:myns="http://www.bar.com">
        <z/>
    </x>
};

declare
  %test:setup
function unbt:setup() {
  let $xml := fn:parse-xml('<x xmlns:myns="http://www.bar.com"><z/></x>')
  return
    xmldb:store("/db", "namespace-binding-test-1.xml", $xml)
  ,
  xmldb:store("/db", "namespace-binding-test-2.xml", $unbt:test)
};

declare
  %test:teardown
function unbt:teardown() {
  xmldb:remove("/db/namespace-binding-test-1.xml"),
  xmldb:remove("/db/namespace-binding-test-2.xml"),
};

declare
  %test:assertError("XUDY0023")
function unbt:insert-namespaced-attribute-1() {
  update insert attribute myns:baz { "qux" } into doc("/db/namespace-binding-test-1.xml")/x/z
};

declare
  %test:assertError("XUDY0023")
function unbt:insert-namespaced-attribute-2() {
  update insert attribute myns:baz { "qux" } into doc("/db/namespace-binding-test-2.xml")/x/z
};
