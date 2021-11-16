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

module namespace serialize="http://exist-db.org/testsuite/modules/file/serialize";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace file="http://exist-db.org/xquery/file";


declare
    %test:pending("need to mechanism to setup a temporary file to work with")
    %test:assertEquals("datadata", "true", "true", "true")
function serialize:append() {

    let $node-set := text {"data"}
    let $path := system:get-exist-home() || "/test.txt"
    let $parameters := ()
    let $append := true()
    let $remove := file:delete($path)
    let $ser1 := 	file:serialize($node-set, $path, (), false())
    let $ser2 := 	file:serialize($node-set, $path, (), true())
    let $read := file:read($path)
    let $remove := file:delete($path)
    return ($read, $ser1, $ser2, $remove)
};

declare
    %test:pending("need to mechanism to setup a temporary file to work with")
    %test:assertEquals("data", "true", "true", "true")
function serialize:overwrite() {

    let $node-set := text {"data"}
    let $path := system:get-exist-home() || "/test.txt"
    let $parameters := ()
    let $append := true()
    let $remove := file:delete($path)
    let $ser1 := 	file:serialize($node-set, $path, (), false())
    let $ser2 := 	file:serialize($node-set, $path, (), false())
    let $read := file:read($path)
    let $remove := file:delete($path)
    return ($read, $ser1, $ser2, $remove)
};