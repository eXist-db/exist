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

module namespace sync="http://exist-db.org/xquery/test/file/sync";
import module namespace helper="http://exist-db.org/xquery/test/util/helper" at "resource:util/helper.xqm";
import module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures" at "resource:util/fixtures.xqm";
import module namespace file="http://exist-db.org/xquery/file";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";
import module namespace util="http://exist-db.org/xquery/util";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $sync:suite := "sync";

declare
    %test:setUp
function sync:setup() as empty-sequence() {
    helper:setup-db()
};

declare
    %test:tearDown
function sync:tear-down() {
    helper:clear-db(),
    helper:clear-suite-fs($sync:suite)
};

declare
    %test:assertTrue
function sync:simple() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(())
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertTrue
function sync:empty-options-map() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(map{})
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertError
function sync:deprecated-options() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options($fixtures:mod-date)
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-1() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(xs:date("2012-12-21"))
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-2() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options("2012-12-21T10:12:21")
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-3() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options("lizard")
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-4() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options("")
};

(:
 : TODO(JL) should also report %test:assertError("err:XPTY0004")
 : it is wrapped in org.exist.xquery.XPathException and therefore not recognized
 :)
declare
    %test:assertError
function sync:bad-options-5() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options((1, map{}, ""))
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-6() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(map{ "prune": "true" })
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-7() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(map{ "prune": "no" })
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-8() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(map{ "after": 1234325 })
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-9() {
    helper:get-test-directory($sync:suite)
    => helper:sync-with-options(map{ "excludes": [] })
};

declare
    %test:assertTrue
function sync:do-not-prune() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "prune": false() })
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS-EXTRA
    })
};

declare
    %test:assertTrue
function sync:prune() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "prune": true() })
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": ("test", "three.s", ".env"),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertTrue
function sync:prune-with-excludes-matching-none() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "prune": true(), "excludes": "*.txt" })
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": ("test", "three.s", ".env"),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertTrue
function sync:after() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "after": $fixtures:mod-date })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": (),
        "fs": ($fixtures:EXTRA-DATA, "data") (: TODO: data should not be here! :)
    })
};

declare
    %test:assertTrue
function sync:after-mod-date-2() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "after": $fixtures:mod-date-2 })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": (),
        "fs": ($fixtures:EXTRA-DATA, "data") (: TODO: data should not be here! :)
    })
};

declare
    %test:assertTrue
function sync:after-with-excludes() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "after": $fixtures:mod-date, "excludes": ".env" })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": (),
        "fs": ($fixtures:EXTRA-DATA, "data") (: TODO: data should not be here! :)
    })
};

declare
    %test:assertTrue
function sync:prune-with-after-and-excludes() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => (function ($directory as xs:string) {
        let $action := (
            file:serialize-binary(
                util:string-to-binary("1"),
                $directory || "/excluded.xq"
            ),
            file:serialize-binary(
                util:string-to-binary("1"),
                $directory || "/pruned.xql"
            ),
            file:serialize-binary(
                util:string-to-binary("oh oh"),
                $directory || "/readme.md"
            )
        )
        return $directory
    })()
    => helper:sync-with-options(map{
        "after": $fixtures:mod-date,
        "excludes": "*.xq",
        "prune": true()
    })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": ("three.s", "test", ".env", "pruned.xql", "readme.md"),
        "fs": ("excluded.xq", "data") (: TODO: data should not be here! :)
    })
};

declare
    %test:assertTrue
function sync:prunes-a-directory() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "prune": true(), "excludes": ".*" })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": ("test", "three.s"),
        "fs": ($fixtures:ROOT-FS, ".env")
    })
};

declare
    %test:pending
    %test:assertTrue
function sync:prunes-a-file() {
    helper:get-test-directory($sync:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "prune": true(), "excludes": "test/*" })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": (".env"),
        "fs": ($fixtures:ROOT-FS, "test")
    })
};
