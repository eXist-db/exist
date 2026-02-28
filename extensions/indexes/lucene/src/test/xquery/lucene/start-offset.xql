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
 : XQSuite tests for correct offset calculation of util:expand (start offsets).
 : Refactored from startOffset.xml (TestSet).
 : Tests whether util:expand() calculates correct start offsets. Since there is a problem with
 : first matching strings of certain nodes, tests come in pairs: one matching the first word,
 : the other matching the second word of a node.
 :
 : @author Ron Van den Branden
 :)
module namespace stof="http://exist-db.org/xquery/lucene/start-offset/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: text qname el.
 :)
declare variable $stof:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text qname="el"/>
            </lucene>
        </index>
    </collection>;

declare variable $stof:COLLECTION_NAME := "lucene-test-start-offset";
declare variable $stof:COLLECTION := "/db/" || $stof:COLLECTION_NAME;

(:~
 : setUp: create collection, config, store five test docs, reindex.
 :)
declare
    %test:setUp
function stof:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $stof:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $stof:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $stof:COLLECTION_NAME, "collection.xconf", $stof:XCONF),
      xmldb:store($stof:COLLECTION, "test1.xml", document { <test><a><b>word</b></a><el>strong string</el></test> }),
      xmldb:store($stof:COLLECTION, "test2.xml", document { <test><el><a><b>word</b></a><c>strong string</c></el></test> }),
      xmldb:store($stof:COLLECTION, "test3.xml", document { <test><el><a>word</a><c>strong string</c></el></test> }),
      xmldb:store($stof:COLLECTION, "test4.xml", document { <test><el><a><b/></a><c>strong string</c></el></test> }),
      xmldb:store($stof:COLLECTION, "test5.xml", document { <test><el><a><b/><c>strong string</c></a></el></test> }),
      xmldb:reindex($stof:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function stof:tearDown() {
    xmldb:remove($stof:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $stof:COLLECTION_NAME)
};

(:~ Serialize with consistent options for assertEquals string comparison. :)
declare variable $stof:SER := map{"method":"xml","omit-xml-declaration":true(),"indent":false()};

(:~
 : atomic match preceded by complex element, first word.
 :)
declare
    %test:assertEquals('<el><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">strong</exist:match> string</el>')
function stof:atomic-complex-first-word() {
    serialize(doc($stof:COLLECTION || "/test1.xml")//el[ft:query(., 'strong')]/util:expand(.), $stof:SER)
};

(:~
 : atomic match preceded by complex element, second word.
 :)
declare
    %test:assertEquals('<el>strong <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">string</exist:match></el>')
function stof:atomic-complex-second-word() {
    serialize(doc($stof:COLLECTION || "/test1.xml")//el[ft:query(., 'string')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by simple element, first word.
 :)
declare
    %test:assertEquals('<el><a>word</a><c><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">strong</exist:match> string</c></el>')
function stof:nested-simple-first-word() {
    serialize(doc($stof:COLLECTION || "/test3.xml")//el[ft:query(., 'strong')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by simple element, second word.
 :)
declare
    %test:assertEquals('<el><a>word</a><c>strong <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">string</exist:match></c></el>')
function stof:nested-simple-second-word() {
    serialize(doc($stof:COLLECTION || "/test3.xml")//el[ft:query(., 'string')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by empty element, first word.
 :)
declare
    %test:assertEquals('<el><a><b/><c><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">strong</exist:match> string</c></a></el>')
function stof:nested-empty-first-word() {
    serialize(doc($stof:COLLECTION || "/test5.xml")//el[ft:query(., 'strong')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by empty element, second word.
 :)
declare
    %test:assertEquals('<el><a><b/><c>strong <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">string</exist:match></c></a></el>')
function stof:nested-empty-second-word() {
    serialize(doc($stof:COLLECTION || "/test5.xml")//el[ft:query(., 'string')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by complex element, first word.
 :)
declare
    %test:assertEquals('<el><a><b>word</b></a><c><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">strong</exist:match> string</c></el>')
function stof:nested-complex-first-word() {
    serialize(doc($stof:COLLECTION || "/test2.xml")//el[ft:query(., 'strong')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by complex element, second word.
 :)
declare
    %test:assertEquals('<el><a><b>word</b></a><c>strong <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">string</exist:match></c></el>')
function stof:nested-complex-second-word() {
    serialize(doc($stof:COLLECTION || "/test2.xml")//el[ft:query(., 'string')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by complex element (with empty child), first word.
 :)
declare
    %test:assertEquals('<el><a><b/></a><c><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">strong</exist:match> string</c></el>')
function stof:nested-complex-empty-child-first-word() {
    serialize(doc($stof:COLLECTION || "/test4.xml")//el[ft:query(., 'strong')]/util:expand(.), $stof:SER)
};

(:~
 : nested match preceded by complex element (with empty child), second word.
 :)
declare
    %test:assertEquals('<el><a><b/></a><c>strong <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">string</exist:match></c></el>')
function stof:nested-complex-empty-child-second-word() {
    serialize(doc($stof:COLLECTION || "/test4.xml")//el[ft:query(., 'string')]/util:expand(.), $stof:SER)
};
