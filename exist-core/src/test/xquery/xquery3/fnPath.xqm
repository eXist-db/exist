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

module namespace fnp="http://exist-db.org/xquery/test/function_path";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEmpty
function fnp:empty() {
    fn:path()
};

declare
    %test:assertEquals("/Q{http://example.com/one}p[1]/@author")
function fnp:author() {
    let $e := document {
        <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
        Freude, schoner Gotterfunken,<br/>
        Tochter aus Elysium,<br/>
        Wir betreten feuertrunken,<br/>
        Himmlische, dein Heiligtum.</p>
    }
    return fn:path($e/*:p/@author)
};

declare
    %test:assertEquals("/Q{http://example.com/one}p[1]/Q{http://example.com/one}br[2]")
function fnp:br() {
    let $e := document {
        <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
        Freude, schoner Gotterfunken,<br/>
        Tochter aus Elysium,<br/>
        Wir betreten feuertrunken,<br/>
        Himmlische, dein Heiligtum.</p>
    }
    return fn:path($e/*:p/*:br[2])
};

declare
    %test:assertEquals("/")
function fnp:document() {
    let $e := document {
        <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
        Freude, schoner Gotterfunken,<br/>
        Tochter aus Elysium,<br/>
        Wir betreten feuertrunken,<br/>
        Himmlische, dein Heiligtum.</p>
    }
    return fn:path($e)
};

declare
    %test:assertEquals("Q{http://www.w3.org/2005/xpath-functions}root()/Q{}empnr[1]")
function fnp:empnr() {
    let $e := <employee xml:id="ID21256">
                  <empnr>E21256</empnr>
                  <first>John</first>
                  <last>Brown</last>
              </employee>

    return fn:path($e/empnr)
};

declare
    %test:assertEquals("Q{http://www.w3.org/2005/xpath-functions}root()/@Q{http://www.w3.org/XML/1998/namespace}id")
function fnp:id() {
    let $e := <employee xml:id="ID21256">
                  <empnr>E21256</empnr>
                  <first>John</first>
                  <last>Brown</last>
              </employee>

    return fn:path($e/@xml:id)
};

declare
    %test:assertEquals("/Q{http://example.com/one}p[1]/@Q{http://www.w3.org/XML/1998/namespace}lang")
function fnp:lang() {
    let $e := document {
        <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
        Freude, schoner Gotterfunken,<br/>
        Tochter aus Elysium,<br/>
        Wir betreten feuertrunken,<br/>
        Himmlische, dein Heiligtum.</p>
    }
    return fn:path($e/*:p/@xml:lang)
};

declare
    %test:assertEquals("/Q{http://example.com/one}p[1]")
function fnp:p() {
    let $e := document {
        <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
        Freude, schoner Gotterfunken,<br/>
        Tochter aus Elysium,<br/>
        Wir betreten feuertrunken,<br/>
        Himmlische, dein Heiligtum.</p>
    }
    return fn:path($e/*:p)
};

declare
    %test:assertEquals("Q{http://www.w3.org/2005/xpath-functions}root()")
function fnp:root() {
    let $e := <employee xml:id="ID21256">
                  <empnr>E21256</empnr>
                  <first>John</first>
                  <last>Brown</last>
              </employee>

    return fn:path($e)
};

declare
    %test:assertEquals("/Q{http://example.com/one}p[1]/text()[2]")
function fnp:text() {
    let $e := document {
        <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
        Freude, schoner Gotterfunken,<br/>
        Tochter aus Elysium,<br/>
        Wir betreten feuertrunken,<br/>
        Himmlische, dein Heiligtum.</p>
    }
    return fn:path($e//text()[starts-with(normalize-space(), 'Tochter')])
};
