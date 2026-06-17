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

(: eXist serialization extension parameters are supplied via their exist-namespace QName key
 : (the spec-conformant form for implementation-defined parameters). :)
declare variable $syse:exist-ns := "http://exist.sourceforge.net/NS/exist";

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
        map{ QName($syse:exist-ns, "insert-final-newline"): true() }
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
        map{ QName($syse:exist-ns, "insert-final-newline"): false() }
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

(:
 : The exist:expand-xincludes extension parameter (an exist-namespace serialization option) must be
 : honored when supplied via the options map. Regression test for
 : https://github.com/eXist-db/exist/issues/3704 — the "fill defaults" loop overwrote a supplied
 : exist:expand-xincludes back to the default, so expand-xincludes=true was silently ignored.
 :)
declare variable $syse:xinclude-host-name := "xinclude-host.xml";
declare variable $syse:xinclude-target-name := "xinclude-target.xml";

declare %private function syse:store-xinclude-fixture() as empty-sequence() {
    helper:create-db-resource($fixtures:collection, $syse:xinclude-target-name, document { <inc>INCLUDED</inc> }),
    helper:create-db-resource($fixtures:collection, $syse:xinclude-host-name,
        document {
            <doc xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="{$syse:xinclude-target-name}"/></doc>
        })
};

(: expand-xincludes=true expands the include (this is the case the #3704 bug broke) :)
declare
    %test:assertTrue
function syse:expand-xincludes-yes() {
    let $directory := helper:get-test-directory($syse:suite)
    let $_ := syse:store-xinclude-fixture()
    let $sync := file:sync($fixtures:collection, $directory, map { QName($syse:exist-ns, "expand-xincludes"): true() })
    let $content := file:read(helper:glue-path(($directory, $syse:xinclude-host-name)))
    return contains($content, "INCLUDED") and not(contains($content, "xi:include"))
};

(: expand-xincludes=false preserves the include (control: also the file:sync default) :)
declare
    %test:assertTrue
function syse:expand-xincludes-no() {
    let $directory := helper:get-test-directory($syse:suite)
    let $_ := syse:store-xinclude-fixture()
    let $sync := file:sync($fixtures:collection, $directory, map { QName($syse:exist-ns, "expand-xincludes"): false() })
    let $content := file:read(helper:glue-path(($directory, $syse:xinclude-host-name)))
    return contains($content, "xi:include")
};
