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
 : XQSuite tests for behaviour of ft:query() on context nodes in a parenthesized location step.
 : Refactored from parenthesizedLocationStep_ftquery_Tests.xml (TestSet).
 :
 : Three sections: [element] ft:query on parenthesized context selecting an element; [attribute]
 : ft:query on parenthesized context selecting an attribute; [attribute retrieval] retrieval of
 : parenthesized context selecting an attribute.
 :
 : The degree of the problems depends on the context node type: elements — queries fail when the
 : parenthesized context node is not immediately preceded by a non-parenthesized location step
 : (unless the preceding step is self::*); attributes — queries always fail with parenthesized
 : context nodes, with the problem appearing at retrieval level. Note: parenthesized context
 : selecting a non-existent attribute whose name matches an element name can cause erroneous
 : bleed-through results; non-parenthesized context does not.
 :
 : @author Ron Van den Branden
 :)
module namespace plst="http://exist-db.org/xquery/lucene/parenthesized-location-step/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: @att1, @att2, el1, el2.
 :)
declare variable $plst:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="@att1"/>
                <text qname="@att2"/>
                <text qname="el1"/>
                <text qname="el2"/>
            </lucene>
        </index>
    </collection>;

(:~
 : Test document: two level1/level2 with p, el1, el2.
 :)
declare variable $plst:XML as document-node() :=
    document {
        <test>
            <level1>
                <level2>
                    <p>this is text with <el1 att1="test">test</el1> strings in <el2 att2="test">test</el2> elements</p>
                </level2>
            </level1>
            <level1>
                <level2>
                    <p>this is text with <el1 att1="test">test</el1> strings in <el2 att="test">test</el2> elements</p>
                </level2>
            </level1>
        </test>
    };

declare variable $plst:COLLECTION_NAME := "lucene-test-parenthesized-location-step";
declare variable $plst:COLLECTION := "/db/" || $plst:COLLECTION_NAME;

(:~
 : Expected: two el1 elements.
 :)
declare variable $plst:EXPECTED_EL1 as element(el1)+ := (
    <el1 att1="test">test</el1>,
    <el1 att1="test">test</el1>
);

(:~
 : Expected: two result elements with att1.
 :)
declare variable $plst:EXPECTED_ATTR_RESULT as element(result)+ := (
    <result att1="test"/>,
    <result att1="test"/>
);

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function plst:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $plst:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $plst:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $plst:COLLECTION_NAME, "collection.xconf", $plst:XCONF),
      xmldb:store($plst:COLLECTION, "test.xml", $plst:XML),
      xmldb:reindex($plst:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function plst:tearDown() {
    xmldb:remove($plst:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $plst:COLLECTION_NAME)
};

(:~
 : [element] parenthesized final location step, non-parenthesized preceding step.
 :)
declare
    %test:assertTrue
function plst:element-parenthesized-preceding-non-paren() {
    let $result := collection($plst:COLLECTION)//level1//(el1)[ft:query(., 'test')]
    return deep-equal($result, $plst:EXPECTED_EL1)
};

(:~
 : [element] parenthesized final location step, no preceding step.
 :)
declare
    %test:assertTrue
function plst:element-parenthesized-no-preceding() {
    let $result := collection($plst:COLLECTION)//(el1)[ft:query(., 'test')]
    return deep-equal($result, $plst:EXPECTED_EL1)
};

(:~
 : [element] parenthesized final location step, parenthesized preceding step.
 :)
declare
    %test:assertTrue
function plst:element-parenthesized-preceding-paren() {
    let $result := collection($plst:COLLECTION)//(level1)//(el1)[ft:query(., 'test')]
    return deep-equal($result, $plst:EXPECTED_EL1)
};

(:~
 : [element] parenthesized final location step, non-parenthesized preceding step with self selector.
 :)
declare
    %test:assertTrue
function plst:element-parenthesized-preceding-self() {
    let $result := collection($plst:COLLECTION)//level1//.//(el1)[ft:query(., 'test')]
    return deep-equal($result, $plst:EXPECTED_EL1)
};

(:~
 : [attribute] parenthesized final location step, non-parenthesized preceding step.
 :)
declare
    %test:pending("previously ignored; parenthesized attribute context")
    %test:assertTrue
function plst:attribute-parenthesized-preceding-non-paren() {
    let $result := for $a in collection($plst:COLLECTION)//level1//(@att1)[ft:query(., 'test')] return <result>{ $a }</result>
    return deep-equal($result, $plst:EXPECTED_ATTR_RESULT)
};

(:~
 : [attribute] parenthesized final location step, no preceding step.
 :)
declare
    %test:pending("previously ignored; parenthesized attribute context")
    %test:assertTrue
function plst:attribute-parenthesized-no-preceding() {
    let $result := for $a in collection($plst:COLLECTION)//(@att1)[ft:query(., 'test')] return <result>{ $a }</result>
    return deep-equal($result, $plst:EXPECTED_ATTR_RESULT)
};

(:~
 : [attribute] parenthesized final location step, parenthesized preceding step.
 :)
declare
    %test:pending("previously ignored; parenthesized attribute context")
    %test:assertTrue
function plst:attribute-parenthesized-preceding-paren() {
    let $result := for $a in collection($plst:COLLECTION)//(level1)//(@att1)[ft:query(., 'test')] return <result>{ $a }</result>
    return deep-equal($result, $plst:EXPECTED_ATTR_RESULT)
};

(:~
 : [attribute] parenthesized final location step, non-parenthesized preceding step with self selector.
 :)
declare
    %test:pending("previously ignored; parenthesized attribute context")
    %test:assertTrue
function plst:attribute-parenthesized-preceding-self() {
    let $result := for $a in collection($plst:COLLECTION)//level1//.//(@att1)[ft:query(., 'test')] return <result>{ $a }</result>
    return deep-equal($result, $plst:EXPECTED_ATTR_RESULT)
};

(:~
 : [attribute retrieval] parenthesized final location step.
 :)
declare
    %test:pending("previously ignored; attribute retrieval parenthesized")
    %test:assertTrue
function plst:attribute-retrieval-parenthesized() {
    let $result := for $a in collection($plst:COLLECTION)//level1//(@att1) return <result>{ $a }</result>
    return deep-equal($result, $plst:EXPECTED_ATTR_RESULT)
};

(:~
 : [attribute retrieval] non-parenthesized final location step.
 :)
declare
    %test:assertTrue
function plst:attribute-retrieval-non-parenthesized() {
    let $result := for $a in collection($plst:COLLECTION)//level1//@att1 return <result>{ $a }</result>
    return deep-equal($result, $plst:EXPECTED_ATTR_RESULT)
};

(:~
 : [attribute retrieval] parenthesized attribute context causes bleed-through when name equals element name.
 :)
declare
    %test:pending("previously ignored; bleed-through behaviour")
    %test:assertEmpty
function plst:attribute-retrieval-parenthesized-bleed-through() {
    for $a in collection($plst:COLLECTION)//level1//(@el1) return <result>{ $a }</result>
};

(:~
 : [attribute retrieval] non-parenthesized attribute context behaves correctly.
 :)
declare
    %test:assertEmpty
function plst:attribute-retrieval-non-paren-correct() {
    for $a in collection($plst:COLLECTION)//level1//@el1 return <result>{ $a }</result>
};
