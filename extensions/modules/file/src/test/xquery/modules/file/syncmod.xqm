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

module namespace syncmod="http://exist-db.org/xquery/test/file/syncmod";
import module namespace helper="http://exist-db.org/xquery/test/util/helper" at "resource:util/helper.xqm";
import module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures" at "resource:util/fixtures.xqm";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $syncmod:suite := "sync-modified";

(:
 : Same setup as for basic sync tests in sync.xqm
 : In addition this time two files are modified an hour after ($fixtures:mod-date)
 :)
declare
    %test:setUp
function syncmod:setup() as empty-sequence() {
    helper:setup-db(),
    helper:modify-db-resource($fixtures:child-collection, "test-data.xml"),
    helper:modify-db-resource($fixtures:collection, "test-text.txt")
};

declare
    %test:tearDown
function syncmod:tearDown() {
    helper:clear-db(),
    helper:clear-suite-fs($syncmod:suite)
};

declare
    %test:assertTrue
function syncmod:simple() {
    helper:get-test-directory($syncmod:suite)
    => helper:sync-with-options(())
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertTrue
function syncmod:empty-options() {
    helper:get-test-directory($syncmod:suite)
    => helper:sync-with-options(map{})
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertError
function syncmod:deprecated-options() {
    helper:get-test-directory($syncmod:suite)
    => helper:sync-with-options($fixtures:mod-date)
    => helper:assert-sync-result(map {
        "updated": $fixtures:ALL-UPDATED,
        "deleted": (),
        "fs": $fixtures:ROOT-FS
    })
};

declare
    %test:assertTrue
function syncmod:do-not-prune() {
    helper:get-test-directory($syncmod:suite)
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
function syncmod:prune() {
    helper:get-test-directory($syncmod:suite)
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
function syncmod:prune-with-excludes-matching-none() {
    helper:get-test-directory($syncmod:suite)
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
function syncmod:after() {
    helper:get-test-directory($syncmod:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "after": $fixtures:mod-date })
    => helper:assert-sync-result(map {
        "updated": ("test-text.txt", "test-data.xml"),
        "deleted": (),
        "fs": ("test", ".env", "test-text.txt", "data")
    })
};

(: collections seem to be synced regardless of their content :)
declare
    %test:assertTrue
function syncmod:after-mod-date-2() {
    helper:get-test-directory($syncmod:suite)
    => helper:sync-with-options(map{ "after": $fixtures:mod-date-2 })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": (),
        "fs": ("data") (: TODO: data should not be here! :)
    })
};

declare
    %test:pending("this would only work if exclude patterns would exclude DB resources from syncing")
    %test:assertTrue
function syncmod:exclude-changed-files() {
    helper:get-test-directory($syncmod:suite)
    => helper:sync-with-options(map{ "excludes":("*.txt", "data/*"), "after": $fixtures:mod-date })
    => helper:assert-sync-result(map {
        "updated": (),
        "deleted": (),
        "fs": ()
    })
};

declare
    %test:assertTrue
function syncmod:prune-with-after-and-excludes-matching-none() {
    helper:get-test-directory($syncmod:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{
            "after": $fixtures:mod-date,
            "excludes": "QQQ",
            "prune": true()
        })
    => helper:assert-sync-result(map {
        "updated": ("test-text.txt", "test-data.xml"),
        "deleted": (".env", "test"),
        "fs": ("test-text.txt", "data")
    })
};

declare
    %test:assertTrue
function syncmod:prune-with-after-and-excludes-matching-all() {
    helper:get-test-directory($syncmod:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{
            "after": $fixtures:mod-date,
            "excludes": "**",
            "prune": true()
        })
    => helper:assert-sync-result(map {
        "updated": ("test-text.txt"), (: TODO , "test-data.xml" is missing here :)
        "deleted": (),
        "fs": (".env", "test", "test-text.txt", "data")
    })
};

declare
    %test:assertTrue
function syncmod:prunes-a-directory-with-after() {
    helper:get-test-directory($syncmod:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{ "prune": true(), "excludes": ".*", "after": $fixtures:mod-date })
    => helper:assert-sync-result(map {
        "updated": ("test-text.txt", "test-data.xml"),
        "deleted": ("test", "three.s"),
        "fs": (".env", "test-text.txt", "data")
    })
};

declare
    %test:pending
    %test:assertTrue
function syncmod:prunes-a-file-with-after() {
    helper:get-test-directory($syncmod:suite)
    => helper:setup-fs-extra()
    => helper:sync-with-options(map{
            "after": $fixtures:mod-date,
            "excludes": "test/*",
            "prune": true()
        })
    => helper:assert-sync-result(map {
        "updated": ("test-text.txt", "test-data.xml"),
        "deleted": (".env"),
        "fs": ("test", "test-text.txt", "data")
    })
};
