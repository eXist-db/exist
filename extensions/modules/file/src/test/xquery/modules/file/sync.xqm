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
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        ()
    )
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertTrue
function sync:empty-options-map() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        map{}
    )
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertError
function sync:deprecated-options() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        $fixtures:mod-date
    )
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-1() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        xs:date("2012-12-21")
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-2() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        "2012-12-21T10:12:21"
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-3() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        "lizard"
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-4() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        ""
    )
};

(:
 : TODO(JL) should also report %test:assertError("err:XPTY0004")
 : it is wrapped in org.exist.xquery.XPathException and therefore not recognized
 :)
declare
    %test:assertError
function sync:bad-options-5() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        (1, map{}, "")
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-6() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        map{ "prune": "true" }
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-7() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        map{ "prune": "no" }
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-8() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        map{ "after": 1234325 }
    )
};

declare
    %test:assertError("err:XPTY0004")
function sync:bad-options-9() {
    file:sync(
        $fixtures:collection,
        helper:get-test-directory($sync:suite),
        map{ "excludes": [] }
    )
};

declare
    %test:assertTrue
function sync:do-not-prune() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "prune": false() }
        )
        => helper:assert-sync-result(map {
            "updated": $fixtures:ALL-UPDATED,
            "deleted": (),
            "fs": $fixtures:ROOT-FS-EXTRA
        })
};

declare
    %test:assertTrue
function sync:prune() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "prune": true() }
        )
        => helper:assert-sync-result(map {
            "updated": $fixtures:ALL-UPDATED,
            "deleted": ("test", "three.s", ".env"),
            "fs": $fixtures:ROOT-FS
        })
};

declare
    %test:assertTrue
function sync:prune-with-excludes-matching-none() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "prune": true(), "excludes": "*.txt" }
        )
        => helper:assert-sync-result(map {
            "updated": $fixtures:ALL-UPDATED,
            "deleted": ("test", "three.s", ".env"),
            "fs": $fixtures:ROOT-FS
        })
};

declare
    %test:assertTrue
function sync:after() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "after": $fixtures:mod-date }
        )
        => helper:assert-sync-result(map {
            "updated": (),
            "deleted": (),
            "fs": ($fixtures:EXTRA-DATA, "data") (: TODO: data should not be here! :)
        })
};

declare
    %test:assertTrue
function sync:after-mod-date-2() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "after": $fixtures:mod-date-2 }
        )
        => helper:assert-sync-result(map {
            "updated": (),
            "deleted": (),
            "fs": ($fixtures:EXTRA-DATA, "data") (: TODO: data should not be here! :)
        })
};

declare
    %test:assertTrue
function sync:after-with-excludes() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "after": $fixtures:mod-date, "excludes": ".env" }
        )
        => helper:assert-sync-result(map {
            "updated": (),
            "deleted": (),
            "fs": ($fixtures:EXTRA-DATA, "data") (: TODO: data should not be here! :)
        })
};

declare
    %test:assertTrue
function sync:prune-with-after-and-excludes() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)
    let $_ := (
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

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{
                "after": $fixtures:mod-date,
                "excludes": "*.xq",
                "prune": true()
            }
        )
        => helper:assert-sync-result(map {
            "updated": (),
            "deleted": ("three.s", "test", ".env", "pruned.xql", "readme.md"),
            "fs": ("excluded.xq", "data") (: TODO: data should not be here! :)
        })
};

declare
    %test:assertTrue
function sync:prunes-a-directory() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "prune": true(), "excludes": ".*" }
        )
        => helper:assert-sync-result(map {
            "updated": $fixtures:ALL-UPDATED,
            "deleted": ("test", "three.s"),
            "fs": ($fixtures:ROOT-FS, ".env")
        })
};

declare
    %test:assertTrue
function sync:prunes-a-file() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{ "prune": true(), "excludes": "test" || $helper:path-separator || "*" }
        )
        => helper:assert-sync-result(map {
            "updated": $fixtures:ALL-UPDATED,
            "deleted": (".env"),
            "fs": ($fixtures:ROOT-FS, "test")
        })
};

declare
    %test:assertTrue
function sync:prunes-with-multiple-excludes() {
    let $directory := helper:get-test-directory($sync:suite)
    let $_ := helper:setup-fs-extra($directory)

    return
        file:sync(
            $fixtures:collection,
            $directory,
            map{
                "prune": true(),
                "excludes": (".env", "**" || $helper:path-separator || "three.?")
            }
        )
        => helper:assert-sync-result(map {
            "updated": $fixtures:ALL-UPDATED,
            "deleted": (),
            "fs": ($fixtures:ROOT-FS, ".env", "test")
        })
};

declare
    %test:assertTrue
function sync:twice() {
    let $directory := helper:get-test-directory($sync:suite)
    (:
     : ensure that files on disk are always recognized as newer by waiting one second until
     : syncing to disk, see https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8177809
     :)
    let $_ := util:wait(1000)
    let $_ := file:sync(
        $fixtures:collection,
        $directory,
        ()
    )

    return
        file:sync(
            $fixtures:collection,
            $directory,
            ()
        )
        => helper:assert-sync-result(map {
            "updated": (),
            "deleted": (),
            "fs": $fixtures:ROOT-FS
        })
};
