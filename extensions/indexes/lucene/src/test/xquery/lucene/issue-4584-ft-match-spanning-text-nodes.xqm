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
 : XQSuite regression test for GitHub #4584: util:expand only tags first
 : text node when Lucene hit spans multiple text nodes (inline elements).
 : "ro<vuji>s</vuji>e" for "rose" – expand should wrap full "rose", not just "ro".
 :
 : Expects one hit per query; string-join assembles match portions across nodes.
 :
 : @see https://github.com/eXist-db/exist/issues/4584
 :)
module namespace i4584 = "http://exist-db.org/xquery/lucene/issue-4584/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace exist = "http://exist.sourceforge.net/NS/exist";
declare namespace wwp = "http://www.wwp.northeastern.edu/ns/textbase";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i4584:XML :=
    <div xmlns="http://www.wwp.northeastern.edu/ns/textbase" xml:lang="en">
        <cit>
            <quote>
                <p>In godlie <wwp:vuji>j</wwp:vuji>oy, but worldlie greefe.</p>
            </quote>
        </cit>
        <cit>
            <quote>
                <p>he finally ro<wwp:vuji>s</wwp:vuji>e superior.</p>
            </quote>
        </cit>
    </div>;

declare variable $i4584:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:wwp="http://www.wwp.northeastern.edu/ns/textbase"
               xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="wwp:quote"/>
                <inline qname="wwp:vuji"/>
            </lucene>
        </index>
    </collection>;

declare variable $i4584:COLLECTION := "i4584-spanning";
declare variable $i4584:COLL_PATH := "/db/" || $i4584:COLLECTION;

declare function i4584:expand-match($word as xs:string) as xs:string {
    string-join(
        collection($i4584:COLL_PATH)//wwp:quote[ft:query(., $word)]/util:expand(.)//exist:match/normalize-space(),
        ""
    )
};

declare
    %test:setUp
function i4584:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db/system/config/db", $i4584:COLLECTION),
      xmldb:create-collection("/db", $i4584:COLLECTION),
      xmldb:store($i4584:COLL_PATH, "test.xml", $i4584:XML),
      xmldb:store("/db/system/config/db/" || $i4584:COLLECTION, "collection.xconf", $i4584:xconf),
      xmldb:reindex($i4584:COLL_PATH) )
};

declare
    %test:tearDown
function i4584:tearDown() {
    xmldb:remove($i4584:COLL_PATH),
    xmldb:remove("/db/system/config/db/" || $i4584:COLLECTION)
};

(: Hit count: "joy" and "rose" both found. :)
declare
    %test:assertEquals(1)
function i4584:find-joy-count() {
    count(collection($i4584:COLL_PATH)//wwp:quote[ft:query(., "joy")])
};

declare
    %test:assertEquals(1)
function i4584:find-rose-count() {
    count(collection($i4584:COLL_PATH)//wwp:quote[ft:query(., "rose")])
};

(: util:expand for "joy": all portions wrapped (was only "j"; fixed #4584). :)
declare
    %test:assertEquals("joy")
function i4584:expand-joy-full-match() {
    i4584:expand-match("joy")
};

(: util:expand for "rose": all portions wrapped (was only "ro"; fixed #4584). :)
declare
    %test:assertEquals("rose")
function i4584:expand-rose-full-match() {
    i4584:expand-match("rose")
};
