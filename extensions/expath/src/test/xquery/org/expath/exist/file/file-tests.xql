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

module namespace ft="http://exist-db.org/testsuite/expath-file";

import module namespace test="http://exist-db.org/xquery/xqsuite"
    at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace exfile="http://expath.org/ns/file";

(: ======== Helper variables ======== :)

declare variable $ft:temp-dir := exfile:temp-dir();
declare variable $ft:test-dir := $ft:temp-dir || "expath-file-test/";

(: ======== Setup / Teardown ======== :)

declare
    %test:setUp
function ft:setup() {
    exfile:create-dir($ft:test-dir)
};

declare
    %test:tearDown
function ft:teardown() {
    if (exfile:exists($ft:test-dir)) then
        exfile:delete($ft:test-dir, true())
    else
        ()
};

(: ======== System Properties ======== :)

declare
    %test:assertExists
function ft:dir-separator() {
    exfile:dir-separator()
};

declare
    %test:assertExists
function ft:line-separator() {
    exfile:line-separator()
};

declare
    %test:assertExists
function ft:path-separator() {
    exfile:path-separator()
};

declare
    %test:assertExists
function ft:temp-dir() {
    exfile:temp-dir()
};

declare
    %test:assertExists
function ft:current-dir() {
    exfile:current-dir()
};

(: temp-dir and current-dir should end with separator :)
declare
    %test:assertTrue
function ft:temp-dir-ends-with-separator() {
    ends-with(exfile:temp-dir(), exfile:dir-separator())
};

declare
    %test:assertTrue
function ft:current-dir-ends-with-separator() {
    ends-with(exfile:current-dir(), exfile:dir-separator())
};

(: ======== File Properties ======== :)

declare
    %test:assertTrue
function ft:exists-temp-dir() {
    exfile:exists(exfile:temp-dir())
};

declare
    %test:assertFalse
function ft:exists-nonexistent() {
    exfile:exists($ft:test-dir || "nonexistent-file.txt")
};

declare
    %test:assertTrue
function ft:is-dir-temp() {
    exfile:is-dir(exfile:temp-dir())
};

declare
    %test:assertFalse
function ft:is-dir-nonexistent() {
    exfile:is-dir($ft:test-dir || "nonexistent")
};

declare
    %test:assertTrue
function ft:is-file-after-write() {
    let $path := $ft:test-dir || "is-file-test.txt"
    let $_ := exfile:write-text($path, "hello")
    return exfile:is-file($path)
};

declare
    %test:assertFalse
function ft:is-file-on-dir() {
    exfile:is-file($ft:test-dir)
};

declare
    %test:assertTrue
function ft:is-absolute-absolute-path() {
    exfile:is-absolute(exfile:temp-dir())
};

declare
    %test:assertFalse
function ft:is-absolute-relative-path() {
    exfile:is-absolute("relative/path")
};

declare
    %test:assertExists
function ft:last-modified() {
    let $path := $ft:test-dir || "last-mod-test.txt"
    let $_ := exfile:write-text($path, "test")
    return exfile:last-modified($path)
};

declare
    %test:assertEquals(5)
function ft:size-file() {
    let $path := $ft:test-dir || "size-test.txt"
    let $_ := exfile:write-text($path, "hello")
    return exfile:size($path)
};

declare
    %test:assertEquals(0)
function ft:size-dir() {
    exfile:size($ft:test-dir)
};

(: ======== Read / Write Text ======== :)

declare
    %test:assertEquals("hello world")
function ft:write-read-text() {
    let $path := $ft:test-dir || "write-read.txt"
    let $_ := exfile:write-text($path, "hello world")
    return exfile:read-text($path)
};

declare
    %test:assertEquals("héllo wörld")
function ft:write-read-text-utf8() {
    let $path := $ft:test-dir || "write-read-utf8.txt"
    let $_ := exfile:write-text($path, "héllo wörld")
    return exfile:read-text($path)
};

declare
    %test:assertEquals("line1", "line2", "line3")
function ft:write-read-text-lines() {
    let $path := $ft:test-dir || "lines-test.txt"
    let $_ := exfile:write-text-lines($path, ("line1", "line2", "line3"))
    return exfile:read-text-lines($path)
};

declare
    %test:assertEquals(0)
function ft:write-text-lines-empty() {
    let $path := $ft:test-dir || "empty-lines.txt"
    let $_ := exfile:write-text-lines($path, ())
    return count(exfile:read-text-lines($path))
};

(: Verify newline normalization: CR and CRLF -> LF :)
declare
    %test:assertEquals("a", "b", "c")
function ft:read-text-lines-normalization() {
    let $path := $ft:test-dir || "crlf-test.txt"
    (: Write raw bytes with CRLF line endings :)
    let $_ := exfile:write-text($path, "a&#13;&#10;b&#13;c")
    return exfile:read-text-lines($path)
};

