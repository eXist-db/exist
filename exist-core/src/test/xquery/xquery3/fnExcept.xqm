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

module namespace fne="http://exist-db.org/xquery/test/function_except";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:setUp
function fne:setup() {
    xmldb:create-collection("/db", "except-test"),
    xmldb:store("/db/except-test", "test-except.xml", document { <container><a/><b/></container>})
};

declare
    %test:tearDown
function fne:cleanup() {
    xmldb:remove("/db/except-test")
};

(: Tests the XQuery `except` operator against an in-memory node on the left and a persistent node on the right :)
declare
    %test:assertEquals("<a/>", "<b/>")
function fne:memtree-except-persistent() {
    let $in-memory := (<a/>, <b/>)
    let $persistent := fn:doc("/db/except-test/test-except.xml")/container/(a|b)

    return ($in-memory except $persistent)
};

(: Tests the XQuery `except` operator against a persistent node on the left and an in-memory node on the right :)
declare
    %test:assertEquals("<a/>", "<b/>")
function fne:persistent-except-memtree() {
    let $in-memory := (<a/>, <b/>)
    let $persistent := fn:doc("/db/except-test/test-except.xml")/container/(a|b)

    return ($persistent except $in-memory)
};
