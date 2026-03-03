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
import module namespace file="http://expath.org/ns/file";

(: ======== Helper variables ======== :)

declare variable $ft:temp-dir := file:temp-dir();
declare variable $ft:test-dir := $ft:temp-dir || "expath-file-test/";

(: ======== Setup / Teardown ======== :)

declare
    %test:setUp
function ft:setup() {
    file:create-dir($ft:test-dir)
};

declare
    %test:tearDown
function ft:teardown() {
    if (file:exists($ft:test-dir)) then
        file:delete($ft:test-dir, true())
    else
        ()
};

(: ======== System Properties ======== :)

declare
    %test:assertExists
function ft:dir-separator() {
    file:dir-separator()
};

declare
    %test:assertExists
function ft:line-separator() {
    file:line-separator()
};

declare
    %test:assertExists
function ft:path-separator() {
    file:path-separator()
};

declare
    %test:assertExists
function ft:temp-dir() {
    file:temp-dir()
};

declare
    %test:assertExists
function ft:current-dir() {
    file:current-dir()
};

(: temp-dir and current-dir should end with separator :)
declare
    %test:assertTrue
function ft:temp-dir-ends-with-separator() {
    ends-with(file:temp-dir(), file:dir-separator())
};

declare
    %test:assertTrue
function ft:current-dir-ends-with-separator() {
    ends-with(file:current-dir(), file:dir-separator())
};

(: ======== File Properties ======== :)

declare
    %test:assertTrue
function ft:exists-temp-dir() {
    file:exists(file:temp-dir())
};

declare
    %test:assertFalse
function ft:exists-nonexistent() {
    file:exists($ft:test-dir || "nonexistent-file.txt")
};

declare
    %test:assertTrue
function ft:is-dir-temp() {
    file:is-dir(file:temp-dir())
};

declare
    %test:assertFalse
function ft:is-dir-nonexistent() {
    file:is-dir($ft:test-dir || "nonexistent")
};

declare
    %test:assertTrue
function ft:is-file-after-write() {
    let $path := $ft:test-dir || "is-file-test.txt"
    let $_ := file:write-text($path, "hello")
    return file:is-file($path)
};

declare
    %test:assertFalse
function ft:is-file-on-dir() {
    file:is-file($ft:test-dir)
};

declare
    %test:assertTrue
function ft:is-absolute-absolute-path() {
    file:is-absolute(file:temp-dir())
};

declare
    %test:assertFalse
function ft:is-absolute-relative-path() {
    file:is-absolute("relative/path")
};

declare
    %test:assertExists
function ft:last-modified() {
    let $path := $ft:test-dir || "last-mod-test.txt"
    let $_ := file:write-text($path, "test")
    return file:last-modified($path)
};

declare
    %test:assertEquals(5)
function ft:size-file() {
    let $path := $ft:test-dir || "size-test.txt"
    let $_ := file:write-text($path, "hello")
    return file:size($path)
};

declare
    %test:assertEquals(0)
function ft:size-dir() {
    file:size($ft:test-dir)
};

(: ======== Read / Write Text ======== :)

declare
    %test:assertEquals("hello world")
function ft:write-read-text() {
    let $path := $ft:test-dir || "write-read.txt"
    let $_ := file:write-text($path, "hello world")
    return file:read-text($path)
};

declare
    %test:assertEquals("héllo wörld")
function ft:write-read-text-utf8() {
    let $path := $ft:test-dir || "write-read-utf8.txt"
    let $_ := file:write-text($path, "héllo wörld")
    return file:read-text($path)
};

declare
    %test:assertEquals("line1", "line2", "line3")
function ft:write-read-text-lines() {
    let $path := $ft:test-dir || "lines-test.txt"
    let $_ := file:write-text-lines($path, ("line1", "line2", "line3"))
    return file:read-text-lines($path)
};

declare
    %test:assertEquals(0)
function ft:write-text-lines-empty() {
    let $path := $ft:test-dir || "empty-lines.txt"
    let $_ := file:write-text-lines($path, ())
    return count(file:read-text-lines($path))
};

(: Verify newline normalization: CR and CRLF -> LF :)
declare
    %test:assertEquals("a", "b", "c")
function ft:read-text-lines-normalization() {
    let $path := $ft:test-dir || "crlf-test.txt"
    (: Write raw bytes with CRLF line endings :)
    let $_ := file:write-text($path, "a&#13;&#10;b&#13;c")
    return file:read-text-lines($path)
};

(: ======== Read / Write Binary ======== :)

