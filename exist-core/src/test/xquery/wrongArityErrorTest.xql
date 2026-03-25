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
 : Since wrong-arity errors are static (compile-time), we use util:eval
 : to compile the expressions dynamically so the test module itself can load.
 :)
module namespace wat="http://exist-db.org/xquery/test/wrong-arity";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Calling a built-in function with too few arguments should raise XPST0017.
 : util:wait expects 1 argument; calling with 0 should mention the correct arity.
 :)
declare
    %test:assertError("XPST0017")
function wat:builtin-too-few-args() {
    util:eval("util:wait()")
};

(:~
 : Calling a built-in function with too many arguments should raise XPST0017.
 : util:wait expects 1 argument; calling with 2 should mention the correct arity.
 :)
declare
    %test:assertError("XPST0017")
function wat:builtin-too-many-args() {
    util:eval("util:wait(100, 200)")
};

(:~
 : Calling a truly undeclared function should still raise XPST0017.
 :)
declare
    %test:assertError("XPST0017")
function wat:undeclared-function() {
    util:eval("wat:this-function-does-not-exist()")
};

(:~
 : Calling an imported module function with wrong arity should raise XPST0017
 : and mention the available signatures.
 :)
declare
    %test:assertError("XPST0017")
function wat:imported-module-wrong-arity() {
    util:eval("
        import module namespace util='http://exist-db.org/xquery/util'
            at 'java:org.exist.xquery.functions.util.UtilModule';
        util:wait()
    ")
};
