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

module namespace sysau = "http://exist-db.org/test/system/as-user";

import module namespace sm = "http://exist-db.org/xquery/securitymanager";
import module namespace system = "http://exist-db.org/xquery/system";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals('admin')
function sysau:function-as-user-admin-inline() {
    system:function-as-user("admin", "", function() {
        (sm:id()//sm:username)[last()]/string(.)
    })
};

declare
    %test:assertEquals('guest')
function sysau:function-as-user-guest-inline() {
    system:function-as-user("guest", "guest", function() {
        (sm:id()//sm:username)[last()]/string(.)
    })
};

declare
    %test:assertEquals('admin')
function sysau:function-as-user-admin-reference() {
    system:function-as-user("admin", "", sysau:get-effective-user-id#0)
};

declare
    %test:assertEquals('guest')
function sysau:function-as-user-guest-reference() {
    system:function-as-user("guest", "guest", sysau:get-effective-user-id#0)
};

declare
    %test:assertError
function sysau:function-as-user-unknown() {
    system:function-as-user("unknown", "", function() {
        ()
    })
};

declare
    %private
function sysau:get-effective-user-id() as xs:string {
    (sm:id()//sm:username)[last()]/string(.)
};