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
 : - GitHub #2170 (util:expand duplicate content fragments)
 : - GitHub #2755 (util:expand with query-field and analyzer-order config)
 :)
module namespace ueh="http://exist-db.org/xquery/lucene/util-expand-highlight/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist = "http://exist.sourceforge.net/NS/exist";
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
    <div xml:lang="en">
        <cit>
            <quote>
                <p>In godlie <vuji>j</vuji>oy, but worldlie greefe.</p>
            </quote>
        </cit>
        <cit>
            <quote>
                <p>he finally ro<vuji>s</vuji>e superior.</p>
            </quote>
        </cit>
    </div>;

declare variable $ueh:XCONF-4584 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="quote"/>
                <inline qname="vuji"/>
            </lucene>
        </index>
    </collection>;

declare variable $ueh:COLL_4584 := "/db/lucene-test-util-expand-highlight-4584";

(: #2170 -------------------------------------------------------------- :)

declare variable $ueh:XML-2170 := document {
    <test>
        <p>Colorless green ideas sleep furiously. They sleep a furiously ideal green sleep.</p>
        <p>Furiously sleep ideas green colorless. They greenly sleep a furiously ideal sleep.</p>
    </test>
};

declare variable $ueh:XCONF-2170 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="no"/>
            <lucene>
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

declare variable $ueh:COLL_2170 := "/db/lucene-test-util-expand-highlight-2170";

(: #2755 -------------------------------------------------------------- :)

declare variable $ueh:XML-2755 :=
    <test>
        <phrase>All phenomena are devoid of independent existence</phrase>
    </test>;

declare variable $ueh:XCONF-2755-ORDERED :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer id="st" class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text match="//phrase" analyzer="st" field="phrase-st"/>
                <text match="//phrase" analyzer="ws" field="phrase-ws"/>
            </lucene>
        </index>
    </collection>;

declare variable $ueh:XCONF-2755-SWAPPED :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer id="st" class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text match="//phrase" analyzer="ws" field="phrase-ws"/>
                <text match="//phrase" analyzer="st" field="phrase-st"/>
            </lucene>
        </index>
    </collection>;

declare variable $ueh:COLL_2755_ORDERED := "/db/lucene-test-util-expand-highlight-2755-ordered";
declare variable $ueh:COLL_2755_SWAPPED := "/db/lucene-test-util-expand-highlight-2755-swapped";

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
        xmldb:reindex($ueh:COLL_4584),

        xmldb:create-collection("/db", "lucene-test-util-expand-highlight-2170"),
        xmldb:create-collection("/db/system/config/db", "lucene-test-util-expand-highlight-2170"),
        xmldb:store($ueh:COLL_2170, "test.xml", $ueh:XML-2170),
        xmldb:store("/db/system/config/db/lucene-test-util-expand-highlight-2170", "collection.xconf", $ueh:XCONF-2170),
        xmldb:reindex($ueh:COLL_2170),

        xmldb:create-collection("/db", "lucene-test-util-expand-highlight-2755-ordered"),
        xmldb:create-collection("/db/system/config/db", "lucene-test-util-expand-highlight-2755-ordered"),
        xmldb:store($ueh:COLL_2755_ORDERED, "test.xml", $ueh:XML-2755),
        xmldb:store("/db/system/config/db/lucene-test-util-expand-highlight-2755-ordered", "collection.xconf", $ueh:XCONF-2755-ORDERED),
        xmldb:reindex($ueh:COLL_2755_ORDERED),

        xmldb:create-collection("/db", "lucene-test-util-expand-highlight-2755-swapped"),
        xmldb:create-collection("/db/system/config/db", "lucene-test-util-expand-highlight-2755-swapped"),
        xmldb:store($ueh:COLL_2755_SWAPPED, "test.xml", $ueh:XML-2755),
        xmldb:store("/db/system/config/db/lucene-test-util-expand-highlight-2755-swapped", "collection.xconf", $ueh:XCONF-2755-SWAPPED),
        xmldb:reindex($ueh:COLL_2755_SWAPPED)
    )
};

