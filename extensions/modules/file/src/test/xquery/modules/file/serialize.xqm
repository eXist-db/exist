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

module namespace serialize="http://exist-db.org/testsuite/modules/file/serialize";

import module namespace file="http://exist-db.org/xquery/file";
import module namespace helper="http://exist-db.org/xquery/test/util/helper" at "resource:util/helper.xqm";
import module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures" at "resource:util/fixtures.xqm";


declare namespace test="http://exist-db.org/xquery/xqsuite";


declare variable $serialize:suite := "serialize";
declare variable $serialize:text := <node>data</node>;
declare variable $serialize:xml := <root>
    <unary attribute="value" />
    <nested>
        text
    </nested>
</root>;


declare
    %test:tearDown
function serialize:tear-down() as empty-sequence() {
    helper:clear-suite-fs($serialize:suite)
};

declare
    %test:assertEquals("datamoredata")
function serialize:append() {
    let $directory := helper:get-test-directory($serialize:suite)
    let $_ := file:mkdirs($directory)

    let $path := $directory || "/append-test.txt"

    let $_ := file:serialize-binary(xs:base64Binary("data"), $path)
    let $_ := file:serialize-binary(xs:base64Binary("moredata"), $path, true())

    return file:read-binary($path) => xs:string()
};

declare
    %test:assertEquals("moredata")
function serialize:overwrite() {
    let $directory := helper:get-test-directory($serialize:suite)
    let $_ := file:mkdirs($directory)

    let $path := $directory || "/overwrite-test.txt"
    let $_ := file:serialize-binary(xs:base64Binary("data"), $path)
    let $_ := file:serialize-binary(xs:base64Binary("moredata"), $path, false())

    return file:read-binary($path) => xs:string()
};

declare
    %test:assertEquals("<node>data</node>")
function serialize:serialize3() {
    let $directory := helper:get-test-directory($serialize:suite)
    let $_ := file:mkdirs($directory)

    let $path := $directory || "/serialize-3-test.txt"
    let $_ := file:serialize($serialize:text, $path, ())
    let $_ := file:serialize($serialize:text, $path, ())

    return file:read($path)
};

declare
    %test:assertTrue
function serialize:xml-defaults() {
    let $directory := helper:get-test-directory($serialize:suite)
    let $_ := file:mkdirs($directory)

    let $path := $directory || "/xml-defaults-test.xml"
    let $_ := file:serialize($serialize:xml, $path, ())

    return file:read($path) eq
        "<root>" || $fixtures:NL ||
        "    <unary attribute=""value""/>" || $fixtures:NL ||
        "    <nested>" || $fixtures:NL ||
        "        text" || $fixtures:NL ||
        "    </nested>" || $fixtures:NL ||
        "</root>"
};

declare
    %test:assertTrue
function serialize:xml-final-newline() {
    let $directory := helper:get-test-directory($serialize:suite)
    let $_ := file:mkdirs($directory)

    let $path := $directory || "/xml-final-newline.xml"
    let $_ := file:serialize($serialize:xml, $path,
    <output:serialization-parameters
            xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
          <exist:insert-final-newline value="yes" />
        </output:serialization-parameters>)

    return file:read($path) eq
        "<root>" || $fixtures:NL ||
        "    <unary attribute=""value""/>" || $fixtures:NL ||
        "    <nested>" || $fixtures:NL ||
        "        text" || $fixtures:NL ||
        "    </nested>" || $fixtures:NL ||
        "</root>" || $fixtures:NL
};

declare
    %test:assertTrue
function serialize:xml-minified() {
    let $directory := helper:get-test-directory($serialize:suite)
    let $_ := file:mkdirs($directory)

    let $path := $directory || "/xml-minified.xml"
    let $_ := file:serialize($serialize:xml, $path,
    <output:serialization-parameters
        xmlns:output="http://www.w3.org/2010/xslt-xquery-serialization">
      <output:indent value="no"/>
    </output:serialization-parameters>)

    return file:read($path) eq
        "<root><unary attribute=""value""/><nested>" || $fixtures:NL ||
        "        text" || $fixtures:NL ||
        "    </nested></root>"
};

