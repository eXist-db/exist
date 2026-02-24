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
 : XQSuite tests for behaviour of retrieval, query and index functions on different index types,
 : on entirely parenthesised contexts.
 : Refactored from parenthesizedContext_ftquery_Tests.xml (TestSet).
 :
 : Three major sections: [retrieval] bare retrieval of parenthesized nodes; [query] queries on
 : different index types; [index] lookup of index terms on different index types.
 :
 : The degree of the problems depends on the type of index and search context. Influencing factors:
 : index definition (qname / path-based); type of query (direct XPath / indirect FLWR); context
 : node (parenthesized context node / parenthesized location step + self axis); type of node
 : (element / attribute); location step (parenthesized attribute in child step / in descendant step).
 :
 : @author Ron Van den Branden
 :)
module namespace pctx="http://exist-db.org/xquery/lucene/parenthesized-context/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: qname, @att.qname, path, @att.path Lucene + range.
 :)
declare variable $pctx:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text qname="qname"/>
                <text qname="@att.qname"/>
                <text match="//path"/>
                <text match="//@att.path"/>
            </lucene>
            <create qname="qname" type="xs:string"/>
            <create qname="@att.qname" type="xs:string"/>
            <create path="//path" type="xs:string"/>
            <create path="//@att.path" type="xs:string"/>
        </index>
    </collection>;

(:~
 : Test document.
 :)
declare variable $pctx:XML as document-node() :=
    document {
        <test>
            <qname att.qname="test">this is a test document</qname>
            <path att.path="test">this is a test document</path>
        </test>
    };

declare variable $pctx:COLLECTION_NAME := "lucene-test-parenthesized-context";
declare variable $pctx:COLLECTION := "/db/" || $pctx:COLLECTION_NAME;

(:~
 : Expected qname element.
 :)
declare variable $pctx:EXPECTED_QNAME as element(qname) := <qname att.qname="test">this is a test document</qname>;
(:~
 : Expected path element.
 :)
declare variable $pctx:EXPECTED_PATH as element(path) := <path att.path="test">this is a test document</path>;
(:~
 : Expected index-keys terms (element node).
 :)
declare variable $pctx:EXPECTED_TERMS_ELEMENT as element(term)+ := (<term freq="1" docs="1" n="1">document</term>, <term freq="1" docs="1" n="2">test</term>);
(:~
 : Expected index-keys term (attribute).
 :)
declare variable $pctx:EXPECTED_TERM_ATTR as element(term) := <term freq="1" docs="1" n="1">test</term>;

(:~
 : Callback for util:index-keys.
 : @param $term term
 : @param $data (freq, docs, n)
 :)
declare
    %private
