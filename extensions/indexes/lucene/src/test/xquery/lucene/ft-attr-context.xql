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
 : XQSuite tests for queries on different indexes on different collection contexts.
 : Attribute context ft:query and util:index-keys (qname/path, @att vs attribute axis).
 : Refactored from FT_AttTest_complex.xml (TestSet).
 :
 : @author Ron Van den Branden
 :)
module namespace ftac="http://exist-db.org/xquery/lucene/ft-attr-context/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: p, @att1, //@att2 Lucene + range.
 :)
declare variable $ftac:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text qname="p"/>
                <text qname="@att1"/>
                <text match="//@att2"/>
            </lucene>
            <create qname="p" type="xs:string"/>
            <create qname="@att1" type="xs:string"/>
            <create path="//@att2" type="xs:string"/>
        </index>
    </collection>;

(:~
 : Test document: p with att1, att2.
 :)
declare variable $ftac:XML as document-node() :=
    document {
        <p att1="val1" att2="val2">this is a test document</p>
    };

declare variable $ftac:COLLECTION_NAME := "lucene-test-ft-attr-context";
declare variable $ftac:COLLECTION := "/db/" || $ftac:COLLECTION_NAME;

(:~ Callback for util:index-keys. :)
declare %private function ftac:term-callback($term as xs:string, $data as xs:int+) as element(term) {
    <term>{ $term }</term>
};

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function ftac:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $ftac:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $ftac:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $ftac:COLLECTION_NAME, "collection.xconf", $ftac:XCONF),
      xmldb:store($ftac:COLLECTION, "test.xml", $ftac:XML),
      xmldb:reindex($ftac:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function ftac:tearDown() {
    xmldb:remove($ftac:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ftac:COLLECTION_NAME)
};

(:~
 : [query] lucene FT index (qname), attribute context @att
 :)
declare %test:assertEquals("val1") function ftac:query-qname-attr-at() {
    collection($ftac:COLLECTION)//p/@att1[ft:query(., 'val1')]/string()
};

(:~
 : [query] lucene FT index (path), attribute context @att
 :)
declare %test:assertEquals("val2") function ftac:query-path-attr-at() {
    collection($ftac:COLLECTION)//p/@att2[ft:query(., 'val2')]/string()
};

(:~
 : [query] lucene FT index (qname), attribute context attribute axis
 :)
declare %test:assertEquals("val1") function ftac:query-qname-attr-attribute-axis() {
    collection($ftac:COLLECTION)//p/attribute::att1[ft:query(., 'val1')]/string()
};

(:~
 : [query] lucene FT index (path), attribute context attribute axis
 :)
declare %test:assertEquals("val2") function ftac:query-path-attr-attribute-axis() {
    collection($ftac:COLLECTION)//p/attribute::att2[ft:query(., 'val2')]/string()
};

(:~
 : [query] lucene FT index (qname), element context
 :)
declare %test:assertTrue function ftac:query-qname-element-context() {
    let $result := collection($ftac:COLLECTION)//p[ft:query(@att1, 'val1')]
    return deep-equal($result, collection($ftac:COLLECTION)//p)
};

(:~
 : [query] lucene FT index (path), element context
 :)
declare %test:assertTrue function ftac:query-path-element-context() {
    let $result := collection($ftac:COLLECTION)//p[ft:query(@att2, 'val2')]
    return deep-equal($result, collection($ftac:COLLECTION)//p)
};

(:~
 : [query] lucene FT index (qname), attribute context inside predicate
 :)
declare %test:assertTrue function ftac:query-qname-attr-in-predicate() {
    let $result := collection($ftac:COLLECTION)/*[descendant-or-self::p/@att1[ft:query(., 'val1')]]
    return deep-equal($result, collection($ftac:COLLECTION)//p)
};

(:~
 : [query] lucene FT index (path), attribute context inside predicate
 :)
declare %test:assertTrue function ftac:query-path-attr-in-predicate() {
    let $result := collection($ftac:COLLECTION)/*[descendant-or-self::p/@att2[ft:query(., 'val2')]]
    return deep-equal($result, collection($ftac:COLLECTION)//p)
};

(:~
 : [index] lucene FT index (qname), attribute context @att
 :)
declare %test:assertTrue function ftac:index-qname-attr-at() {
    let $a := collection($ftac:COLLECTION)//p/@att1
    let $result := util:index-keys($a, '', util:function(xs:QName('ftac:term-callback'), 2), 100, 'lucene-index')
    return deep-equal($result, <term>val1</term>)
};

(:~
 : [index] lucene FT index (path), attribute context @att
 :)
declare %test:assertTrue function ftac:index-path-attr-at() {
    let $a := collection($ftac:COLLECTION)//p/@att2
    let $result := util:index-keys($a, '', util:function(xs:QName('ftac:term-callback'), 2), 100, 'lucene-index')
    return deep-equal($result, <term>val2</term>)
};

(:~
 : [index] lucene FT index (qname), attribute context attribute axis
 :)
declare %test:assertTrue function ftac:index-qname-attr-attribute-axis() {
    let $a := collection($ftac:COLLECTION)//p/attribute::att1
    let $result := util:index-keys($a, '', util:function(xs:QName('ftac:term-callback'), 2), 100, 'lucene-index')
    return deep-equal($result, <term>val1</term>)
};

(:~
 : [index] lucene FT index (path), attribute context attribute axis
 :)
declare %test:assertTrue function ftac:index-path-attr-attribute-axis() {
    let $a := collection($ftac:COLLECTION)//p/attribute::att2
    let $result := util:index-keys($a, '', util:function(xs:QName('ftac:term-callback'), 2), 100, 'lucene-index')
    return deep-equal($result, <term>val2</term>)
};

(:~
 : [index] lucene FT index (qname), element context
 :)
declare %test:assertTrue function ftac:index-qname-element-context() {
    let $a := collection($ftac:COLLECTION)//p
    let $result := util:index-keys($a/@att1, '', util:function(xs:QName('ftac:term-callback'), 2), 100, 'lucene-index')
    return deep-equal($result, <term>val1</term>)
};

(:~
 : [index] lucene FT index (path), element context
 :)
declare %test:assertTrue function ftac:index-path-element-context() {
    let $a := collection($ftac:COLLECTION)//p
    let $result := util:index-keys($a/@att2, '', util:function(xs:QName('ftac:term-callback'), 2), 100, 'lucene-index')
    return deep-equal($result, <term>val2</term>)
};
