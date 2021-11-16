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

module namespace readbinary="http://exist-db.org/testsuite/modules/file/read-binary";
import module namespace helper="http://exist-db.org/xquery/test/util/helper" at "resource:util/helper.xqm";
import module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures" at "resource:util/fixtures.xqm";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace file="http://exist-db.org/xquery/file";
import module namespace util="http://exist-db.org/xquery/util";

declare variable $readbinary:suite := "read-binary";

declare
    %test:setUp
function readbinary:setup() as empty-sequence() {
};

declare
    %test:tearDown
function readbinary:tear-down() as empty-sequence() {
    helper:clear-suite-fs($readbinary:suite)
};

declare
    %test:assertEquals("SERVER_SECRET=123!")
function readbinary:without-serialization() {
    helper:get-test-directory($readbinary:suite)
    => helper:setup-fs-extra()
    => concat("/.env")
    => file:read-binary()
    => util:binary-to-string()
};