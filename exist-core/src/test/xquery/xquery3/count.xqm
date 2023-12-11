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

module namespace ct = "http://exist-db.org/xquery/test/count";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare
    %test:assertEquals(
        '<x index1="1">1</x>',
        '<x index1="2">2</x>',
        '<x index1="3">3</x>',
        '<x index1="4">4</x>'
    )
function ct:simple() {
  for $x in 1 to 4
  count $index1
  return
    <x index1="{$index1}">{$x}</x>
};

declare
    %test:assertEquals(
        '<x index1="1">1</x>',
        '<x index1="2">2</x>',
        '<x index1="3">3</x>',
        '<x index1="4">4</x>'
    )
function ct:order-ascending-index1-before() {
  for $x in 1 to 4
  order by $x ascending
  count $index1
  return
    <x index1="{$index1}">{$x}</x>
};

declare
    %test:assertEquals(
        '<x index1="1">1</x>',
        '<x index1="2">2</x>',
        '<x index1="3">3</x>',
        '<x index1="4">4</x>'
    )
function ct:order-ascending-index1-after() {
  for $x in 1 to 4
  count $index1
  order by $x ascending
  return
    <x index1="{$index1}">{$x}</x>
};

declare
    %test:assertEquals(
        '<x index1="1">4</x>',
        '<x index1="2">3</x>',
        '<x index1="3">2</x>',
        '<x index1="4">1</x>'
    )
function ct:order-descending-index1-before() {
  for $x in 1 to 4
  order by $x descending
  count $index1
  return
    <x index1="{$index1}">{$x}</x>
};

declare
    %test:assertEquals(
        '<x index1="4">4</x>',
        '<x index1="3">3</x>',
        '<x index1="2">2</x>',
        '<x index1="1">1</x>'
    )
function ct:order-descending-index1-after() {
  for $x in 1 to 4
  count $index1
  order by $x descending
  return
    <x index1="{$index1}">{$x}</x>
};

declare
%test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
%test:assertEquals(
        '<x index1="2" index2="1">b</x>',
        '<x index1="1" index2="2">a</x>'
    )
function ct:order-alpha-ascending-indexes() {
  for $x in ('a', 'b')
  count $index1
  let $remainder := $index1 mod 2
  order by $remainder, $index1
  count $index2
  return
    <x index1="{$index1}" index2="{$index2}">{$x}</x>
};

declare
    %test:assertEquals(
        '<x index1="1" index2="1">1</x>',
        '<x index1="2" index2="2">2</x>',
        '<x index1="3" index2="3">3</x>',
        '<x index1="4" index2="4">4</x>'
    )
function ct:order-ascending-indexes() {
  for $x in 1 to 4
  count $index1
  order by $x ascending
  count $index2
  return
    <x index1="{$index1}" index2="{$index2}">{$x}</x>
};

declare
    %test:assertEquals(
        '<x index1="4" index2="1">4</x>',
        '<x index1="3" index2="2">3</x>',
        '<x index1="2" index2="3">2</x>',
        '<x index1="1" index2="4">1</x>'
    )
function ct:order-descending-indexes() {
  for $x in 1 to 4
  count $index1
  order by $x descending
  count $index2
  return
    <x index1="{$index1}" index2="{$index2}">{$x}</x>
};

declare
    %test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
    %test:assertEquals(
        '<item index1="3" remainder="0" index2="1"><x>3</x></item>',
        '<item index1="1" remainder="1" index2="2"><x>1</x></item>',
        '<item index1="4" remainder="1" index2="3"><x>4</x></item>',
        '<item index1="2" remainder="2" index2="4"><x>2</x></item>'
    )
function ct:order-non-linear-ascending-indexes() {
  for $x in 1 to 4
  count $index1
  let $remainder := $index1 mod 3
  order by $remainder ascending
  count $index2
  return
    <item index1="{$index1}" remainder="{$remainder}" index2="{$index2}"><x>{$x}</x></item>
};

