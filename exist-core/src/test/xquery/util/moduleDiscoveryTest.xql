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

module namespace mdt="http://exist-db.org/xquery/test/module-discovery";
declare namespace test="http://exist-db.org/xquery/xqsuite";

(: Built-in Java modules should be in registered-modules :)
declare
    %test:assertTrue
function mdt:util-is-registered() {
    "http://exist-db.org/xquery/util" = util:registered-modules()
};

(: Mapped XQuery modules should now also be in registered-modules :)
declare
    %test:assertTrue
function mdt:mapped-modules-in-registered() {
    every $uri in util:mapped-modules() satisfies $uri = util:registered-modules()
};

(: registered-modules should contain no duplicates :)
declare
    %test:assertTrue
function mdt:no-duplicates() {
    let $modules := util:registered-modules()
    return count($modules) eq count(distinct-values($modules))
};

(: registered-modules-info returns maps with required keys :)
declare
    %test:assertTrue
function mdt:info-has-required-keys() {
    let $info := util:registered-modules-info()
    return every $m in $info satisfies
        map:contains($m, "uri") and map:contains($m, "prefix") and map:contains($m, "source")
};

(: registered-modules-info sources are valid :)
declare
    %test:assertTrue
function mdt:info-valid-sources() {
    let $info := util:registered-modules-info()
    return every $m in $info satisfies
        $m?source = ("built-in", "package", "mapped")
};

(: registered-modules-info URIs should match registered-modules :)
declare
    %test:assertTrue
function mdt:info-uris-match-registered() {
    let $registered := util:registered-modules()
    let $info-uris := util:registered-modules-info() ! ?uri
    return
        (every $uri in $registered satisfies $uri = $info-uris)
        and
        (every $uri in $info-uris satisfies $uri = $registered)
};

(: Built-in modules should have source "built-in" :)
declare
    %test:assertTrue
function mdt:util-is-built-in() {
    let $info := util:registered-modules-info()
    let $util := $info[?uri = "http://exist-db.org/xquery/util"]
    return $util?source = "built-in"
};
