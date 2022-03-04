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

declare variable $syse:simple-file-name := "simple-data.xml";
declare variable $syse:complex-file-name := "complex-data.xml";

declare
    %test:setUp
function syse:setup() as empty-sequence() {
    let $_ := (
        xmldb:create-collection("/db", $fixtures:collection-name),
        helper:create-db-resource($fixtures:collection, $syse:simple-file-name, $fixtures:XML),
        helper:create-db-resource($fixtures:collection, $syse:complex-file-name, $fixtures:COMPLEX_XML)
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
    %test:assertEquals("true", "true")
function syse:defaults() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        ()
    )

    return (
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:SIMPLE_XML_INDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:COMPLEX_XML_INDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:indent-no() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"indent": false()}
    )

    return (
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:SIMPLE_XML_UNINDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:COMPLEX_XML_UNINDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:indent-yes() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"indent": true()}
    )

    return (
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:SIMPLE_XML_INDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:COMPLEX_XML_INDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:omit-xml-declaration-no() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"omit-xml-declaration": false()}
    )

    return (
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:SIMPLE_XML_INDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:COMPLEX_XML_INDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:omit-xml-declaration-yes() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{"omit-xml-declaration": true()}
    )

    return (
        helper:assert-file-contents(
            $fixtures:SIMPLE_XML_INDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:COMPLEX_XML_INDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:unindented-no-declaration() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{
            "omit-xml-declaration": true(),
            "indent": false()
        }
    )

    return (
        helper:assert-file-contents(
            $fixtures:SIMPLE_XML_UNINDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:COMPLEX_XML_UNINDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:insert-final-newline-yes() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{ "exist:insert-final-newline": true() }
    )

    return (
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:SIMPLE_XML_INDENTED || $fixtures:NL,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:COMPLEX_XML_INDENTED || $fixtures:NL,
            ($directory, $syse:complex-file-name)
        )
    )
};

declare
    %test:assertEquals("true", "true")
function syse:insert-final-newline-no() {
    let $directory := helper:get-test-directory($syse:suite)
    let $sync := file:sync(
        $fixtures:collection,
        $directory,
        map{ "exist:insert-final-newline": false() }
    )

    return (
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:SIMPLE_XML_INDENTED,
            ($directory, $syse:simple-file-name)
        ),
        helper:assert-file-contents(
            $fixtures:XML_DECLARATION || $fixtures:NL ||
            $fixtures:COMPLEX_XML_INDENTED,
            ($directory, $syse:complex-file-name)
        )
    )
};