declare
    %test:assertExists
function ft:write-read-binary() {
    let $path := $ft:test-dir || "binary-test.bin"
    let $data := xs:base64Binary("SGVsbG8gV29ybGQ=") (: "Hello World" :)
    let $_ := file:write-binary($path, $data)
    return file:read-binary($path)
};

(: ======== Append ======== :)

declare
    %test:assertEquals("helloworld")
function ft:append-text() {
    let $path := $ft:test-dir || "append-test.txt"
    let $_ := file:write-text($path, "hello")
    let $_ := file:append-text($path, "world")
    return file:read-text($path)
};

declare
    %test:assertEquals("line1", "line2", "line3", "line4")
function ft:append-text-lines() {
    let $path := $ft:test-dir || "append-lines.txt"
    let $_ := file:write-text-lines($path, ("line1", "line2"))
    let $_ := file:append-text-lines($path, ("line3", "line4"))
    return file:read-text-lines($path)
};

(: ======== Directory Operations ======== :)

declare
    %test:assertTrue
function ft:create-dir() {
    let $dir := $ft:test-dir || "subdir/"
    let $_ := file:create-dir($dir)
    return file:is-dir($dir)
};

declare
    %test:assertTrue
function ft:create-dir-nested() {
    let $dir := $ft:test-dir || "a/b/c/"
    let $_ := file:create-dir($dir)
    return file:is-dir($dir)
};

declare
    %test:assertExists
function ft:create-temp-dir() {
    let $dir := file:create-temp-dir("test-", "-dir", $ft:test-dir)
    return
        if (file:is-dir($dir)) then
            $dir
        else
            ()
};

declare
    %test:assertExists
function ft:create-temp-file() {
    let $f := file:create-temp-file("test-", ".tmp", $ft:test-dir)
    return
        if (file:is-file($f)) then
            $f
        else
            ()
};

(: ======== List / Children / Descendants ======== :)

declare
    %test:assertExists
function ft:list-dir() {
    let $_ := file:write-text($ft:test-dir || "list-a.txt", "a")
    let $_ := file:write-text($ft:test-dir || "list-b.txt", "b")
    return file:list($ft:test-dir)
};

declare
    %test:assertTrue
function ft:list-contains-file() {
    let $_ := file:write-text($ft:test-dir || "list-find.txt", "find me")
    return "list-find.txt" = file:list($ft:test-dir)
};

declare
    %test:assertTrue
function ft:list-dir-trailing-separator() {
    let $_ := file:create-dir($ft:test-dir || "list-subdir")
    let $entries := file:list($ft:test-dir)
    return some $e in $entries satisfies
        starts-with($e, "list-subdir") and ends-with($e, file:dir-separator())
};

declare
    %test:assertTrue
function ft:list-recursive() {
    let $_ := file:create-dir($ft:test-dir || "rec-dir")
    let $_ := file:write-text($ft:test-dir || "rec-dir/nested.txt", "nested")
    let $entries := file:list($ft:test-dir, true())
    return some $e in $entries satisfies contains($e, "nested.txt")
};

declare
    %test:assertTrue
function ft:list-pattern() {
    let $_ := file:write-text($ft:test-dir || "pat-a.txt", "a")
    let $_ := file:write-text($ft:test-dir || "pat-b.xml", "b")
    let $entries := file:list($ft:test-dir, false(), "*.txt")
    return
        (some $e in $entries satisfies $e = "pat-a.txt")
        and not(some $e in $entries satisfies $e = "pat-b.xml")
};

declare
    %test:assertExists
function ft:children() {
    let $_ := file:write-text($ft:test-dir || "child.txt", "x")
    return file:children($ft:test-dir)
};

declare
    %test:assertTrue
function ft:children-absolute-paths() {
    let $_ := file:write-text($ft:test-dir || "child-abs.txt", "x")
    let $children := file:children($ft:test-dir)
    return every $c in $children satisfies file:is-absolute($c)
};

declare
    %test:assertExists
function ft:descendants() {
    let $_ := file:create-dir($ft:test-dir || "desc-dir")
    let $_ := file:write-text($ft:test-dir || "desc-dir/deep.txt", "deep")
    return file:descendants($ft:test-dir)
};

declare
    %test:assertExists
function ft:list-roots() {
    file:list-roots()
};

(: ======== Copy / Move / Delete ======== :)

declare
    %test:assertEquals("copy content")
