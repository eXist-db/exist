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

(:~
 : Tests for the Inspect module
 :)
module namespace insp = "http://exist-db.org/test/insp";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace inspect = "http://exist-db.org/xquery/inspection";

declare
    %test:setUp
function insp:setup() {
    let $coll := xmldb:create-collection("/db", "inspect-test")
    return (
        xmldb:store($coll, "mod1.xqy", "<main>{current-dateTime()}</main>", "application/xquery"),
        xmldb:store($coll, "mod1.xqm", "module namespace x = ""http://x.com""; declare function x:y() { <library>{current-dateTime()}</library> };", "application/xquery")
    )
};

declare
    %test:tearDown
function insp:cleanup() {
    xmldb:remove("/db/inspect-test")
};

declare
    %test:assertEquals(0)
function insp:module-functions-main-module() {
    count(inspect:inspect-module(xs:anyURI("xmldb:exist:///db/inspect-test/mod1.xqy")))
};

declare
    %test:assertEquals(1)
function insp:module-functions-library-module() {
    count(inspect:inspect-module(xs:anyURI("xmldb:exist:///db/inspect-test/mod1.xqm")))
};

(: When there is an issue, the issue is printed as part of the error messages:)
declare
    %test:assertEquals("END")
function insp:check_if_prefix_is_present() {
    (
        for $namespace-uri in util:registered-modules()
        let $functions := try { util:registered-functions($namespace-uri) } catch * { <error>{$err:code} raised when getting registered functions for module {$namespace-uri}</error> }
        for $fie in $functions
        return
            if( contains($fie,":") or contains($namespace-uri, "w3.org") )
            then   () else $fie || " in " || $namespace-uri
    ,"END")
};