function pctx:term-callback($term as xs:string, $data as xs:int+) as element(term) {
    <term freq="{$data[1]}" docs="{$data[2]}" n="{$data[3]}">{ $term }</term>
};

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function pctx:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $pctx:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $pctx:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $pctx:COLLECTION_NAME, "collection.xconf", $pctx:XCONF),
      xmldb:store($pctx:COLLECTION, "test.xml", $pctx:XML),
      xmldb:reindex($pctx:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function pctx:tearDown() {
    xmldb:remove($pctx:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $pctx:COLLECTION_NAME)
};

(:~
 : [query] fully parenthesized element node.
 :)
declare
    %test:assertTrue
function pctx:query-qname-direct-fully-paren() {
    deep-equal((collection($pctx:COLLECTION)//test/qname)[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-qname-indirect-fully-paren() {
    let $a := (collection($pctx:COLLECTION)//test/qname) return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-path-direct-fully-paren() {
    deep-equal((collection($pctx:COLLECTION)//test/path)[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};
declare
    %test:assertTrue
function pctx:query-path-indirect-fully-paren() {
    let $a := (collection($pctx:COLLECTION)//test/path) return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};

(:~
 : [query] fully parenthesized element node + self axis.
 :)
declare
    %test:assertTrue
function pctx:query-qname-direct-self() {
    deep-equal((collection($pctx:COLLECTION)//test/qname/self::*)[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-qname-indirect-self() {
    let $a := (collection($pctx:COLLECTION)//test/qname/self::*) return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-path-direct-self() {
    deep-equal((collection($pctx:COLLECTION)//test/path/self::*)[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};
declare
    %test:assertTrue
function pctx:query-path-indirect-self() {
    let $a := (collection($pctx:COLLECTION)//test/path/self::*) return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};

(:~
 : [query] child/descendant step with fully parenthesized attribute node.
 :)
declare
    %test:assertEquals("test")
function pctx:query-qname-direct-child-attr() {
    (collection($pctx:COLLECTION)//test/qname/@att.qname)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-qname-indirect-child-attr() {
    let $a := (collection($pctx:COLLECTION)//test/qname/@att.qname) return $a[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-qname-direct-desc-attr() {
    (collection($pctx:COLLECTION)//test/qname//@att.qname)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-qname-indirect-desc-attr() {
    let $a := (collection($pctx:COLLECTION)//test/qname//@att.qname) return $a[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-direct-child-attr() {
    (collection($pctx:COLLECTION)//test/path/@att.path)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-indirect-child-attr() {
    let $a := (collection($pctx:COLLECTION)//test/path/@att.path) return $a[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-direct-desc-attr() {
    (collection($pctx:COLLECTION)//test/path//@att.path)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-indirect-desc-attr() {
    let $a := (collection($pctx:COLLECTION)//test/path//@att.path) return $a[ft:query(., 'test')]/string()
};

(:~
 : util:index-keys with parenthesized qname context (collection()//test/(qname)).
 : Parentheses affect how the optimizer derives index hints; index lookup must handle them.
 :)
declare
    %test:assertTrue
function pctx:index-qname-fully-paren-has-terms() {
    let $a := (collection($pctx:COLLECTION)//test/qname),
        $result := util:index-keys($a, '', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index')
    return count($result) eq count($pctx:EXPECTED_TERMS_ELEMENT)
};

(:~
 : [index] fully parenthesized element node.
 :)
declare
    %test:assertTrue
function pctx:index-qname-fully-paren() {
    let $a := (collection($pctx:COLLECTION)//test/qname) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
(:~
 : util:index-keys with fully parenthesized path context. (collection()//test/path)
 : wraps the entire step; scan must resolve path-based index from this context.
 :)
declare
    %test:assertTrue
function pctx:index-path-fully-paren-has-terms() {
    let $a := (collection($pctx:COLLECTION)//test/path),
        $result := util:index-keys($a, '', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index')
    return count($result) eq count($pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-path-fully-paren() {
    let $a := (collection($pctx:COLLECTION)//test/path) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-qname-fully-paren-self() {
    let $a := (collection($pctx:COLLECTION)//test/qname/self::*) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-path-fully-paren-self() {
    let $a := (collection($pctx:COLLECTION)//test/path/self::*) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-qname-child-fully-paren-attr() {
    let $a := (collection($pctx:COLLECTION)//test/qname/@att.qname) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
declare
    %test:assertTrue
function pctx:index-qname-desc-fully-paren-attr() {
    let $a := (collection($pctx:COLLECTION)//test/qname//@att.qname) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
declare
    %test:assertTrue
function pctx:index-path-child-fully-paren-attr() {
    let $a := (collection($pctx:COLLECTION)//test/path/@att.path) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
declare
    %test:assertTrue
function pctx:index-path-desc-fully-paren-attr() {
    let $a := (collection($pctx:COLLECTION)//test/path//@att.path) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};

(:~
 : [query] parenthesized element node.
 :)
declare
    %test:assertTrue
function pctx:query-qname-direct-paren() {
    deep-equal(collection($pctx:COLLECTION)//test/(qname)[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-qname-indirect-paren() {
    let $a := collection($pctx:COLLECTION)//test/(qname) return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-path-direct-paren() {
    deep-equal(collection($pctx:COLLECTION)//test/(path)[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};
declare
    %test:assertTrue
function pctx:query-path-indirect-paren() {
    let $a := collection($pctx:COLLECTION)//test/(path) return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};

(:~
 : [query] parenthesized element node + self axis.
 :)
declare
    %test:assertTrue
function pctx:query-qname-direct-paren-self() {
    deep-equal(collection($pctx:COLLECTION)//test/(qname)/self::*[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-qname-indirect-paren-self() {
    let $a := collection($pctx:COLLECTION)//test/(qname)/self::* return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_QNAME)
};
declare
    %test:assertTrue
function pctx:query-path-direct-paren-self() {
    deep-equal(collection($pctx:COLLECTION)//test/(path)/self::*[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};
declare
    %test:assertTrue
function pctx:query-path-indirect-paren-self() {
    let $a := collection($pctx:COLLECTION)//test/(path)/self::* return deep-equal($a[ft:query(., 'test')], $pctx:EXPECTED_PATH)
};

(:~
 : [query] child/descendant step with parenthesized attribute node.
 :)
declare
    %test:assertEquals("test")
function pctx:query-qname-direct-child-paren-attr() {
    collection($pctx:COLLECTION)//test/qname/(@att.qname)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-qname-indirect-child-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/qname/(@att.qname) return $a[ft:query(., 'test')]/string()
};
declare
    %test:pending("previously ignored")
    %test:assertEquals("test")
function pctx:query-qname-direct-desc-paren-attr() {
    collection($pctx:COLLECTION)//test/qname//(@att.qname)[ft:query(., 'test')]/string()
};
declare
    %test:pending("previously ignored")
    %test:assertEquals("test")
function pctx:query-qname-indirect-desc-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/qname//(@att.qname) return $a[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-direct-child-paren-attr() {
    collection($pctx:COLLECTION)//test/path/(@att.path)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-indirect-child-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/path/(@att.path) return $a[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-direct-desc-paren-attr() {
    collection($pctx:COLLECTION)//test/path/(@att.path)[ft:query(., 'test')]/string()
};
declare
    %test:assertEquals("test")
function pctx:query-path-indirect-desc-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/path/(@att.path) return $a[ft:query(., 'test')]/string()
};

(:~
 : util:index-keys with (path) in location step. Context (collection()//test/(path))
 : differs from //test/path; index lookup must resolve parenthesized step.
 :)
declare
    %test:assertTrue
function pctx:index-path-paren-has-terms() {
    let $a := collection($pctx:COLLECTION)//test/(path),
        $result := util:index-keys($a, '', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index')
    return count($result) eq count($pctx:EXPECTED_TERMS_ELEMENT)
};

(:~
 : [index] parenthesized element node.
 :)
declare
    %test:assertTrue
function pctx:index-qname-paren() {
    let $a := collection($pctx:COLLECTION)//test/(qname) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-path-paren() {
    let $a := collection($pctx:COLLECTION)//test/(path) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-qname-paren-self() {
    let $a := collection($pctx:COLLECTION)//test/(qname)/self::* return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-path-paren-self() {
    let $a := collection($pctx:COLLECTION)//test/(path)/self::* return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERMS_ELEMENT)
};
declare
    %test:assertTrue
function pctx:index-qname-child-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/qname/(@att.qname) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
declare
    %test:pending("previously ignored")
    %test:assertTrue
function pctx:index-qname-desc-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/qname//(@att.qname) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
declare
    %test:assertTrue
function pctx:index-path-child-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/path/(@att.path) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
declare
    %test:pending("previously ignored")
    %test:assertTrue
function pctx:index-path-desc-paren-attr() {
    let $a := collection($pctx:COLLECTION)//test/path//(@att.path) return deep-equal(util:index-keys($a,'', util:function(xs:QName('pctx:term-callback'), 2), 100, 'lucene-index'), $pctx:EXPECTED_TERM_ATTR)
};
