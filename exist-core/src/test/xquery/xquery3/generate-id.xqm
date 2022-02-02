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

module namespace gid = "http://exist-db.org/xquery/test/generate-id";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare
    %test:setUp
function gid:setup() {
  xmldb:create-collection("/db", "generate-id-test"),
  xmldb:store("/db/generate-id-test", "a.xml", <node>a</node>),
  xmldb:store("/db/generate-id-test", "b.xml", <node>b</node>),
  xmldb:store("/db/generate-id-test", "inner-a.xml", <node><inner>a</inner></node>),

  if ((("a.xml", "b.xml", "inner-a.xml") ! doc-available("/db/generate-id-test/" || .)) = false())
  then
    error(xs:QName("gid:missing-documents"), "Missing setup documents")
  else ()
};

declare
    %test:tearDown
function gid:cleanup() {
  xmldb:remove("/db/generate-id-test")
};


declare
    %test:assertFalse
function gid:parsed-in-memory-dom-identity() {
  let $node1 := parse-xml-fragment("<node>a</node>")/node(),
      $node2 := parse-xml-fragment("<node>b</node>")/node()
  return
    codepoint-equal(generate-id($node1), generate-id($node2))
};

declare
    %test:assertEquals("false", "true", "true")
function gid:in-memory-dom-element-identity() {
  let $node1 := <node>a</node>,
      $node2 := <node>b</node>
  return
  (
      codepoint-equal(generate-id($node1), generate-id($node2)),
      codepoint-equal(generate-id($node1), generate-id($node1)),
      codepoint-equal(generate-id($node2), generate-id($node2))
  )
};

declare
    %test:assertEquals("false", "true", "true")
function gid:in-memory-dom-document-identity() {
  let $node1 := document { <node>a</node> },
      $node2 := document { <node>b</node> }
  return
    (
        codepoint-equal(generate-id($node1), generate-id($node2)),
        codepoint-equal(generate-id($node1), generate-id($node1)),
        codepoint-equal(generate-id($node2), generate-id($node2))
    )
};

declare
    %test:assertEquals("false", "true", "true")
function gid:in-memory-dom-document-element-identity() {
  let $node1 := document { <node>a</node> }/element(),
      $node2 := document { <node>b</node> }/element()
  return
    (
        codepoint-equal(generate-id($node1), generate-id($node2)),
        codepoint-equal(generate-id($node1), generate-id($node1)),
        codepoint-equal(generate-id($node2), generate-id($node2))
    )
};

declare
    %test:assertEquals("true", "true")
function gid:in-memory-dom-element-identity-ascii-alphanum-only() {
  let $node1 := <node><inner>a</inner></node>,
      $id := generate-id($node1/inner)
  return
    (
        starts-with($id, "M"),
        matches($id, "[a-zA-Z0-9]+")
    )
};

declare
    %test:assertEquals("false", "true", "true")
function gid:persistent-dom-document-identity() {
  let $node1 := doc("/db/generate-id-test/a.xml"),
      $node2 := doc("/db/generate-id-test/b.xml")
  return
    (
        codepoint-equal(generate-id($node1), generate-id($node2)),
        codepoint-equal(generate-id($node1), generate-id($node1)),
        codepoint-equal(generate-id($node2), generate-id($node2))
    )
};

declare
    %test:assertEquals("false", "true", "true")
function gid:persistent-dom-document-element-identity() {
  let $node1 := doc("/db/generate-id-test/a.xml")/element(),
      $node2 := doc("/db/generate-id-test/b.xml")/element()
  return
    (
        codepoint-equal(generate-id($node1), generate-id($node2)),
        codepoint-equal(generate-id($node1), generate-id($node1)),
        codepoint-equal(generate-id($node2), generate-id($node2))
    )
};

declare
    %test:assertEquals("true", "true")
function gid:persistent-dom-element-identity-ascii-alphanum-only() {
  let $node1 := doc("/db/generate-id-test/inner-a.xml"),
      $id := generate-id($node1/node/inner)
  return
    (
        starts-with($id, "P"),
        matches($id, "[a-zA-Z0-9]+")
    )
};
