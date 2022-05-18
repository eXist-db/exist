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

module namespace testNilled="http://exist-db.org/xquery/test/fnNilled";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $testNilled:simple_node := <node>"1"</node>;

declare
    %test:assertFalse
function testNilled:nilled-no-param() {
    fn:nilled()
};

declare
    %test:assertFalse
function testNilled:nilled-node-is-false() {
    fn:nilled($testNilled:simple_node)
};

declare
    %test:assertExists
function testNilled:nilled-node-exists() {
    fn:nilled($testNilled:simple_node)
};

declare variable $testNilled:empty_seq := ();

declare
    %test:assertFalse
function testNilled:nilled-empty-is-false() {
    fn:nilled($testNilled:empty_seq)
};

declare
    %test:assertEmpty
function testNilled:nilled-empty-is-empty() {
    fn:nilled($testNilled:empty_seq)
};

declare variable $testNilled:one_element := <node>"just-one-element"</node>;

declare
    %test:assertFalse
function testNilled:nilled-single-is-false() {
    fn:nilled($testNilled:one_element)
};

declare
    %test:assertExists
function testNilled:nilled-single-is-not-empty() {
    fn:nilled($testNilled:one_element)
};

declare
    %test:args("one")
    %test:assertError("err:XPTY0004")
function testNilled:nilled-string-not-a-node($param) {
    fn:nilled($param)
};

declare variable $testNilled:two_elements := ("one","two");

declare
    %test:assertError("XPTY0004")
function testNilled:nilled-p2() {
    fn:nilled($testNilled:two_elements)
};
