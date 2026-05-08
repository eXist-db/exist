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
 : Tests for improved error messages when calling functions with wrong arity.
 : See https://github.com/eXist-db/exist/issues/1756
 :
 : XPST0017 is a static error thrown at compile time, which try/catch can
 : only catch when the offending code is wrapped in util:eval -- the eval'd
 : query's compile error then surfaces as a dynamic error in the outer query.
 : Assertions use contains() on key substrings of $err:description rather
 : than asserting the full message text, so the assertions stay stable
 : across changes in surrounding context (e.g. the W3C error preamble).
 :)
module namespace wat="http://exist-db.org/xquery/test/wrong-arity";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Built-in function called with too few arguments raises XPST0017. :)
declare
    %test:assertError("XPST0017")
function wat:builtin-too-few-args() {
    util:eval("util:wait()")
};

(:~ Built-in function called with too many arguments raises XPST0017. :)
declare
    %test:assertError("XPST0017")
function wat:builtin-too-many-args() {
    util:eval("util:wait(100, 200)")
};

(:~ The error message for a wrong-arity call to a built-in names the
 :  function and reports an unexpected argument count. :)
declare %test:assertTrue
function wat:builtin-wrong-arity-message-mentions-function-and-arity() {
    try {
        util:eval("util:wait(100, 200)")
    } catch * {
        contains($err:description, "util:wait") and
        contains($err:description, "argument")
    }
};

(:~ A truly undeclared function still raises XPST0017. :)
declare
    %test:assertError("XPST0017")
function wat:undeclared-function() {
    util:eval("wat:this-function-does-not-exist()")
};

(:~ User-defined function called with wrong arity raises XPST0017
 :  and the message names the function. :)
declare %test:assertTrue
function wat:user-defined-wrong-arity-message-mentions-function() {
    try {
        util:eval("declare function local:f($a) { $a }; local:f(1, 2)")
    } catch * {
        contains($err:description, "local:f")
    }
};

(:~ User-defined function with a typed signature: the message names the
 :  function (declared signature is reported by the improved error path). :)
declare %test:assertTrue
function wat:user-defined-wrong-arity-message-mentions-signature() {
    try {
        util:eval("
            declare function local:f($a as xs:integer) as xs:integer { $a };
            local:f()
        ")
    } catch * {
        contains($err:description, "local:f")
    }
};

(:~ Imported-module wrong arity raises XPST0017. :)
declare
    %test:assertError("XPST0017")
function wat:imported-module-wrong-arity() {
    util:eval("
        import module namespace util='http://exist-db.org/xquery/util'
            at 'java:org.exist.xquery.functions.util.UtilModule';
        util:wait()
    ")
};
