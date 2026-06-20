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
 : When a function (item) is invoked with the wrong number of arguments,
 : Function.setArguments raises XPST0017 with a message that must report the
 : number of arguments ACTUALLY supplied, not the function's declared (expected)
 : arity. Regression test: the message previously printed the expected arity in
 : the "Unexpectedly received N parameter(s)" clause and dropped the real count.
 :)
module namespace fae = "http://exist-db.org/xquery/test/function-arity-error";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: a 1-arity function reference invoked with 2 arguments -> "received 2" :)
declare
    %test:assertError("Unexpectedly received 2 parameter")
function fae:too-many-arguments() {
    let $f := fn:abs#1
    return $f(1, 2)
};

(: a 1-arity function reference invoked with 0 arguments -> "received 0" :)
declare
    %test:assertError("Unexpectedly received 0 parameter")
function fae:zero-arguments() {
    let $f := fn:abs#1
    return $f()
};

(: a 2-arity function reference invoked with 1 argument -> "received 1" :)
declare
    %test:assertError("Unexpectedly received 1 parameter")
function fae:too-few-arguments() {
    let $f := fn:contains#2
    return $f("abc")
};