function ft:copy-file() {
    let $src := $ft:test-dir || "copy-src.txt"
    let $dst := $ft:test-dir || "copy-dst.txt"
    let $_ := file:write-text($src, "copy content")
    let $_ := file:copy($src, $dst)
    return file:read-text($dst)
};

declare
    %test:assertTrue
function ft:move-file() {
    let $src := $ft:test-dir || "move-src.txt"
    let $dst := $ft:test-dir || "move-dst.txt"
    let $_ := file:write-text($src, "move content")
    let $_ := file:move($src, $dst)
    return
        file:exists($dst) and not(file:exists($src))
};

declare
    %test:assertFalse
function ft:delete-file() {
    let $path := $ft:test-dir || "delete-me.txt"
    let $_ := file:write-text($path, "bye")
    let $_ := file:delete($path)
    return file:exists($path)
};

declare
    %test:assertFalse
function ft:delete-dir-recursive() {
    let $dir := $ft:test-dir || "delete-dir/"
    let $_ := file:create-dir($dir)
    let $_ := file:write-text($dir || "inner.txt", "inner")
    let $_ := file:delete($dir, true())
    return file:exists($dir)
};

(: ======== Path Functions ======== :)

declare
    %test:assertEquals("file.txt")
function ft:name() {
    file:name("/some/path/file.txt")
};

declare
    %test:assertEquals("")
function ft:name-root() {
    file:name("/")
};

declare
    %test:assertExists
function ft:parent() {
    file:parent("/some/path/file.txt")
};

declare
    %test:assertEmpty
function ft:parent-root() {
    file:parent("/")
};

declare
    %test:assertTrue
function ft:parent-ends-with-separator() {
    ends-with(file:parent("/some/path/file.txt"), file:dir-separator())
};

declare
    %test:assertExists
function ft:path-to-native() {
    (: temp-dir definitely exists :)
    file:path-to-native(file:temp-dir())
};

declare
    %test:assertTrue
function ft:path-to-uri-starts-with-file() {
    starts-with(string(file:path-to-uri("/tmp")), "file:/")
};

declare
    %test:assertTrue
function ft:resolve-path-absolute() {
    let $resolved := file:resolve-path("relative")
    return file:is-absolute($resolved)
};

declare
    %test:assertTrue
function ft:resolve-path-with-base() {
    let $resolved := file:resolve-path("child.txt", "/some/base/")
    return contains($resolved, "base") and contains($resolved, "child.txt")
};

(: ======== Serialized Write / Append ======== :)

declare
    %test:assertTrue
function ft:write-xml() {
    let $path := $ft:test-dir || "write-xml.xml"
    let $_ := file:write($path, <root><child>text</child></root>)
    let $content := file:read-text($path)
    return contains($content, "<root>") and contains($content, "text")
};

declare
    %test:assertTrue
function ft:append-xml() {
    let $path := $ft:test-dir || "append-xml.xml"
    let $_ := file:write($path, <first/>)
    let $_ := file:append($path, <second/>)
    let $content := file:read-text($path)
    return contains($content, "first") and contains($content, "second")
};

(: ======== Error Conditions ======== :)

declare
    %test:assertError("file:not-found")
function ft:read-text-not-found() {
    file:read-text($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("file:is-dir")
function ft:read-text-is-dir() {
    file:read-text($ft:test-dir)
};

declare
    %test:assertError("file:not-found")
function ft:last-modified-not-found() {
    file:last-modified($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("file:not-found")
function ft:size-not-found() {
    file:size($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("file:not-found")
function ft:delete-not-found() {
    file:delete($ft:test-dir || "does-not-exist.txt")
};

declare
    %test:assertError("file:not-found")
function ft:children-not-found() {
    file:children($ft:test-dir || "does-not-exist/")
};

declare
    %test:assertError("file:no-dir")
function ft:children-not-dir() {
    let $path := $ft:test-dir || "not-a-dir.txt"
    let $_ := file:write-text($path, "x")
    return file:children($path)
};

declare
    %test:assertError("file:no-dir")
function ft:write-text-no-parent-dir() {
    file:write-text($ft:test-dir || "no-such-parent/file.txt", "hello")
};

declare
    %test:assertError("file:unknown-encoding")
function ft:read-text-bad-encoding() {
    let $path := $ft:test-dir || "encoding-test.txt"
    let $_ := file:write-text($path, "hello")
    return file:read-text($path, "not-a-real-encoding")
};

declare
    %test:assertError("file:not-found")
function ft:path-to-native-not-found() {
    file:path-to-native("/this/path/surely/does/not/exist/anywhere")
};
