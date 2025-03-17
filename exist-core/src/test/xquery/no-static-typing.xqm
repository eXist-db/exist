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

(:
 : Tests created for issue https://github.com/exist-db/exist/issues/2445
 :)
module namespace tnst="http://exist-db.org/xquery/test/no-static-typing";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:assertEmpty
function tnst:self-other-node-name() {
    <a/>[self::b]
};

declare
    %test:assertEmpty
function tnst:element-after-attribute() {
    <a/>/@b/c
};

declare
    %test:assertEmpty
function tnst:element-after-text() {
    <a/>/text()/b
};

declare
    %test:assertEmpty
function tnst:descendant-or-self-other-node-name() {
    <a/>[descendant-or-self::b]
};

declare
    %test:assertEmpty
function tnst:element-after-descendant-or-self-attribute() {
    <a/>/descendant-or-self::attribute(b)/c
};

declare
    %test:assertEmpty
function tnst:element-after-descendant-or-self-text() {
    <a/>/descendant-or-self::text()/b
};

