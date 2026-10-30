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
 : Full-text matches carried by a named attribute of an ft:query hit, which a location step
 : copies from the element it selects the attribute from. Asserts develop's behavior, so that
 : it does not depend on the size of the context set (see PR 6941).
 :)
module namespace am="http://exist-db.org/xquery/lucene/attribute-matches/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

declare variable $am:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index><lucene><text qname="p"/></lucene></index>
    </collection>;

declare variable $am:COLLECTION_NAME := "test-attribute-matches";
declare variable $am:COLLECTION := "/db/" || $am:COLLECTION_NAME;
declare variable $am:CONF := "/db/system/config/db/" || $am:COLLECTION_NAME;

declare
    %test:setUp
function am:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $am:COLLECTION_NAME),
    xmldb:store($am:CONF, "collection.xconf", $am:XCONF),
    xmldb:create-collection("/db", $am:COLLECTION_NAME),
    xmldb:store($am:COLLECTION, "test.xml",
        <text><p n="p1">an apple a day</p><p n="p2">a pear</p><p n="p3">apple pie</p></text>)
};

declare
    %test:tearDown
function am:tearDown() {
    xmldb:remove($am:COLLECTION),
    xmldb:remove($am:CONF)
};

declare
    %test:assertEquals("true", "true")
function am:named-attribute-carries-the-hit-score() {
    for $hit in collection($am:COLLECTION)//p[ft:query(., "apple")]
    return ft:score($hit/@n) > 0
};

declare
    %test:assertEquals(1, 1)
function am:highlight-after-returning-from-a-named-attribute() {
    for $hit in collection($am:COLLECTION)//p[ft:query(., "apple")]
    return count(util:expand($hit/@n/..)//exist:match)
};

(:~
 : Current behaviour for comparison, not a goal: a wildcard attribute step does not copy the
 : element's matches, so it does not agree with the named step above.
 :)
declare
    %test:assertEquals(0, 0)
function am:wildcard-attribute-carries-no-score() {
    for $hit in collection($am:COLLECTION)//p[ft:query(., "apple")]
    return ft:score($hit/@*)
};
