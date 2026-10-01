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
 : update replace and update insert of the comments and processing instructions around the
 : document element of a stored document (https://github.com/eXist-db/exist/issues/628).
 :)
module namespace dlu="http://exist-db.org/xquery/test/document-level-updates";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $dlu:COLLECTION := "/db/test-document-level-updates";

declare variable $dlu:WITH-COMMENT := document {
    comment { " to be replaced " },
    <document><p>Contains stuff.</p></document>
};

declare variable $dlu:WITHOUT-PROLOG := document {
    <document><p>Contains stuff.</p></document>
};

declare
    %test:setUp
function dlu:setup() {
    xmldb:create-collection("/db", "test-document-level-updates")
};

declare
    %test:tearDown
function dlu:tearDown() {
    xmldb:remove($dlu:COLLECTION)
};

(:~ store a copy of a document under a name of its own, and return it :)
declare %private function dlu:store($name as xs:string, $doc as document-node()) as document-node() {
    doc(xmldb:store($dlu:COLLECTION, $name, $doc))
};

(:~ the document, serialized, and serialized again after it has been defragmented :)
declare %private function dlu:serialized($doc as document-node()) as xs:string+ {
    serialize($doc),
    (xmldb:defragment($doc), serialize($doc))
};

declare
    %test:assertEquals("<!--replaced--><document><p>Contains stuff.</p></document>", "<!--replaced--><document><p>Contains stuff.</p></document>")
function dlu:replace-comment() {
    let $doc := dlu:store("replace-comment.xml", $dlu:WITH-COMMENT)
    return (
        update replace $doc/comment() with comment { "replaced" },
        dlu:serialized($doc)
    )
};

declare
    %test:assertEquals("<?target data?><document><p>Contains stuff.</p></document>")
function dlu:replace-comment-with-pi() {
    let $doc := dlu:store("replace-comment-with-pi.xml", $dlu:WITH-COMMENT)
    return (
        update replace $doc/comment() with processing-instruction target { "data" },
        serialize($doc)
    )
};

declare
    %test:assertEquals("<!-- to be replaced --><!--new--><document><p>Contains stuff.</p></document>", "<!-- to be replaced --><!--new--><document><p>Contains stuff.</p></document>")
function dlu:insert-before-document-element() {
    let $doc := dlu:store("insert-before-root.xml", $dlu:WITH-COMMENT)
    return (
        update insert comment { "new" } preceding $doc/document,
        dlu:serialized($doc)
    )
};

declare
    %test:assertEquals("<!-- to be replaced --><document><p>Contains stuff.</p></document><!--new-->", "<!-- to be replaced --><document><p>Contains stuff.</p></document><!--new-->")
function dlu:insert-after-document-element() {
    let $doc := dlu:store("insert-after-root.xml", $dlu:WITH-COMMENT)
    return (
        update insert comment { "new" } following $doc/document,
        dlu:serialized($doc)
    )
};

declare
    %test:assertEquals("<!--new--><!-- to be replaced --><document><p>Contains stuff.</p></document>", "<!--new--><!-- to be replaced --><document><p>Contains stuff.</p></document>")
function dlu:insert-before-first-comment() {
    let $doc := dlu:store("insert-before-first.xml", $dlu:WITH-COMMENT)
    return (
        update insert comment { "new" } preceding $doc/comment(),
        dlu:serialized($doc)
    )
};

(:~ the document element is the first child: its record is moved after the new nodes :)
declare
    %test:assertEquals("<?a x?><!--c--><document><p>Contains stuff.</p></document>", "<?a x?><!--c--><document><p>Contains stuff.</p></document>", "Contains stuff.", "p")
function dlu:insert-before-document-element-without-prolog() {
    let $doc := dlu:store("insert-no-prolog.xml", $dlu:WITHOUT-PROLOG)
    return (
        update insert (processing-instruction a { "x" }, comment { "c" }) preceding $doc/document,
        dlu:serialized($doc),
        $doc/document/p/string(),
        local-name($doc/document/*)
    )
};

declare
    %test:assertEquals("error", "<document><p>Contains stuff.</p></document>")
function dlu:insert-element-is-rejected() {
    let $doc := dlu:store("insert-element.xml", $dlu:WITHOUT-PROLOG)
    return (
        try { update insert <other/> preceding $doc/document, "no error" } catch * { "error" },
        serialize($doc)
    )
};

declare
    %test:assertEquals("error", "<!-- to be replaced --><document><p>Contains stuff.</p></document>")
function dlu:replace-comment-with-element-is-rejected() {
    let $doc := dlu:store("replace-with-element.xml", $dlu:WITH-COMMENT)
    return (
        try { update replace $doc/comment() with <other/>, "no error" } catch * { "error" },
        serialize($doc)
    )
};

declare
    %test:assertEquals("<a><!--y--><b/></a>")
function dlu:replace-comment-in-an-element() {
    let $doc := dlu:store("replace-in-element.xml", document { <a>{ comment { " x " } }<b/></a> })
    return (
        update replace $doc/a/comment() with comment { "y" },
        serialize($doc)
    )
};
