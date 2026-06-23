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

(:~
 : A file-backed binary value (file:read-binary -> BinaryValueFromFile) returned nested in a map held by
 : a sequence must remain readable after the function that produced it returns.
 :
 : Regression for the premature-close bug where the general-purpose value sequences' containsReference did
 : not recurse into container items, so the variable-cleanup guard (popLocalVariables -> destroy) failed to
 : see a binary nested in a map inside a sequence and closed its channel while it was still referenced. This
 : is the path the Roaster / existdb-openapi multipart upload tripped (request:get-uploaded-file-data carried
 : as $request?body?file?data, Roaster's form-data binary shape, then stored with xmldb:store).
 :)
module namespace bnm="http://exist-db.org/testsuite/modules/file/binary-nested-in-map";

import module namespace file="http://exist-db.org/xquery/file";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace helper="http://exist-db.org/xquery/test/util/helper" at "resource:util/helper.xqm";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $bnm:suite := "binary-nested-in-map";

(: One directory per run, shared by setUp and the test (helper:get-test-directory embeds a fresh uuid
 : on each call, so it must be computed once here rather than recomputed in each function). :)
declare variable $bnm:directory := helper:get-test-directory($bnm:suite);

declare
    %test:setUp
function bnm:set-up() as empty-sequence() {
    let $_ := helper:setup-fs-extra($bnm:directory)
    return ()
};

declare
    %test:tearDown
function bnm:tear-down() as empty-sequence() {
    helper:clear-suite-fs($bnm:suite)
};

(:~
 : The inner one-iteration FLWOR is load-bearing: it wraps the map in a ValueSequence -- the
 : contextSequence type whose containsReference was buggy. Returning a bare map { ... } yields a MapType,
 : whose containsReference already recurses, so it would NOT exercise the bug.
 :)
declare %private
function bnm:make-nested($path as xs:string) {
    let $b := file:read-binary($path)
    return (for $i in 1 to 1 return map { "data": $b })
};

declare
    %test:assertEquals("SERVER_SECRET=123!")
function bnm:binary-nested-in-map-survives-function-return() {
    util:binary-to-string(bnm:make-nested(concat($bnm:directory, "/.env"))[1]?data)
};
