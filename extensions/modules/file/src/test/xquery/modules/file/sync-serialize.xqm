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

(:
 : test serialization defaults and setting different serialization options on
 : file:sync#3
 :)
module namespace syse="http://exist-db.org/xquery/test/file/sync-serialize";


import module namespace helper="http://exist-db.org/xquery/test/util/helper" at "resource:util/helper.xqm";
import module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures" at "resource:util/fixtures.xqm";


declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $syse:suite := "syse";

declare variable $syse:indented-XML :=
    <root>
        <item>This is a very long line. Certainly longer than eighty characters. It is here to see what happens to lines longer than a certain limit. Let's see.</item>
        <empty-item></empty-item>
        <unary />
        <item>

            MIXED CONTENT&#10;
            <nested
                attr = "with lots of whitespace"
            />
            The next word <hi>is</hi> highlighted.
        </item>
        <p>
            <y>
                <r>
                    <a>
                        <mid />
                    </a>
                </r>
            </y>
        </p>
    </root>
;

declare variable $syse:default-serialization-XML :=
``[<root>
    <item>This is a very long line. Certainly longer than eighty characters. It is here to see what happens to lines longer than a certain limit. Let's see.</item>
    <empty-item/>
    <unary/>
    <item>

            MIXED CONTENT

            <nested attr="with lots of whitespace"/>
            The next word <hi>is</hi> highlighted.
        </item>
    <p>
        <y>
            <r>
                <a>
                    <mid/>
                </a>
            </r>
        </y>
    </p>
</root>]``
;

(: TODO(JL) the encoding value capitalization changes when reading default values :)
declare variable $syse:exist-xml-declaration := '<?xml version="1.0" encoding="UTF-8"?>';
declare variable $syse:exist-xml-declaration-lower-case := '<?xml version="1.0" encoding="utf-8"?>';

declare variable $syse:newline := "&#10;";

declare variable $syse:expected-indented-XML-unindented :=
    serialize($syse:indented-XML, map {"indent": false()});

declare variable $syse:expected-minified-XML-indented := ``[<foo>
    <bar/>
</foo>]``
;

declare
    %test:setUp
function syse:setup() as empty-sequence() {
    let $_ := (
        xmldb:create-collection("/db", $fixtures:collection-name),
        helper:create-db-resource($fixtures:collection, "minified-data.xml", $fixtures:XML),
        helper:create-db-resource($fixtures:collection, "indented-data.xml", $syse:indented-XML)
    )
    return ()
};

declare
    %test:tearDown
function syse:tear-down() {
    helper:clear-db(),
    helper:clear-suite-fs($syse:suite)
};

declare
    %test:assertTrue
function syse:defaults() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        ()
    )

    return file:read($directory || "/indented-data.xml")
        = $syse:exist-xml-declaration || $syse:newline || $syse:default-serialization-XML
};

declare
    %test:assertTrue
function syse:indent-no() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"indent": false()}
    )

    return file:read($directory || "/indented-data.xml") =
         $syse:exist-xml-declaration-lower-case || $syse:newline || $syse:expected-indented-XML-unindented
};

declare
    %test:assertTrue
function syse:indent-yes() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"indent": true()}
    )

    return
        file:read($directory || "/minified-data.xml") =
             $syse:exist-xml-declaration-lower-case || $syse:newline || $syse:expected-minified-XML-indented

        and
            file:read($directory || "/indented-data.xml") =
                $syse:exist-xml-declaration-lower-case || $syse:newline || $syse:default-serialization-XML
};

declare
    %test:assertTrue
function syse:omit-xml-declaration-no() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"omit-xml-declaration": false()}
    )

    return
        file:read($directory || "/indented-data.xml")
            => starts-with($syse:exist-xml-declaration-lower-case || $syse:newline)
};

declare
    %test:assertFalse
function syse:omit-xml-declaration-yes() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"omit-xml-declaration": true()}
    )

    return
        file:read($directory || "/indented-data.xml")
            => starts-with($syse:exist-xml-declaration || $syse:newline)
};