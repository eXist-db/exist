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

module namespace fni="http://exist-db.org/xquery/test/function_intersect";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:setUp
function fni:setup() {
    xmldb:create-collection("/db", "intersect-test"),
    xmldb:store("/db/intersect-test", "test-intersect.xml", document { <container><a/><b/></container>})
};

declare
    %test:tearDown
function fni:cleanup() {
    xmldb:remove("/db/intersect-test")
};

(: Tests the XQuery intersect operator against an in-memory node on both the left and right sides :)
declare
    %test:assertEquals("<a/>", "<b/>")
function fni:memtree-intersect-memtree() {
    let $in-memory := (<a/>, <b/>)

    return ($in-memory intersect $in-memory)
};

(: Tests the XQuery intersect operator against in-memory node on the left and a persistent node on the right :)
declare
    %test:assertEmpty
function fni:memtree-intersect-persistent() {
    let $in-memory := (<a/>, <b/>)
    let $persistent := fn:doc("/db/intersect-test/test-intersect.xml")/container/(a|b)

    return ($persistent intersect $in-memory)
};
