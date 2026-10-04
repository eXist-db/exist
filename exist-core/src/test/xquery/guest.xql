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

module namespace guest="http://exist-db.org/xquery/guest";
declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Name of the collection of this file, unique among the files of the suite. :)
declare variable $guest:COLLECTION_NAME := "test-guest";

(:~ Full path of the collection of this file. :)
declare variable $guest:COLLECTION := "/db/" || $guest:COLLECTION_NAME;

declare
    %test:setUp
function guest:setup() {
    xmldb:create-collection("/db", $guest:COLLECTION_NAME),
    xmldb:store($guest:COLLECTION, "test.xml", <foo/>)
};
declare
    %test:tearDown
function guest:tearDown() {
    xmldb:remove($guest:COLLECTION)
};
declare
    %test:user("guest", "guest")
    %test:assertError("java:org.xmldb.api.base.XMLDBException")
function guest:create-collection() {
    xmldb:create-collection("/db", "guest-collection")
};
declare
    %test:user("guest", "guest")
    %test:assertError("java:org.xmldb.api.base.XMLDBException")
function guest:store-document() {
    xmldb:store($guest:COLLECTION, "test2.xml", <guest-was-here/>)
};
declare
    %test:user("guest", "guest")
    %test:assertError("java:org.xmldb.api.base.XMLDBException")
function guest:overwrite-document() {
    xmldb:store($guest:COLLECTION, "test.xml", <guest-was-here/>)
};
declare
    %test:user("guest", "guest")
    %test:assertError("java:org.xmldb.api.base.XMLDBException")
function guest:remove-document() {
    xmldb:remove($guest:COLLECTION, "test.xml")
};
declare
    %test:user("guest", "guest")
    %test:assertError("java:org.xmldb.api.base.XMLDBException")
function guest:remove-collection() {
    xmldb:remove($guest:COLLECTION)
};