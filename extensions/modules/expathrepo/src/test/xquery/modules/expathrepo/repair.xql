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
 : Tests for repairing the package repository (repair.xql), which a restore runs.
 : A scanned collection without an expath-pkg.xml must be passed over: fn:doc raises
 : err:FODC0002 for a missing document.
 :)
module namespace rt="http://exist-db.org/test/repair";

declare namespace test="http://exist-db.org/xquery/xqsuite";
import module namespace repair="http://exist-db.org/xquery/repo/repair"
    at "resource:org/exist/xquery/modules/expathrepo/repair.xql";
import module namespace repo="http://exist-db.org/xquery/repo";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $rt:root := "/db/repair-test";
declare variable $rt:empty := $rt:root || "/no-package";
declare variable $rt:pkg := $rt:root || "/package";
declare variable $rt:pkg-name := "http://exist-db.org/test/repair-test";

declare
    %test:setUp
function rt:setup() {
    xmldb:create-collection("/db", "repair-test"),
    xmldb:create-collection($rt:root, "no-package"),
    xmldb:store($rt:empty, "data.xml", <data/>),
    xmldb:create-collection($rt:root, "package"),
    xmldb:store($rt:pkg, "expath-pkg.xml",
        <package xmlns="http://expath.org/ns/pkg" name="{$rt:pkg-name}" abbrev="repair-test" version="1.0.0" spec="1.0">
            <title>Repair Test</title>
        </package>),
    xmldb:store($rt:pkg, "repo.xml",
        <meta xmlns="http://exist-db.org/xquery/repo">
            <description>Repair Test</description>
            <type>library</type>
            <target/>
        </meta>)
};

declare
    %test:tearDown
function rt:cleanup() {
    if (repo:list() = $rt:pkg-name) then
        repo:remove($rt:pkg-name)
    else (),
    if (xmldb:collection-available($rt:root)) then
        xmldb:remove($rt:root)
    else ()
};

(: A collection holding no expath-pkg.xml, such as the repository root, is not a package :)
declare
    %test:assertEquals(0)
function rt:collection-without-descriptor() {
    count(repair:repair(xs:anyURI($rt:empty))/*)
};

(: A collection holding a package's files is registered again :)
declare
    %test:assertEquals("true", "true")
function rt:collection-with-descriptor() {
    string(repair:repair(xs:anyURI($rt:pkg))/package[@name = $rt:pkg-name]/@restored),
    string(repo:list() = $rt:pkg-name)
};
