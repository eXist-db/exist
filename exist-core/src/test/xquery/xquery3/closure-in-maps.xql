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
(:~
 : @see https://github.com/eXist-db/exist/issues/1550
 :)
xquery version "3.1";

module namespace ct="http://exist-db.org/xquery/closures/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $ct:test := "123";

declare variable $ct:model := function($param) {
    let $func := function() {
        $param
    }
    return
        map {
        'test1': $func,
        'test2': $func(),
        'test3': function() {
            $param
        }
        }
};

declare %test:assertEquals('Hello World') function ct:test1-function-reference() as xs:string {
    try {
        $ct:model('Hello World')?test1()
    }
    catch * {$err:code || ' ' || $err:description}
};

declare %test:assertEquals('Hello World') function ct:test2-function-call() as xs:string {
    try {
        $ct:model('Hello World')?test2
    }
    catch * {$err:code || ' ' || $err:description}
};

declare %test:assertEquals('Hello World') function ct:test3-inline-function() as xs:string {
    try {
        $ct:model('Hello World')?test3()
    }
    catch * {$err:code || ' ' || $err:description}
};