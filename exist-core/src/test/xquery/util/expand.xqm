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
xquery version "3.0";

module namespace ue = "http://exist-db.org/xquery/test/util/expand";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace util = "http://exist-db.org/xquery/util";

declare namespace x = "x";

declare variable $ue:test-collection-name := "expand-test";
declare variable $ue:test-collection-uri := "/db/" || $ue:test-collection-name;

declare variable $ue:SIMPLE_ROOT := document {
 <simple>simple</simple>
};
declare variable $ue:COMMENTS_BEFORE_ROOT := document {
  <!-- comment 1 before -->,
  <!-- comment 2 before -->,
 <comments-before>comments-before</comments-before>
};
declare variable $ue:PI_BEFORE_ROOT := document {
  processing-instruction foo { "" },
  <pi-before>pi-before</pi-before>
};
declare variable $ue:ROOT_WITH_ATTRIBUTE := document {
  <with-attr foo="bar">with-attr</with-attr>
};
declare variable $ue:ROOT_WITH_NS := document {
  <with-ns xmlns:x="x" x:foo="bar">with-ns</with-ns>
};


declare
    %test:setUp
function ue:setup () {
    xmldb:create-collection('/db', $ue:test-collection-name),
    xmldb:store($ue:test-collection-uri, "simple.xml", $ue:SIMPLE_ROOT),
    xmldb:store($ue:test-collection-uri, "comments.xml", $ue:COMMENTS_BEFORE_ROOT),
    xmldb:store($ue:test-collection-uri, "pi.xml", $ue:PI_BEFORE_ROOT),
    xmldb:store($ue:test-collection-uri, "with-attribute.xml", $ue:ROOT_WITH_ATTRIBUTE),
    xmldb:store($ue:test-collection-uri, "with-ns.xml", $ue:ROOT_WITH_NS)
};

declare
    %test:tearDown
function ue:cleanup () {
    xmldb:remove($ue:test-collection-uri)
};

declare
    %test:assertEquals("1", "true")
function ue:expand-persistent-dom() as item()+ {
   let $nodes := doc($ue:test-collection-uri || '/simple.xml')/node()
   let $expanded-nodes := util:expand($nodes)
   return (
       count($expanded-nodes),
       $expanded-nodes instance of element(simple)
   )
};

declare
    %test:assertEquals("3", "true")
function ue:expand-persistent-dom-comments-before() as item()+ {
   let $nodes := doc($ue:test-collection-uri || '/comments.xml')/node()
   let $expanded-nodes := util:expand($nodes)
   return (
       count($expanded-nodes),
       $expanded-nodes[fn:last()] instance of element(comments-before)
   )
};

declare
    %test:assertEquals("2", "true")
function ue:expand-persistent-dom-pi-before() as item()+ {
   let $nodes := doc($ue:test-collection-uri || '/pi.xml')/node()
   let $expanded-nodes := util:expand($nodes)
   return (
       count($expanded-nodes),
       $expanded-nodes[fn:last()] instance of element(pi-before)
   )
};


declare
    %test:assertEquals("true", "true", "bar")
function ue:expand-persistent-dom-with-attributes() as item()+ {
   let $nodes := doc($ue:test-collection-uri || '/with-attribute.xml')/with-attr/@foo
   let $content := util:expand($nodes)
   return (
       $content instance of attribute(),
       node-name($content) eq xs:QName("foo"),
       string($content)
   )
};

declare
    %test:assertEquals("true", "true", "bar")
function ue:expand-persistent-dom-with-ns() as item()+ {
   let $nodes := doc($ue:test-collection-uri || '/with-ns.xml')/with-ns/@x:foo
   let $content := util:expand($nodes)
   return (
       $content instance of attribute(),
       node-name($content) eq xs:QName("x:foo"),
       string($content)
   )
};

(: -------- IN MEMORY TESTS ----------- :)

declare
    %test:assertEquals("true", "true", "bar")
function ue:attribute() as item()+ {
   let $attr := util:expand( attribute foo { "bar" } )
   return
       (
           $attr instance of attribute(),
           node-name($attr) eq xs:QName("foo"),
           string($attr)
       )
};

declare
    %test:assertEquals("true", "true", "bar")
function ue:attributeNs() as item()+ {
    let $attr := util:expand( attribute ue:foo { "bar" } )
    return
        (
            $attr instance of attribute(),
            node-name($attr) eq xs:QName("ue:foo"),
            string($attr)
        )
};

declare
    %test:assertTrue
function ue:comment() as xs:boolean {
    util:expand( comment { "foo" } ) instance of comment()
};

declare
    %test:assertTrue
function ue:document() as xs:boolean {
    util:expand( document { element foo {()} } ) instance of document-node()
};

declare
    %test:assertTrue
function ue:documentNs() as xs:boolean {
    util:expand( document { element ue:foo {()} } ) instance of document-node()
};

declare
    %test:assertEquals("true", "true")
function ue:element() as xs:boolean+ {
    let $elem := util:expand( element foo {()} )
    return
        (
            $elem instance of element(),
            node-name($elem) eq xs:QName("foo")
        )
};

declare
    %test:assertEquals("true", "true")
function ue:elementNs() as xs:boolean+ {
    let $elem := util:expand( element ue:foo {()} )
    return
        (
            $elem instance of element(),
            node-name($elem) eq xs:QName("ue:foo")
        )
};

declare
    %test:assertTrue
function ue:pi() as xs:boolean {
    util:expand( processing-instruction foo { "" } ) instance of processing-instruction()
};

declare
    %test:assertTrue
function ue:text() as xs:boolean {
    util:expand( text { "foo" } ) instance of text()
};