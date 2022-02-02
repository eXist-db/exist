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

module namespace ee="http://exist-db.org/xquery/test/enclosed-expressions";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Enclosed expressions in element content may be empty in 3.1. Tests borrowed from QT3.
 :
 : @see https://github.com/w3c/qt3tests/blob/44252d2496286b0bd0ca1e3b3622f369f0890557/prod/DirElemContent.xml#L1173-L1193
:)

declare
    %test:assertEquals("<elem>contentcontent</elem>")
function ee:K2-DirectConElemContent-26a() {
    <elem>content{}content</elem>
};

declare
    %test:assertEquals("<elem>contentcontent</elem>")
function ee:K2-DirectConElemContent-26b() {
    <elem>content{(:comment:)}content</elem>
};

declare
    %test:assertEquals('<elem attribute="contentcontent"/>')
function ee:direct-attribute-constructor-empty() {
    <elem attribute="content{}content" />
};

declare
    %test:assertEquals('<elem attribute="contentcontent"/>')
function ee:direct-attribute-constructor-comment() {
    <elem attribute="content{(:comment:)}content" />
};
