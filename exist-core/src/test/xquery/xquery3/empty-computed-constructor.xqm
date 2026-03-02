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
 : Tests for empty computed constructors.
 :
 : @see https://github.com/eXist-db/exist/issues/4593
 : @see https://github.com/eXist-db/exist/issues/4594
 : @see https://github.com/eXist-db/exist/issues/4595
 :)
module namespace ecc = "http://exist-db.org/xquery/test/empty-computed-constructors";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(:~ document {} should produce a document node (issue #4593) :)
declare
    %test:assertTrue
function ecc:empty-document-constructor() {
    document {} instance of document-node()
};

(:~ document { () } should also produce a document node :)
declare
    %test:assertTrue
function ecc:empty-document-constructor-with-empty-sequence() {
    document { () } instance of document-node()
};

(:~ comment {} should produce a comment node (issue #4594) :)
declare
    %test:assertTrue
function ecc:empty-comment-constructor() {
    comment {} instance of comment()
};

(:~ comment { () } should also produce a comment node :)
declare
    %test:assertTrue
function ecc:empty-comment-constructor-with-empty-sequence() {
    comment { () } instance of comment()
};

(:~ comment {} should have zero-length string value :)
declare
    %test:assertEquals("")
function ecc:empty-comment-string-value() {
    string(comment {})
};

(:~ namespace foo {} should raise XQDY0101 (issue #4595) :)
declare
    %test:assertError("XQDY0101")
function ecc:empty-namespace-constructor() {
    namespace foo {}
};

(:~ namespace foo { () } should also raise XQDY0101 :)
declare
    %test:assertError("XQDY0101")
function ecc:empty-namespace-constructor-with-empty-sequence() {
    namespace foo { () }
};
