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
    %test:assertEquals("true")
function mdt:util-is-registered() {
    "http://exist-db.org/xquery/util" = util:registered-modules()
};

(: Every mapped XQuery module should also appear in registered-modules :)
declare
    %test:assertTrue
function mdt:mapped-modules-in-registered() {
    every $uri in util:mapped-modules() satisfies $uri = util:registered-modules()
};

(: registered-modules should contain no duplicates.
 : Returns the number of duplicate entries (0 = pass). :)
declare
    %test:assertEquals(0)
function mdt:no-duplicates() {
    let $modules := util:registered-modules()
    return count($modules) - count(distinct-values($modules))
};

(: Every module info map must have uri, prefix, and source keys.
 : Returns any maps that are missing required keys. :)
declare
    %test:assertEmpty
function mdt:info-missing-keys() {
    let $info := util:registered-modules-info()
    return $info[not(map:contains(., "uri") and map:contains(., "prefix") and map:contains(., "source"))]
};

(: Every module info source must be "built-in", "package", or "mapped".
 : Returns any maps with invalid source values. :)
declare
    %test:assertEmpty
function mdt:info-invalid-sources() {
    let $info := util:registered-modules-info()
    return $info[not(map:get(., "source") = ("built-in", "package", "mapped"))]
};

(: Every URI in registered-modules should appear in registered-modules-info.
 : Returns any registered URIs missing from info. :)
declare
    %test:assertEmpty
function mdt:registered-not-in-info() {
    let $registered := util:registered-modules()
    let $info-uris := for $m in util:registered-modules-info() return map:get($m, "uri")
    return $registered[not(. = $info-uris)]
};

(: Every URI in registered-modules-info should appear in registered-modules.
 : Returns any info URIs missing from registered. :)
declare
    %test:assertEmpty
function mdt:info-not-in-registered() {
    let $registered := util:registered-modules()
    let $info-uris := for $m in util:registered-modules-info() return map:get($m, "uri")
    return $info-uris[not(. = $registered)]
};

(: The util module should have source "built-in" :)
declare
    %test:assertEquals("built-in")
function mdt:util-is-built-in() {
    let $info := util:registered-modules-info()
    let $util := $info[map:get(., "uri") = "http://exist-db.org/xquery/util"]
    return map:get($util, "source")
};
