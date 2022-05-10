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

module namespace fnt="http://exist-db.org/xquery/test/fnnilled";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fnt:simple_node := <node>"1"</node>;

declare
    %test:assertFalse
function fnt:nilled-no-param() {
    fn:nilled()
};

declare
    %test:assertFalse
function fnt:nilled-node-is-false() {
    fn:nilled($fnt:simple_node)
};

declare
    %test:assertExists
function fnt:nilled-node-exists() {
    fn:nilled($fnt:simple_node)
};

declare variable $fnt:empty_seq := ();

declare
    %test:assertFalse
function fnt:nilled-empty-is-false() {
    fn:nilled($fnt:empty_seq)
};

declare
    %test:assertEmpty
function fnt:nilled-empty-is-empty() {
    fn:nilled($fnt:empty_seq)
};

declare variable $fnt:one_element := <node>"just-one-element"</node>;

declare
    %test:assertFalse
function fnt:nilled-single-is-false() {
    fn:nilled($fnt:one_element)
};

declare
    %test:assertExists
function fnt:nilled-single-is-not-empty() {
    fn:nilled($fnt:one_element)
};

declare
    %test:args("one")
    %test:assertError("err:XPTY0004")
function fnt:nilled-string-not-a-node($param) {
    fn:nilled($param)
};

declare variable $fnt:two_elements := ("one","two");

declare
    %test:assertError("XPTY0004")
function fnt:nilled-p2() {
    fn:nilled($fnt:two_elements)
};
