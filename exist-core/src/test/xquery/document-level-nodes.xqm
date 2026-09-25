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
 : Comments and processing instructions before and after the document element
 : of a stored document are nodes in their own right: reading them must not
 : return the document element, and updating them must not change it.
 :)
module namespace dln="http://exist-db.org/xquery/test/document-level-nodes";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $dln:COLLECTION := "/db/test-document-level-nodes";

declare variable $dln:XML := document {
    comment { " before " },
    processing-instruction target { "data" },
    <root><a/></root>,
    comment { " after " }
};

declare
    %test:setUp
function dln:setup() {
    xmldb:create-collection("/db", "test-document-level-nodes"),
    for $name in ("read.xml", "value.xml", "rename.xml", "delete-comment.xml", "delete-pi.xml", "delete-root.xml")
    return xmldb:store($dln:COLLECTION, $name, $dln:XML)
};

declare
    %test:tearDown
function dln:tearDown() {
    xmldb:remove($dln:COLLECTION)
};

declare %private function dln:doc($name as xs:string) as document-node() {
    doc($dln:COLLECTION || "/" || $name)
};

(:~ each child of the document, as kind and string value :)
declare %private function dln:children($doc as document-node()) as xs:string* {
    for $n in $doc/node()
    return
        typeswitch ($n)
            case comment() return "comment:" || $n
            case processing-instruction() return "pi:" || $n
            case element() return "element:" || name($n)
            default return "other"
};

declare
    %test:assertEquals("", "", "")
function dln:name-of-comment-db() {
    let $doc := dln:doc("read.xml")
    return (name($doc/comment()[1]), name($doc/comment()[2]), string(node-name($doc/comment()[1])))
};

declare
    %test:assertEquals("", "", "")
function dln:name-of-comment-mem() {
    (name($dln:XML/comment()[1]), name($dln:XML/comment()[2]), string(node-name($dln:XML/comment()[1])))
};

declare
    %test:assertEquals("comment: before ", "pi:data", "element:root", "comment: after ")
function dln:children-db() {
    dln:children(dln:doc("read.xml"))
};

declare
    %test:assertEquals("comment: before ", "pi:data", "element:root", "comment: after ")
function dln:children-mem() {
    dln:children($dln:XML)
};

(:~
 : "update value" does not support comments, here as inside an element; what
 : matters is that it no longer replaces the content of the document element
 :)
declare
    %test:assertEquals("refused", "comment: before ", "pi:data", "element:root", "comment: after ", "1")
function dln:update-value-of-comment() {
    let $doc := dln:doc("value.xml")
    let $result :=
        try {
            update value $doc/comment()[1] with "changed", "updated"
        } catch * {
            "refused"
        }
    return ($result, dln:children($doc), string(count($doc/root/a)))
};

declare
    %test:assertEquals("comment: before ", "pi:data", "element:top", "comment: after ", "1")
function dln:update-rename-of-document-element() {
    let $doc := dln:doc("rename.xml")
    let $_ := update rename $doc/root as "top"
    return (dln:children($doc), string(count($doc/top/a)))
};

declare
    %test:assertEquals("pi:data", "element:root", "comment: after ")
function dln:update-delete-of-comment() {
    let $doc := dln:doc("delete-comment.xml")
    let $_ := update delete $doc/comment()[1]
    return dln:children($doc)
};

declare
    %test:assertEquals("comment: before ", "element:root", "comment: after ")
function dln:update-delete-of-processing-instruction() {
    let $doc := dln:doc("delete-pi.xml")
    let $_ := update delete $doc/processing-instruction()
    return dln:children($doc)
};

declare
    %test:assertError
function dln:update-delete-of-document-element() {
    update delete dln:doc("delete-root.xml")/root
};
