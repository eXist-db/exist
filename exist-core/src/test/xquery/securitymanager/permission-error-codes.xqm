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
 : The sm: permission functions assign distinct error codes so callers can tell a
 : permission failure (EXXQDY0007) from an invalid-argument failure (EXXQDY0008)
 : by branching on $err:code rather than matching message text -- e.g. an HTTP API
 : mapping the former to 403 and the latter to 400.
 :)
module namespace pec = "http://exist-db.org/test/securitymanager/permission-error-codes";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace sm = "http://exist-db.org/xquery/securitymanager";

declare variable $pec:col := "/db/permission-error-codes-test";
declare variable $pec:resource := $pec:col || "/locked.xml";

(: a resource owned by admin with no group/other access, so a non-owner non-dba
   (guest) is denied any chmod on it :)
declare
    %test:setUp
function pec:setup() {
    xmldb:create-collection("/db", "permission-error-codes-test"),
    xmldb:store($pec:col, "locked.xml", <locked/>),
    sm:chmod(xs:anyURI($pec:resource), "rwx------")
};

declare
    %test:tearDown
function pec:teardown() {
    if (xmldb:collection-available($pec:col)) then xmldb:remove($pec:col) else ()
};

(: invalid argument -> EXXQDY0008: sm:has-access requires a partial (<=3 char) mode :)
declare
    %test:assertError("EXXQDY0008")
function pec:has-access-overlong-mode() {
    sm:has-access(xs:anyURI("/db"), "rwxrwxrwx")
};

(: invalid argument -> EXXQDY0008: sm:mode-to-octal rejects a malformed symbolic mode :)
declare
    %test:assertError("EXXQDY0008")
function pec:mode-to-octal-bad-syntax() {
    sm:mode-to-octal("not-a-valid-mode")
};

(: permission denied -> EXXQDY0007: guest is neither owner nor dba of the locked resource :)
declare
    %test:assertError("EXXQDY0007")
function pec:guest-chmod-denied() {
    system:as-user("guest", "guest", sm:chmod(xs:anyURI($pec:resource), "rwxr-xr-x"))
};
