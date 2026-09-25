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
 : A processing-instruction(NAME) sequence type matches a processing
 : instruction with that target, in instance of and in typeswitch.
 :)
module namespace pint="http://exist-db.org/xquery/test/processing-instruction-name-test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $pint:PI := <r><?target data?></r>/processing-instruction();

declare
    %test:assertEquals("true", "true", "false")
function pint:instance-of() {
    $pint:PI instance of processing-instruction(target),
    $pint:PI instance of processing-instruction("target"),
    $pint:PI instance of processing-instruction(other)
};

declare
    %test:assertEquals("named")
function pint:typeswitch() {
    typeswitch ($pint:PI)
        case processing-instruction(other) return "other"
        case processing-instruction(target) return "named"
        case processing-instruction() return "any"
        default return "no match"
};