declare
    %test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
    %test:assertEquals(
        '<item index1="2" remainder="2" index2="1"><x>2</x></item>',
        '<item index1="1" remainder="1" index2="2"><x>1</x></item>',
        '<item index1="4" remainder="1" index2="3"><x>4</x></item>',
        '<item index1="3" remainder="0" index2="4"><x>3</x></item>'
    )
function ct:order-non-linear-descending-indexes() {
  for $x in 1 to 4
  count $index1
  let $remainder := $index1 mod 3
  order by $remainder descending
  count $index2
  return
    <item index1="{$index1}" remainder="{$remainder}" index2="{$index2}"><x>{$x}</x></item>
};

declare
    %test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
    %test:assertEquals(
        '<item index1="3" remainder="0" index2="1"><x>2</x><y>1</y></item>',
        '<item index1="1" remainder="1" index2="2"><x>1</x><y>1</y></item>',
        '<item index1="4" remainder="1" index2="3"><x>2</x><y>2</y></item>',
        '<item index1="2" remainder="2" index2="4"><x>1</x><y>2</y></item>'
    )
function ct:order-ascending-indexes-two-keys() {
  for $x in 1 to 2
  for $y in 1 to 2
  count $index1
  let $remainder := $index1 mod 3
  order by $remainder, $index1
  count $index2
  return
    <item index1="{$index1}" remainder="{$remainder}" index2="{$index2}"><x>{$x}</x><y>{$y}</y></item>
};

declare
    %test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
    %test:assertEquals(
        '<item index1="2" remainder="2" index2="1"><x>1</x><y>2</y></item>',
        '<item index1="4" remainder="1" index2="2"><x>2</x><y>2</y></item>',
        '<item index1="1" remainder="1" index2="3"><x>1</x><y>1</y></item>',
        '<item index1="3" remainder="0" index2="4"><x>2</x><y>1</y></item>'
    )
function ct:order-descending-indexes-two-keys() {
  for $x in 1 to 2
  for $y in 1 to 2
  count $index1
  let $remainder := $index1 mod 3
  order by $remainder descending, $index1 descending
  count $index2
  return
    <item index1="{$index1}" remainder="{$remainder}" index2="{$index2}"><x>{$x}</x><y>{$y}</y></item>
};

declare
    %test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
    %test:assertEquals(
        '<item index1="3" remainder="0" index2="1"><x>2</x><y>1</y></item>',
        '<item index1="4" remainder="1" index2="2"><x>2</x><y>2</y></item>',
        '<item index1="1" remainder="1" index2="3"><x>1</x><y>1</y></item>',
        '<item index1="2" remainder="2" index2="4"><x>1</x><y>2</y></item>'
    )
function ct:order-ascending-descending-indexes-two-keys() {
  for $x in 1 to 2
  for $y in 1 to 2
  count $index1
  let $remainder := $index1 mod 3
  order by $remainder ascending, $index1 descending
  count $index2
  return
    <item index1="{$index1}" remainder="{$remainder}" index2="{$index2}"><x>{$x}</x><y>{$y}</y></item>
};


declare
    %test:pending('Related to failing XQTS test prod-CountClause/count-009, see: https://github.com/eXist-db/exist/pull/4530#issue-1356325345')
    %test:assertEquals(
        '<item index1="2" remainder="2" index2="1"><x>1</x><y>2</y></item>',
        '<item index1="1" remainder="1" index2="2"><x>1</x><y>1</y></item>',
        '<item index1="4" remainder="1" index2="3"><x>2</x><y>2</y></item>',
        '<item index1="3" remainder="0" index2="4"><x>2</x><y>1</y></item>'
    )
function ct:order-descending-ascending-indexes-two-keys() {
  for $x in 1 to 2
  for $y in 1 to 2
  count $index1
  let $remainder := $index1 mod 3
  order by $remainder descending, $index1 ascending
  count $index2
  return
    <item index1="{$index1}" remainder="{$remainder}" index2="{$index2}"><x>{$x}</x><y>{$y}</y></item>
};