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

module namespace account = "http://exist-db.org/test/securitymanager/account";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace sm = "http://exist-db.org/xquery/securitymanager";

declare
    %test:assertFalse
function account:is-account-enabled-nonexisting() {
    sm:is-account-enabled('blablabla')
};

declare
    %test:assertTrue
function account:is-account-enabled-guest() {
    sm:is-account-enabled('guest')
};
