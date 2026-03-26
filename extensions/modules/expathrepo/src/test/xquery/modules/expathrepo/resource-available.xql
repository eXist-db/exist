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
 : Tests for repo:resource-available().
 : https://github.com/eXist-db/exist/issues/3904
 :)
module namespace ra="http://exist-db.org/test/resource-available";

declare namespace test="http://exist-db.org/xquery/xqsuite";
import module namespace repo="http://exist-db.org/xquery/repo";
import module namespace compression="http://exist-db.org/xquery/compression";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $ra:pkg-name := "http://exist-db.org/test/resource-available-test";

declare variable $ra:expathxml :=
    <package xmlns="http://expath.org/ns/pkg" name="{$ra:pkg-name}" abbrev="ra-test" version="1.0.0" spec="1.0">
        <title>Resource Available Test</title>
    </package>;

declare variable $ra:repoxml :=
    <meta xmlns="http://exist-db.org/xquery/repo">
        <description>Resource Available Test</description>
        <type>library</type>
        <target/>
    </meta>;

declare variable $ra:entries := (
    <entry name="expath-pkg.xml" type="xml">{$ra:expathxml}</entry>,
    <entry name="repo.xml" type="xml">{$ra:repoxml}</entry>,
    <entry name="test-data.xml" type="xml"><test><data/></test></entry>
);

declare
    %test:setUp
function ra:setup() {
    xmldb:create-collection("/db", "ra-test"),
    let $zip := compression:zip($ra:entries, false())
    let $stored := xmldb:store("/db/ra-test", "ra-test-1.0.xar", $zip)
    return repo:install-and-deploy-from-db($stored)
};

declare
    %test:tearDown
function ra:cleanup() {
    if (repo:list() = $ra:pkg-name) then (
        repo:undeploy($ra:pkg-name),
        repo:remove($ra:pkg-name)
    ) else (),
    if (xmldb:collection-available("/db/ra-test")) then
        xmldb:remove("/db/ra-test")
    else ()
};

(: A resource that was included in the package should be available :)
declare
    %test:assertTrue
function ra:existing-resource() {
    repo:resource-available($ra:pkg-name, "expath-pkg.xml")
};

(: A resource not in the package should not be available :)
declare
    %test:assertFalse
function ra:missing-resource() {
    repo:resource-available($ra:pkg-name, "nonexistent-file-xyz.xml")
};

(: A non-existing package should return false :)
declare
    %test:assertFalse
function ra:missing-package() {
    repo:resource-available("http://example.com/no-such-package-42", "anything.xml")
};
