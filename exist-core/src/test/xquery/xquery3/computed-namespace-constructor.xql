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
xquery version "3.0";

(:~
 : Tests for Computed Namespace Constructors
 :)
module namespace cnc = "http://exist-db.org/xquery/test/computed-namespace-constructors";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace ok="http://place-on-interwebz.com/a-ok";
declare namespace doh="http://also-on-interwebz.com/problem";


declare
    %test:assertError("XQDY0102")
function cnc:cannot-override-no-ns() {
    element root {namespace {""} {"http://also-on-interwebz.com/problem"},
        namespace ok {"http://place-on-interwebz.com/a-ok"},
        for $n in 1 to 3
        return
            element stuff {$n}
    }
};

declare
    %test:assertEquals(3)
function cnc:ns-default-constructor() {
    count(
        element ok:root {namespace {""} {"http://also-on-interwebz.com/problem"},
            namespace ok {"http://place-on-interwebz.com/a-ok"},
            for $n in 1 to 3
            return
                element stuff {$n}
        }/stuff
    )
};
