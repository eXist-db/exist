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

module namespace twt = "http://exist-db.org/xquery/test/tumbling-window";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare
    %test:assertEquals("<window>2 4 6</window>", "<window>8 10 12</window>")
function twt:non-overlapping-windows-of-three-items() {
  for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  only end at $e when $e - $s eq 2
  return
    <window>{$w}</window>
};

declare
    %test:assertEquals(4, 10)
function twt:averages-of-non-overlapping-windows-of-three-items() {
  for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  only end at $e when $e - $s eq 2
  return
    fn:avg($w)
};

declare
    %test:assertEquals("<window>2 6</window>", "<window>8 12</window>")
function twt:first-and-last-in-each-window-of-three-items() {
  for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)
  start $first at $s when fn:true()
  only end $last at $e when $e - $s eq 2
  return
    <window>{$first, $last}</window>
};

declare
    %test:assertEquals("<window>2 4 6</window>", "<window>8 10 12</window>", "<window>14</window>")
function twt:non-overlapping-windows-of-upto-three-items-1() {
  for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  end at $e when $e - $s eq 2
  return
    <window>{$w}</window>
};

declare
    %test:assertEquals("<window>2 4 6</window>", "<window>8 10 12</window>", "<window>14</window>")
function twt:non-overlapping-windows-of-upto-three-items-2() {
  for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when $s mod 3 = 1
  return
    <window>{$w}</window>
};

declare
    %test:assertEquals("<window>6 8 10</window>", "<window>12 14</window>")
function twt:non-overlapping-sequences-starting-with-mod3() {
  for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)
  start $first when $first mod 3 = 0
  return
    <window>{$w}</window>
};

declare
    %test:assertError("XPST0008")
function twt:single-step-window-var-in-start-condition() {
  for tumbling window $w in (1, 2, 3)
  start when fn:not(fn:empty($w))
  return
    <window>{$w}</window>
};

declare
    %test:assertError("XPST0008")
function twt:single-step-window-var-in-end-condition() {
  for tumbling window $w in (1, 2, 3)
  start when fn:true()
  end when fn:not(fn:empty($w))
  return
    <window>{$w}</window>
};

declare
    %test:assertEquals(
      '<window start-current-item="a" start-position="4" start-previous-item="a" start-next-item="test">a test of</window>',
      '<window start-current-item="of" start-position="7" start-previous-item="of" start-next-item="windowing">of windowing</window>'
    )
function twt:jw-duplicate-words() {
  let $sentence := "This is a a test of of windowing"
  let $words := fn:tokenize($sentence, " ")
  for tumbling window $window in $words
      start $start-current-item at $start-position previous $start-previous-item next $start-next-item
      when $start-previous-item eq $start-current-item
  return
    <window start-current-item="{$start-current-item}" start-position="{$start-position}" start-previous-item="{$start-previous-item}" start-next-item="{$start-next-item}">{ $window }</window>
};

declare
  %test:assertEmpty
function twt:reserved-word-tumbling() {
  <x/>/@tumbling
};

declare
  %test:assertEmpty
function twt:reserved-word-window() {
  <x/>/@window
};

declare
  %test:assertEmpty
function twt:reserved-word-start() {
  <x/>/@start
};

declare
  %test:assertEmpty
function twt:reserved-word-end() {
  <x/>/@end
};

declare
  %test:assertEmpty
function twt:reserved-word-previous() {
  <x/>/@previous
};

declare
  %test:assertEmpty
function twt:reserved-word-next() {
  <x/>/@next
};

declare
  %test:assertEmpty
function twt:reserved-word-at() {
  <x/>/@at
};

declare
  %test:assertEmpty
function twt:reserved-word-in() {
  <x/>/@in
};

declare
  %test:assertEmpty
function twt:reserved-word-when() {
  <x/>/@when
};

declare
  %test:assertEmpty
function twt:reserved-word-only() {
  <x/>/@only
};