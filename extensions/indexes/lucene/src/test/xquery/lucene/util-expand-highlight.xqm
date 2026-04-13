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
 : Consolidated util:expand match-span highlighting regression tests.
 :
 : Merges:
 : - GitHub #4835 (multi-match util:expand on nested p elements)
 : - GitHub #4584 (util:expand across spanning text nodes/inline elements)
 :)
module namespace ueh="http://exist-db.org/xquery/lucene/util-expand-highlight/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist = "http://exist.sourceforge.net/NS/exist";
declare namespace wwp = "http://www.wwp.northeastern.edu/ns/textbase";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";
declare namespace util = "http://exist-db.org/xquery/util";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(: #4835 -------------------------------------------------------------- :)

declare variable $ueh:XML-multiple-nested := document {
    <root>
        <div>
            <p>Letter</p>
            <p>LETTER</p>
        </div>
        <div>
            <p>letter</p>
            <p>leTTer</p>
            <div>
                <p>LeTtEr</p>
            </div>
        </div>
    </root>
};

declare variable $ueh:XML-two-in-one := document {
    <root>
        <p>Letter and letter</p>
    </root>
};

declare variable $ueh:XCONF-4835 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene>
                <text qname="p"/>
                <text qname="h1"/>
                <text qname="h2"/>
                <inline qname="i"/>
            </lucene>
        </index>
    </collection>;

declare variable $ueh:COLL_4835 := "/db/lucene-test-util-expand-highlight-4835";

(: #4584 -------------------------------------------------------------- :)

declare variable $ueh:XML-4584 :=
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

declare variable $ueh:XCONF-4584 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:wwp="http://www.wwp.northeastern.edu/ns/textbase"
               xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="wwp:quote"/>
                <inline qname="wwp:vuji"/>
            </lucene>
        </index>
    </collection>;

declare variable $ueh:COLL_4584 := "/db/lucene-test-util-expand-highlight-4584";

(: shared setUp/tearDown ------------------------------------------------ :)

declare
    %test:setUp
function ueh:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    return (
        xmldb:create-collection("/db", "lucene-test-util-expand-highlight-4835"),
        xmldb:create-collection("/db/system/config/db", "lucene-test-util-expand-highlight-4835"),
        xmldb:store($ueh:COLL_4835, "multiple-nested.xml", $ueh:XML-multiple-nested),
        xmldb:store($ueh:COLL_4835, "two-in-one.xml", $ueh:XML-two-in-one),
        xmldb:store("/db/system/config/db/lucene-test-util-expand-highlight-4835", "collection.xconf", $ueh:XCONF-4835),
        xmldb:reindex($ueh:COLL_4835),

        xmldb:create-collection("/db", "lucene-test-util-expand-highlight-4584"),
        xmldb:create-collection("/db/system/config/db", "lucene-test-util-expand-highlight-4584"),
        xmldb:store($ueh:COLL_4584, "test.xml", $ueh:XML-4584),
        xmldb:store("/db/system/config/db/lucene-test-util-expand-highlight-4584", "collection.xconf", $ueh:XCONF-4584),
        xmldb:reindex($ueh:COLL_4584)
    )
};

declare
    %test:tearDown
function ueh:tearDown() {
    (
        xmldb:remove($ueh:COLL_4835),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-4835"),
        xmldb:remove($ueh:COLL_4584),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-4584")
    )
};

(: #4835 tests ---------------------------------------------------------- :)

(: Parent div[ft:query(p,...)] – 3 divs contain matching p (incl. nested div). :)
declare
    %test:assertEquals(3)
function ueh:issue4835-multiple-nested-parent-hits-count() {
    let $doc := doc($ueh:COLL_4835 || "/multiple-nested.xml")
    let $hits := $doc/root//div[ft:query(p, "letter")]
    return count($hits)
};

(: All 5 matching p elements should get exist:match. :)
declare
    %test:assertEquals(5)
function ueh:issue4835-multiple-nested-parent-matches-count() {
    let $doc := doc($ueh:COLL_4835 || "/multiple-nested.xml")
    let $hits := $doc/root//div[ft:query(p, "letter")]
    let $result := util:expand($hits)
    return count($result//exist:match)
};

(: Direct p hits: all 5 should get exist:match. :)
declare
    %test:assertEquals(5)
function ueh:issue4835-multiple-nested-direct-matches-count() {
    let $doc := doc($ueh:COLL_4835 || "/multiple-nested.xml")
    let $hits := $doc//p[ft:query(., "letter")]
    let $result := util:expand($hits)
    return count($result//exist:match)
};

(: Two matches in one p: both should get exist:match. :)
declare
    %test:assertEquals(2)
function ueh:issue4835-two-in-one-matches-count() {
    let $doc := doc($ueh:COLL_4835 || "/two-in-one.xml")
    let $hits := $doc//p[ft:query(., "letter")]
    let $result := util:expand($hits)
    return count($result//exist:match)
};

(: #4584 tests ---------------------------------------------------------- :)

declare %private function ueh:issue4584-expand-match($word as xs:string) as xs:string {
    string-join(
        collection($ueh:COLL_4584)//wwp:quote[ft:query(., $word)]/util:expand(.)//exist:match/normalize-space(),
        ""
    )
};

(: Hit count: "joy" should be found once. :)
declare
    %test:assertEquals(1)
function ueh:issue4584-find-joy-count() {
    count(collection($ueh:COLL_4584)//wwp:quote[ft:query(., "joy")])
};

(: Hit count: "rose" should be found once. :)
declare
    %test:assertEquals(1)
function ueh:issue4584-find-rose-count() {
    count(collection($ueh:COLL_4584)//wwp:quote[ft:query(., "rose")])
};

(: util:expand for "joy": all portions wrapped. :)
declare
    %test:assertEquals("joy")
function ueh:issue4584-expand-joy-full-match() {
    ueh:issue4584-expand-match("joy")
};

(: util:expand for "rose": all portions wrapped. :)
declare
    %test:assertEquals("rose")
function ueh:issue4584-expand-rose-full-match() {
    ueh:issue4584-expand-match("rose")
};