declare
    %test:tearDown
function ueh:tearDown() {
    (
        xmldb:remove($ueh:COLL_4835),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-4835"),
        xmldb:remove($ueh:COLL_4584),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-4584"),
        xmldb:remove($ueh:COLL_2170),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-2170"),
        xmldb:remove($ueh:COLL_2755_ORDERED),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-2755-ordered"),
        xmldb:remove($ueh:COLL_2755_SWAPPED),
        xmldb:remove("/db/system/config/db/lucene-test-util-expand-highlight-2755-swapped")
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
        collection($ueh:COLL_4584)//quote[ft:query(., $word)]/util:expand(.)//exist:match/normalize-space(),
        ""
    )
};

(: Hit count: "joy" should be found once. :)
declare
    %test:assertEquals(1)
function ueh:issue4584-find-joy-count() {
    count(collection($ueh:COLL_4584)//quote[ft:query(., "joy")])
};

(: Hit count: "rose" should be found once. :)
declare
    %test:assertEquals(1)
function ueh:issue4584-find-rose-count() {
    count(collection($ueh:COLL_4584)//quote[ft:query(., "rose")])
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

(: #2170 tests ---------------------------------------------------------- :)

declare %private function ueh:issue2170-doc() as document-node() {
    doc($ueh:COLL_2170 || "/test.xml")
};

declare %private function ueh:issue2170-query1-expanded() as element(test) {
    util:expand(ueh:issue2170-doc()//p[ft:query(., "sleep")]/ancestor::test)
};

declare %private function ueh:issue2170-query2-expanded() as element(test) {
    util:expand(ueh:issue2170-doc()//test[.//p[ft:query(., "sleep")]])
};

(:~
 : #2170: util:expand on ancestor selection should wrap exactly 6 matches.
 : @see https://github.com/eXist-db/exist/issues/2170
 :)
declare
    %test:assertEquals(6)
function ueh:issue2170-query1-match-count() {
    count(ueh:issue2170-query1-expanded()//exist:match)
};

(:~
 : #2170: util:expand on direct test selection should wrap exactly 6 matches.
 : @see https://github.com/eXist-db/exist/issues/2170
 :)
declare
    %test:assertEquals(6)
function ueh:issue2170-query2-match-count() {
    count(ueh:issue2170-query2-expanded()//exist:match)
};

(:~
 : #2170: ensure the second paragraph is not duplicated by util:expand(query1 shape).
 : @see https://github.com/eXist-db/exist/issues/2170
 :)
declare
    %test:assertEquals(0)
function ueh:issue2170-query1-no-duplicate-fragment() {
    count(ueh:issue2170-query1-expanded()//p[contains(., "sleepsleep")])
};

(:~
 : #2170: ensure the second paragraph is not duplicated by util:expand(query2 shape).
 : @see https://github.com/eXist-db/exist/issues/2170
 :)
declare
    %test:assertEquals(0)
function ueh:issue2170-query2-no-duplicate-fragment() {
    count(ueh:issue2170-query2-expanded()//p[contains(., "sleepsleep")])
};

(: #2755 tests ---------------------------------------------------------- :)

declare %private function ueh:issue2755-match-count($collection as xs:string) as xs:integer {
    let $hits :=
        collection($collection)//phrase[ft:query-field("phrase-ws", "of")]
    let $expanded := util:expand($hits)
    return count($expanded//exist:match)
};

(:~
 : #2755: util:expand should preserve exist:match for analyzer order from issue report.
 : @see https://github.com/eXist-db/exist/issues/2755
 :)
declare
    %test:assertEquals(1)
function ueh:issue2755-expand-has-match-ordered() {
    ueh:issue2755-match-count($ueh:COLL_2755_ORDERED)
};

(:~
 : #2755 control: swapping analyzer field declaration order should still preserve exist:match.
 : @see https://github.com/eXist-db/exist/issues/2755
 :)
declare
    %test:assertEquals(1)
function ueh:issue2755-expand-has-match-swapped() {
    ueh:issue2755-match-count($ueh:COLL_2755_SWAPPED)
};

