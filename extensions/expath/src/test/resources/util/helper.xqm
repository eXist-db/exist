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

module namespace helper="http://exist-db.org/xquery/test/util/helper";
import module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures" at "fixtures.xqm";
import module namespace file="http://expath.org/ns/file";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";
import module namespace util="http://exist-db.org/xquery/util";

declare namespace utilns="http://exist-db.org/xquery/util";

declare variable $helper:error := xs:QName("helper:assert-sync-error");

declare variable $helper:path-separator := util:system-property("file.separator");

(:
/db
    /file-module-test
        /data
            test-data.xml
        test-text.txt
        test-query.xq
        bin
:)
declare function helper:setup-db() as empty-sequence() {
    let $_ := (
        xmldb:create-collection("/db", $fixtures:collection-name),
        helper:create-db-resource($fixtures:collection, "test-text.txt", $fixtures:TXT),
        helper:create-db-resource($fixtures:collection, "test-query.xq", $fixtures:XQY),
        helper:create-db-resource($fixtures:collection, "bin", $fixtures:BIN),

        xmldb:create-collection($fixtures:collection, $fixtures:child-collection-name),
        helper:create-db-resource($fixtures:child-collection, "test-data.xml", $fixtures:XML)
    )
    return ()
};

declare function helper:clear-db() {
    xmldb:remove($fixtures:collection)
};

declare function helper:create-db-resource($collection as xs:string, $resource as xs:string, $content as item()) as empty-sequence() {
    let $_ := xmldb:store($collection, $resource, $content)
    return ()
};

declare function helper:modify-db-resource($collection as xs:string, $resource as xs:string) as empty-sequence() {
    let $_ := xmldb:touch($collection, $resource, $fixtures:mod-date-2)
    return ()
};

declare function helper:clear-suite-fs ($suite as xs:string) as empty-sequence() {
    let $dir :=
        helper:glue-path((
            util:system-property("java.io.tmpdir"),
            $suite
        ))
    return
        if (file:exists($dir)) then
            let $_ := file:delete($dir, true())
            return ()
        else ()
};

declare function helper:clear-fs ($directory as xs:string) as empty-sequence() {
    if (file:exists($directory)) then
        let $_ := file:delete($directory, true())
        return ()
    else ()
};

declare function helper:get-test-directory ($suite as xs:string) as xs:string {
    helper:glue-path((
        util:system-property("java.io.tmpdir"),
        $suite,
        util:uuid()
    ))
};

declare function helper:glue-path ($parts as xs:string+) as xs:string {
    string-join($parts, $helper:path-separator)
};

(:
 : clear FS state and simulate additional data on the file system in a specific directory
 : @returns given directory to allow use in pipeline (chain of arrow operators)
 :)
declare function helper:setup-fs-extra ($directory as xs:string) as xs:string {
    let $_ := file:create-dir($directory)
    let $_ := file:create-dir(helper:glue-path(($directory, "test")))
    let $_ := (
        file:write-binary(
            helper:glue-path(($directory, ".env")),
            util:string-to-binary("SERVER_SECRET=123!")),
        file:write-binary(
            helper:glue-path(($directory, "test", "three.s")),
            util:string-to-binary("..."))
    )
    return $directory
};

declare function helper:get-deleted-from-sync-result ($result as element(utilns:sync)) as xs:string* {
    $result//utilns:delete/@name/string()
};

declare function helper:get-dir-from-sync-result ($result as element(utilns:sync)) as xs:string* {
    $result/@utilns:dir/string()
};

declare function helper:get-updated-from-sync-result ($result as element(utilns:sync)) as xs:string* {
    $result//utilns:update/@name/string()
};

declare function helper:list-files-and-directories ($directory as xs:string) as xs:string* {
    (: EXPath file:list returns relative path strings, directories end with separator :)
    for $entry in file:list($directory)
    return
        (: strip trailing separator from directory names :)
        if (ends-with($entry, file:dir-separator()))
        then substring($entry, 1, string-length($entry) - string-length(file:dir-separator()))
        else $entry
};

declare function helper:sync-with-options ($directory as xs:string, $options as item()?) as element(utilns:sync) {
    util:file-sync($fixtures:collection, $directory, $options)/*
};

declare function helper:assert-sync-result (
    $result as document-node(element(utilns:sync)),
    $expected as map(xs:string, xs:string*)
) as xs:boolean {
    helper:assert-permutation-of(
        $expected?updated,
        helper:get-updated-from-sync-result($result/*),
        "updated"
    )
    and
    helper:assert-permutation-of(
        $expected?deleted,
        helper:get-deleted-from-sync-result($result/*),
        "deleted"
    )
    and
    helper:assert-permutation-of(
        $expected?fs,
        helper:get-dir-from-sync-result($result/*)
        => helper:list-files-and-directories(),
        "filesystem"
    )
};

declare function helper:assert-permutation-of(
    $expected as xs:anyAtomicType*,
    $actual as xs:anyAtomicType*,
    $label as xs:string
) as xs:boolean {
    let $test := fold-left(
        $expected,
        [true(), $actual],
        helper:permutation-reducer#2
    )

    return
        if (empty($expected) and not(empty($actual)))
        then error($helper:error,
            "Assertion failed (" || $label || "): expected empty sequence" ||
               " but got (" || string-join($actual, ", ") || ")")
        else if (not($test?1 or exists($test?2)))
        then error($helper:error,
            "Assertion failed (" || $label || "): expected permutation of " ||
               "(" || string-join($expected, ", ") || ")" ||
               " but got (" || string-join($actual, ", ") || ")")
        else true()
};

declare function helper:permutation-reducer ($result, $next) as array(*) {
    let $first-index := index-of($result?2, $next)[1]
    return [
        $result?1 and $first-index > 0,
        helper:maybe-remove-item-at-index($result?2, $first-index)
    ]
};

declare function helper:maybe-remove-item-at-index($sequence as xs:anyAtomicType*, $index as xs:integer?) as xs:anyAtomicType* {
    if ($index = 1)
    then subsequence($sequence, 2)
    else if ($index > 1)
    then (
        subsequence($sequence, 1, $index - 1),
        subsequence($sequence, $index + 1)
    )
    else $sequence (: do nothing - will be handled later :)
};

declare function helper:assert-file-contents($expected as xs:string, $path-parts as xs:string+) as xs:boolean {
    let $path := helper:glue-path($path-parts)
    let $actual := file:read-text($path)

    return
        if (
            exists($actual) and
            count($actual) = 1 and
            $actual eq $expected)
        then true()
        else error(
            $helper:error,
            "File Content Assertion failed:&#10;expected file " || $path || "&#10;" ||
             "to contain string&#10;" || "<[" || $expected || "]>" ||
             " but was&#10;<["  || $actual || "]>"
        )
};
