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

module namespace ut="http://exist-db.org/xquery/update/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare namespace myns="http://www.foo.com";
declare namespace myns2="http://www.foo.net";

(: insert node into a ns with a conflicting ns in parent tree :)
declare %test:assertError("XUDY0023")
function ut:insert-child-namespaced-attr-conflicted() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root xmlns:myns="http://www.bar.com" attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert <child myns:baz="qux"/> into doc($f)/root/z
    return doc($f)
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertEquals("<z blah=""wah""><child xmlns:myns=""http://www.foo.com"" myns:baz=""qux""/></z>")
function ut:insert-child-namespaced-attr() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert <child myns:baz="qux"/> into doc($f)/root/z
    return doc($f)/root/z
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertEquals("<z blah=""wah""><myns:child xmlns:myns=""http://www.foo.com"" baz=""qux""/></z>")
function ut:insert-namespaced-child() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert <myns:child baz="qux"/> into doc($f)/root/z
    return doc($f)/root/z
};

(: We "manually" redefined xmlns:myns in <grand> -- what does the code see in <great myns:boz="chux"..>, and should we reject it ? :)
(: Do we need to code up the added namespaces, and check conflicts, thus this would XUDY0023 :)
(: or are we content to ignore manual redefinitions :)
declare %test:assertEquals("<z blah=""wah""><myns:child xmlns:myns=""http://www.foo.com"" baz=""qux""><grand xmlns:myns=""http://www.fubar.com""><great xmlns:myns2=""http://www.foo.net"" myns:boz=""chux"" myns2:pip=""dickens""/></grand></myns:child></z>")
function ut:insert-namespaced-child-deep() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert <myns:child baz="qux"><grand xmlns:myns="http://www.fubar.com"><great myns:boz="chux" myns2:pip="dickens"/></grand></myns:child> into doc($f)/root/z
    return fn:serialize(doc($f)/root/z)
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertError("XUDY0023")
function ut:insert-namespaced-child-conflicted() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root xmlns:myns="http://www.bar.com" attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert <myns:child baz="qux"/> into doc($f)/root/z
    return doc($f)/root/z
};

(: insert attr into a ns with a conflicting ns in parent tree :)
declare %test:assertError("XUDY0023")
function ut:insert-namespaced-attr-conflicted() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root xmlns:myns="http://www.bar.com" attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert attribute myns:baz { "qux" } into doc($f)/root/z
    return doc($f)
};

(: insert attr into a ns, but nothing contradictory in the tree - should add ns node :)
declare %test:assertEquals("<z xmlns:myns=""http://www.foo.com"" blah=""wah"" myns:baz=""qux""/>")
function ut:insert-namespaced-attr() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><!-- foobar --><z blah="wah"/></root>)
    let $u  := update insert attribute myns:baz { "qux" } into doc($f)/root/z
    return doc($f)/root/z
};
