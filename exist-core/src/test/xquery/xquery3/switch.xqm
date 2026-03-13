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
 : ensure that different types can be matched to string literals in switch-statements
 : @see https://github.com/eXist-db/exist/issues/6131
 :)
module namespace tsw="http://exist-db.org/xquery/test/switch";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertTrue
function tsw:element() {
    switch(<node>test</node>)
        case "test" return true()
        default return false()
};

declare
    %test:assertTrue
function tsw:text() {
    switch(text{"test"})
        case "test" return true()
        default return false()
};

declare
    %test:assertTrue
function tsw:array() {
    switch(["test"])
        case "test" return true()
        default return false()
};

declare
    %test:assertTrue
function tsw:anyURI() {
    switch(xs:anyURI("test"))
        case "test" return true()
        default return false()
};

declare
    %test:assertTrue
function tsw:NCName() {
    switch(xs:NCName("test"))
        case "test" return true()
        default return false()
};

declare
    %test:assertTrue
function tsw:ID() {
    switch(xs:ID("test"))
        case "test" return true()
        default return false()
};
