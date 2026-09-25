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
 : The name of a processing instruction is its target, in no namespace, for
 : stored processing instructions as much as for constructed ones.
 :)
module namespace pin="http://exist-db.org/xquery/test/processing-instruction-name";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $pin:XML := document { <root><?target data?><a/></root> };

declare
    %test:setUp
function pin:setup() {
    xmldb:create-collection("/db", "test-pi-name"),
    xmldb:store("/db/test-pi-name", "test.xml", $pin:XML)
};

declare
    %test:tearDown
function pin:tearDown() {
    xmldb:remove("/db/test-pi-name")
};

declare function pin:stored() as processing-instruction() {
    doc("/db/test-pi-name/test.xml")/root/processing-instruction()
};

declare
    %test:assertEquals("target")
function pin:name-mem() {
    name($pin:XML/root/processing-instruction())
};

declare
    %test:assertEquals("target")
function pin:name-db() {
    name(pin:stored())
};

declare
    %test:assertEquals("target")
function pin:node-name-db() {
    string(node-name(pin:stored()))
};

declare
    %test:assertEquals("")
function pin:node-name-namespace-db() {
    namespace-uri-from-QName(node-name(pin:stored()))
};

declare
    %test:assertEquals("target")
function pin:local-name-db() {
    local-name(pin:stored())
};

declare
    %test:assertEquals("data")
function pin:filter-by-name-db() {
    string(doc("/db/test-pi-name/test.xml")/root/processing-instruction()[name() eq "target"])
};