(: ======== Read / Write Binary ======== :)

declare
    %test:assertExists
function ft:write-read-binary() {
    let $path := $ft:test-dir || "binary-test.bin"
    let $data := xs:base64Binary("SGVsbG8gV29ybGQ=") (: "Hello World" :)
    let $_ := exfile:write-binary($path, $data)
    return exfile:read-binary($path)
};

(: ======== Append ======== :)

declare
    %test:assertEquals("helloworld")
function ft:append-text() {
    let $path := $ft:test-dir || "append-test.txt"
    let $_ := exfile:write-text($path, "hello")
    let $_ := exfile:append-text($path, "world")
    return exfile:read-text($path)
};

declare
    %test:assertEquals("line1", "line2", "line3", "line4")
function ft:append-text-lines() {
    let $path := $ft:test-dir || "append-lines.txt"
    let $_ := exfile:write-text-lines($path, ("line1", "line2"))
    let $_ := exfile:append-text-lines($path, ("line3", "line4"))
    return exfile:read-text-lines($path)
};

(: ======== Directory Operations ======== :)

declare
    %test:assertTrue
function ft:create-dir() {
    let $dir := $ft:test-dir || "subdir/"
    let $_ := exfile:create-dir($dir)
    return exfile:is-dir($dir)
};

declare
    %test:assertTrue
function ft:create-dir-nested() {
    let $dir := $ft:test-dir || "a/b/c/"
    let $_ := exfile:create-dir($dir)
    return exfile:is-dir($dir)
};

declare
    %test:assertExists
function ft:create-temp-dir() {
    let $dir := exfile:create-temp-dir("test-", "-dir", $ft:test-dir)
    return
        if (exfile:is-dir($dir)) then
            $dir
        else
            ()
};

declare
    %test:assertExists
function ft:create-temp-file() {
    let $f := exfile:create-temp-file("test-", ".tmp", $ft:test-dir)
    return
        if (exfile:is-file($f)) then
            $f
        else
            ()
};

(: ======== List / Children / Descendants ======== :)

declare
    %test:assertExists
function ft:list-dir() {
    let $_ := exfile:write-text($ft:test-dir || "list-a.txt", "a")
    let $_ := exfile:write-text($ft:test-dir || "list-b.txt", "b")
    return exfile:list($ft:test-dir)
};

declare
    %test:assertTrue
function ft:list-contains-file() {
    let $_ := exfile:write-text($ft:test-dir || "list-find.txt", "find me")
    return "list-find.txt" = exfile:list($ft:test-dir)
};

declare
    %test:assertTrue
function ft:list-dir-trailing-separator() {
    let $_ := exfile:create-dir($ft:test-dir || "list-subdir")
    let $entries := exfile:list($ft:test-dir)
    return some $e in $entries satisfies
        starts-with($e, "list-subdir") and ends-with($e, exfile:dir-separator())
};

declare
    %test:assertTrue
function ft:list-recursive() {
    let $_ := exfile:create-dir($ft:test-dir || "rec-dir")
    let $_ := exfile:write-text($ft:test-dir || "rec-dir/nested.txt", "nested")
    let $entries := exfile:list($ft:test-dir, true())
    return some $e in $entries satisfies contains($e, "nested.txt")
};

declare
    %test:assertTrue
function ft:list-pattern() {
    let $_ := exfile:write-text($ft:test-dir || "pat-a.txt", "a")
    let $_ := exfile:write-text($ft:test-dir || "pat-b.xml", "b")
    let $entries := exfile:list($ft:test-dir, false(), "*.txt")
    return
        (some $e in $entries satisfies $e = "pat-a.txt")
        and not(some $e in $entries satisfies $e = "pat-b.xml")
};

declare
    %test:assertExists
function ft:children() {
    let $_ := exfile:write-text($ft:test-dir || "child.txt", "x")
    return exfile:children($ft:test-dir)
};

declare
    %test:assertTrue
function ft:children-absolute-paths() {
    let $_ := exfile:write-text($ft:test-dir || "child-abs.txt", "x")
    let $children := exfile:children($ft:test-dir)
    return every $c in $children satisfies exfile:is-absolute($c)
};

declare
    %test:assertExists
function ft:descendants() {
    let $_ := exfile:create-dir($ft:test-dir || "desc-dir")
    let $_ := exfile:write-text($ft:test-dir || "desc-dir/deep.txt", "deep")
    return exfile:descendants($ft:test-dir)
};

declare
    %test:assertExists
function ft:list-roots() {
    exfile:list-roots()
};

(: ======== Copy / Move / Delete ======== :)

declare
    %test:assertEquals("copy content")
