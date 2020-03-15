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

(:~ Additional tests for the fn:count function :)
module namespace comments="http://exist-db.org/xquery/test/comments";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare 
    %test:assertEquals("<r><a>1</a>(:comment:)<b>2</b>(:<c>3</c>:)<d>4</d></r>")
function comments:comments-in-xml() {
    (:for eXist 2.2:)
    <r><a>1</a>(:comment:)<b>2</b>(:<c>3</c>:)<d>4</d></r>
};

declare 
    %test:assertEquals('<r><a b="a (: comment :)">1</a></r>')
function comments:ignore-comment-in-attrib() {
    <r><a b="a (: comment :)">1</a></r>
};

declare 
    %test:assertEquals('<r><a b="a 1">1</a></r>')
function comments:comment-in-enclosed-attr() {
    <r><a b="a {(: comment :)1}">1</a></r>
};

declare 
    %test:assertEquals('<r><a>1</a></r>')
function comments:comment-in-enclosed() {
    (: before :)
    <r><a>{(: start :)1(: end :)}</a></r>
    (: after :)
};