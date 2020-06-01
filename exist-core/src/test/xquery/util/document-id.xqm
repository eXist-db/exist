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

module namespace docid = "http://exist-db.org/test/util/document-id";

import module namespace util = "http://exist-db.org/xquery/util";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:setUp
function docid:setup() {
    xmldb:store("/db", "docid.xml", <doc>/db</doc>),
    xmldb:create-collection("/db", "test-docid"),
    xmldb:store("/db/test-docid", "docid.xml", <doc>/db/test-docid</doc>)
};

declare
    %test:tearDown
function docid:teardown() {
    xmldb:remove("/db/test-docid"),
    xmldb:remove("/db", "docid.xml")
};

declare
    %test:assertEquals('<doc>/db</doc>')
function docid:by-id-root() {
    let $doc := doc("/db/docid.xml")
    let $id := util:absolute-resource-id($doc)
    return
    	util:get-resource-by-absolute-id($id)
};

declare
    %test:assertEquals('<doc>/db/test-docid</doc>')
function docid:by-id() {
    let $doc := doc("/db/test-docid/docid.xml")
    let $id := util:absolute-resource-id($doc)
    return
    	util:get-resource-by-absolute-id($id)
};

declare
    %test:args("1")
    %test:assertEquals('<doc>/db</doc>')
    %test:args("1.1")
    %test:assertEquals('/db')
    %test:args("1.15")
    %test:assertEmpty
function docid:node-by-id($id as xs:string) {
    util:node-by-id(doc("/db/docid.xml"), $id)
};