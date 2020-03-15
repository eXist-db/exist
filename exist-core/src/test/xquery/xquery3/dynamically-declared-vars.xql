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

module namespace devar="http://exist-db.org/xquery/test/declared-variables";
declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $devar:static := 'I am static.';
declare variable $devar:dynamic := replace($devar:static, 'static', 'dynamic');
declare variable $devar:function := (function ($a) {
    replace($a, 'static', 'the return of a function expression')
})($devar:static);

declare variable $devar:arrow := $devar:static
=> replace('static', 'the return of an arrow expression')
;

declare
%test:assertEquals('I am static.')
function devar:read-static () {
    $devar:static
};

declare
%test:assertEquals('I am dynamic.')
function devar:read-dynamic () {
    $devar:dynamic
};

declare
%test:assertEquals('I am the return of a function expression.')
function devar:read-function () {
    $devar:function
};

declare
%test:assertEquals('I am the return of an arrow expression.')
function devar:read-arrow () {
    $devar:arrow
};