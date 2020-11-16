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
 : Test functions with empty body parse and are callable
 : https://github.com/eXist-db/exist/issues/3551
 :)
module namespace noop="http://exist-db.org/xquery/test/noop";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $noop:variable := function () {};
declare function noop:declared () {};
declare function noop:declared-with-parameter ($p) {};

declare
    %test:assertTrue
function noop:callable-inline() {
    empty((function () {})())
};

declare
    %test:assertTrue
function noop:callable-let-var() {
    let $noop := function () {}
    return empty($noop())
};

declare
    %test:assertTrue
function noop:callable-declared-var() {
    empty($noop:variable())
};

declare
    %test:assertTrue
function noop:callable-declared-function() {
    empty(noop:declared())
};

declare
    %test:assertTrue
function noop:callable-declared-with-param-function() {
    empty(noop:declared-with-parameter("this parameter is ignored"))
};
