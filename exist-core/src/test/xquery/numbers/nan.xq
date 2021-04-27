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
xquery version "3.0";

module namespace nan = "http://exist-db.org/xquery/test/nan";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare
    %test:assertFalse
function nan:nan-atomic-eq-nan() {
    number(()) eq number(())
};

declare
    %test:assertFalse
function nan:nan-atomic-eq-1() {
    number(()) eq 1
};

declare
    %test:assertFalse
function nan:nan-atomic-lt-nan() {
    number(()) lt number(())
};

declare
    %test:assertFalse
function nan:nan-atomic-lt-1() {
    number(()) lt 1
};

declare
    %test:assertFalse
function nan:nan-atomic-le-nan() {
    number(()) le number(())
};

declare
    %test:assertFalse
function nan:nan-atomic-le-1() {
    number(()) le 1
};

declare
    %test:assertFalse
function nan:nan-atomic-gt-nan() {
    number(()) gt number(())
};

declare
    %test:assertFalse
function nan:nan-atomic-gt-1() {
    number(()) gt 1
};

declare
    %test:assertFalse
function nan:nan-atomic-ge-nan() {
    number(()) ge number(())
};

declare
    %test:assertFalse
function nan:nan-atomic-ge-1() {
    number(()) ge 1
};

declare
    %test:assertTrue
function nan:nan-atomic-ne-nan() {
    number(()) ne number(())
};

declare
    %test:assertTrue
function nan:nan-atomic-ne-1() {
    number(()) ne 1
};

declare
    %test:assertFalse
function nan:nan-sequence-eq-nan() {
    number(()) = number(())
};

declare
    %test:assertFalse
function nan:nan-sequence-eq-1() {
    number(()) = 1
};

declare
    %test:assertFalse
function nan:nan-sequence-lt-nan() {
    number(()) < number(())
};

declare
    %test:assertFalse
function nan:nan-sequence-lt-1() {
    number(()) < 1
};

declare
    %test:assertFalse
function nan:nan-sequence-le-nan() {
    number(()) <= number(())
};

declare
    %test:assertFalse
function nan:nan-sequence-le-1() {
    number(()) <= 1
};

declare
    %test:assertFalse
function nan:nan-sequence-gt-nan() {
    number(()) > number(())
};

declare
    %test:assertFalse
function nan:nan-sequence-gt-1() {
    number(()) > 1
};

declare
    %test:assertFalse
function nan:nan-sequence-ge-nan() {
    number(()) >= number(())
};

declare
    %test:assertFalse
function nan:nan-sequence-ge-1() {
    number(()) >= 1
};

declare
    %test:assertTrue
function nan:nan-sequence-ne-nan() {
    number(()) != number(())
};

declare
    %test:assertTrue
function nan:nan-sequence-ne-1() {
    number(()) != 1
};