function ft:copy-file() {
    let $src := $ft:test-dir || "copy-src.txt"
    let $dst := $ft:test-dir || "copy-dst.txt"
    let $_ := exfile:write-text($src, "copy content")
    let $_ := exfile:copy($src, $dst)
    return exfile:read-text($dst)
};

declare
    %test:assertTrue
function ft:move-file() {
    let $src := $ft:test-dir || "move-src.txt"
    let $dst := $ft:test-dir || "move-dst.txt"
    let $_ := exfile:write-text($src, "move content")
    let $_ := exfile:move($src, $dst)
    return
        exfile:exists($dst) and not(exfile:exists($src))
};

declare
    %test:assertFalse
function ft:delete-file() {
    let $path := $ft:test-dir || "delete-me.txt"
    let $_ := exfile:write-text($path, "bye")
    let $_ := exfile:delete($path)
    return exfile:exists($path)
};

declare
    %test:assertFalse
function ft:delete-dir-recursive() {
    let $dir := $ft:test-dir || "delete-dir/"
    let $_ := exfile:create-dir($dir)
    let $_ := exfile:write-text($dir || "inner.txt", "inner")
    let $_ := exfile:delete($dir, true())
    return exfile:exists($dir)
};

(: ======== Path Functions ======== :)

declare
    %test:assertEquals("file.txt")
function ft:name() {
    exfile:name("/some/path/file.txt")
};

declare
    %test:assertEquals("")
function ft:name-root() {
    exfile:name("/")
};

declare
    %test:assertExists
function ft:parent() {
    exfile:parent("/some/path/file.txt")
};

declare
    %test:assertEmpty
function ft:parent-root() {
    exfile:parent("/")
};

declare
    %test:assertTrue
function ft:parent-ends-with-separator() {
    ends-with(exfile:parent("/some/path/file.txt"), exfile:dir-separator())
};

declare
    %test:assertExists
function ft:path-to-native() {
    (: temp-dir definitely exists :)
    exfile:path-to-native(exfile:temp-dir())
};

declare
    %test:assertTrue
function ft:path-to-uri-starts-with-file() {
    starts-with(string(exfile:path-to-uri("/tmp")), "file:/")
};

declare
    %test:assertTrue
function ft:resolve-path-absolute() {
    let $resolved := exfile:resolve-path("relative")
    return exfile:is-absolute($resolved)
};

declare
    %test:assertTrue
function ft:resolve-path-with-base() {
    let $resolved := exfile:resolve-path("child.txt", "/some/base/")
    return contains($resolved, "base") and contains($resolved, "child.txt")
};

(: ======== Serialized Write / Append ======== :)

declare
    %test:assertTrue
function ft:write-xml() {
    let $path := $ft:test-dir || "write-xml.xml"
    let $_ := exfile:write($path, <root><child>text</child></root>)
    let $content := exfile:read-text($path)
    return contains($content, "<root>") and contains($content, "text")
};

declare
    %test:assertTrue
function ft:append-xml() {
    let $path := $ft:test-dir || "append-xml.xml"
    let $_ := exfile:write($path, <first/>)
    let $_ := exfile:append($path, <second/>)
    let $content := exfile:read-text($path)
    return contains($content, "first") and contains($content, "second")
};

(: ======== Error Conditions ======== :)

declare
    %test:assertError("exfile:not-found")
function ft:read-text-not-found() {
    exfile:read-text($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("exfile:is-dir")
function ft:read-text-is-dir() {
    exfile:read-text($ft:test-dir)
};

declare
    %test:assertError("exfile:not-found")
function ft:last-modified-not-found() {
    exfile:last-modified($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("exfile:not-found")
function ft:size-not-found() {
    exfile:size($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("exfile:not-found")
function ft:delete-not-found() {
    exfile:delete($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("exfile:not-found")
function ft:children-not-found() {
    exfile:children($ft:test-dir || "does-not-exist/")
};

declare
    %test:assertError("exfile:no-dir")
function ft:children-not-dir() {
    let $path := $ft:test-dir || "not-a-dir.txt"
    let $_ := exfile:write-text($path, "x")
    return exfile:children($path)
};

declare
    %test:assertError("exfile:no-dir")
function ft:write-text-no-parent-dir() {
    exfile:write-text($ft:test-dir || "no-such-parent/file.txt", "hello")
};

declare
    %test:assertError("exfile:unknown-encoding")
function ft:read-text-bad-encoding() {
    let $path := $ft:test-dir || "encoding-test.txt"
    let $_ := exfile:write-text($path, "hello")
    return exfile:read-text($path, "not-a-real-encoding")
};

declare
    %test:assertError("exfile:not-found")
function ft:path-to-native-not-found() {
    exfile:path-to-native("/this/path/surely/does/not/exist/anywhere")
};
