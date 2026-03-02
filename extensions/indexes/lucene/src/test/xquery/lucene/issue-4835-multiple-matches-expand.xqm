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
 : XQSuite regression test for GitHub #4835: only last matched item in
 : parent element highlighted by util:expand. When multiple nested elements
 : match (e.g. div with 4 p children all containing "letter"), all should
 : get exist:match, not just the last.
 :
 : @see https://github.com/eXist-db/exist/issues/4835
 :)
module namespace i4835 = "http://exist-db.org/xquery/lucene/issue-4835/test";

declare namespace exist = "http://exist.sourceforge.net/NS/exist";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i4835:XML-multiple-nested := document {
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

declare variable $i4835:XML-two-in-one := document {
    <root>
        <p>Letter and letter</p>
    </root>
};

declare variable $i4835:xconf :=
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

declare variable $i4835:COLLECTION := "i4835-multiple-matches";
declare variable $i4835:COLL_PATH := "/db/" || $i4835:COLLECTION;

declare
    %test:setUp
function i4835:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4835:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4835:COLLECTION),
      xmldb:store($i4835:COLL_PATH, "multiple-nested.xml", $i4835:XML-multiple-nested),
      xmldb:store($i4835:COLL_PATH, "two-in-one.xml", $i4835:XML-two-in-one),
      xmldb:store("/db/system/config/db/" || $i4835:COLLECTION, "collection.xconf", $i4835:xconf),
      xmldb:reindex($i4835:COLL_PATH) )
};

declare
    %test:tearDown
function i4835:tearDown() {
    xmldb:remove($i4835:COLL_PATH),
    xmldb:remove("/db/system/config/db/" || $i4835:COLLECTION)
};

(: Parent div[ft:query(p,...)] – 3 divs contain matching p (incl. nested div). :)
declare
    %test:assertEquals(3)
function i4835:multiple-nested-parent-hits-count() {
    let $doc := doc($i4835:COLL_PATH || "/multiple-nested.xml")
    let $hits := $doc/root//div[ft:query(p, "letter")]
    return count($hits)
};

(: All 5 matching p elements should get exist:match. Fixed #4835. :)
declare
    %test:assertEquals(5)
function i4835:multiple-nested-parent-matches-count() {
    let $doc := doc($i4835:COLL_PATH || "/multiple-nested.xml")
    let $hits := $doc/root//div[ft:query(p, "letter")]
    let $result := util:expand($hits)
    return count($result//exist:match)
};

(: Direct p hits: all 5 should get exist:match. Fixed #4835. :)
declare
    %test:assertEquals(5)
function i4835:multiple-nested-direct-matches-count() {
    let $doc := doc($i4835:COLL_PATH || "/multiple-nested.xml")
    let $hits := $doc//p[ft:query(., "letter")]
    let $result := util:expand($hits)
    return count($result//exist:match)
};

(: Two matches in one p: both should get exist:match. :)
declare
    %test:assertEquals(2)
function i4835:two-in-one-matches-count() {
    let $doc := doc($i4835:COLL_PATH || "/two-in-one.xml")
    let $hits := $doc//p[ft:query(., "letter")]
    let $result := util:expand($hits)
    return count($result//exist:match)
};
