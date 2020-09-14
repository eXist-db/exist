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
  these tests were created because of
  https://github.com/eXist-db/exist/issues/2308
~:)
module namespace boolseq="http://exist-db.org/xquery/xqsuite/boolseq";
declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $boolseq:sequence := (true(), false(), true());

(:~
  wrapping the not() function to
  mitigate the issue with context-item handling in eXistdb
~:)
declare %private function boolseq:wrappedNot($a) { not($a) };

declare
    %test:assertEquals(3)
function boolseq:countAll() {
    count($boolseq:sequence)
};

declare
    %test:assertEquals(2)
function boolseq:countPositivesContextItem() {
    count($boolseq:sequence[.])
};

(:~
  this is the failing issue
~:)
declare
    %test:pending
    %test:assertEquals(1)
function boolseq:countNegativesContextItem() {
    count($boolseq:sequence[not(.)])
};

declare
    %test:assertEquals(1)
function boolseq:countNegativesContextItemWrappedNot() {
    count($boolseq:sequence[boolseq:wrappedNot(.)])
};

declare
    %test:assertEquals(2)
function boolseq:countPositivesExplicitEquals() {
    count($boolseq:sequence[. eq true()])
};

declare
    %test:assertEquals(1)
function boolseq:countNegativesExplicitEquals() {
    count($boolseq:sequence[. eq false()])
};

declare
    %test:assertEquals(2)
function boolseq:countPositivesFold() {
    fold-left($boolseq:sequence, 0,
        function($r, $n) { if ($n) then ($r + 1) else ($r) })
};

declare
    %test:assertEquals(1)
function boolseq:countNegativesFold() {
    fold-left($boolseq:sequence, 0, 
        function($r, $n) { if (not($n)) then ($r + 1) else ($r) })
};

declare
    %test:assertEquals(2)
function boolseq:countPositivesFilter() {
    count(
        filter(
            $boolseq:sequence, function($n) { $n }))
};

declare
    %test:assertEquals(1)
function boolseq:countNegativesFilter() {
    count(
        filter(
            $boolseq:sequence, function($n) { not($n) }))
};
