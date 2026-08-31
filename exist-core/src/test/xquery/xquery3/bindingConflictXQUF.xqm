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

(: W3C XQUF 3.0 versions of the namespace binding conflict tests.
 : These use copy/modify/return (in-memory) instead of the legacy
 : update syntax (persistent). Separated into its own module because
 : legacy and XQUF syntax cannot be mixed in the same module.
 :)

module namespace utx="http://exist-db.org/xquery/update/xquf-test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare namespace myns="http://www.foo.com";
declare namespace myns2="http://www.foo.net";

(: insert node into a ns with a conflicting ns in parent tree :)
declare %test:assertError("XUDY0023")
function utx:xquf-insert-child-namespaced-attr-conflicted() {
    copy $data := <root xmlns:myns="http://www.bar.com" attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node <child myns:baz="qux"/> into $data/z
    return $data
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertEquals("<z blah=""wah""><child xmlns:myns=""http://www.foo.com"" myns:baz=""qux""/></z>")
function utx:xquf-insert-child-namespaced-attr() {
    copy $data := <root attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node <child myns:baz="qux"/> into $data/z
    return $data/z
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertEquals("<z blah=""wah""><myns:child xmlns:myns=""http://www.foo.com"" baz=""qux""/></z>")
function utx:xquf-insert-namespaced-child() {
    copy $data := <root attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node <myns:child baz="qux"/> into $data/z
    return $data/z
};

declare %test:assertEquals("<z blah=""wah""><myns:child xmlns:myns=""http://www.foo.com"" baz=""qux""><grand xmlns:myns=""http://www.fubar.com""><great xmlns:myns2=""http://www.foo.net"" myns:boz=""chux"" myns2:pip=""dickens""/></grand></myns:child></z>")
function utx:xquf-insert-namespaced-child-deep() {
    copy $data := <root attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node <myns:child baz="qux"><grand xmlns:myns="http://www.fubar.com"><great myns:boz="chux" myns2:pip="dickens"/></grand></myns:child> into $data/z
    return fn:serialize($data/z)
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertError("XUDY0023")
function utx:xquf-insert-namespaced-child-conflicted() {
    copy $data := <root xmlns:myns="http://www.bar.com" attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node <myns:child baz="qux"/> into $data/z
    return $data/z
};

(: insert attr into a ns with a conflicting ns in parent tree :)
declare %test:assertError("XUDY0023")
function utx:xquf-insert-namespaced-attr-conflicted() {
    copy $data := <root xmlns:myns="http://www.bar.com" attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node attribute myns:baz { "qux" } into $data/z
    return $data
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertEquals("<z xmlns:myns=""http://www.foo.com"" blah=""wah"" myns:baz=""qux""/>")
function utx:xquf-insert-namespaced-attr() {
    copy $data := <root attr="1"><!-- foobar --><z blah="wah"/></root>
    modify insert node attribute myns:baz { "qux" } into $data/z
    return $data/z
};
