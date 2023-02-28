(:
 : Copyright (C) 2014, Evolved Binary Ltd
 :
 : This file was originally ported from FusionDB to eXist-db by
 : Evolved Binary, for the benefit of the eXist-db Open Source community.
 : Only the ported code as it appears in this file, at the time that
 : it was contributed to eXist-db, was re-licensed under The GNU
 : Lesser General Public License v2.1 only for use in eXist-db.
 :
 : This license grant applies only to a snapshot of the code as it
 : appeared when ported, it does not offer or infer any rights to either
 : updates of this source code or access to the original source code.
 :
 : The GNU Lesser General Public License v2.1 only license follows.
 :
 : ---------------------------------------------------------------------
 :
 : Copyright (C) 2014, Evolved Binary Ltd
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; version 2.1.
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
 : Inline Tests for XQSuite assertions.
 :
 : @author Adam Retter
 :)
module namespace xqtai = "http://exist-db.org/xquery/xqsuite/test/assertions-inline";

(:~
 : Resource functions to test through XQSuite.
 :)
import module namespace xqtares = "http://exist-db.org/xquery/xqsuite/test/assertions/resources"
    at "xqsuite-assertions.resources.xqm.ignore";

import module namespace test = "http://exist-db.org/xquery/xqsuite"
    at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtai:test-assertTrue-assertion-when-true() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertTrue-assertion-when-true#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertTrue-assertion-when-false() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertTrue-assertion-when-false#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 1, 0)
function xqtai:test-assertTrue-assertion-when-error() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertTrue-assertion-when-error#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertFalse-assertion-when-true() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertFalse-assertion-when-true#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtai:test-assertFalse-assertion-when-false() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertFalse-assertion-when-false#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 1, 0)
function xqtai:test-assertFalse-assertion-when-error() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertFalse-assertion-when-error#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtai:test-assertError-assertion-when-error() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-error#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-unexpected-error() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-unexpected-error#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtai:test-assertError-assertion-when-expected-error() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-expected-error#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-different-error() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-different-error#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-true() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-true#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-false() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-false#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-empty() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-empty#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-non-empty() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-non-empty#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-element() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-element#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-string() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-string#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtai:test-assertError-assertion-when-integer() {
  let $testsuite-result as element(testsuite) := test:suite(xqtares:assertError-assertion-when-integer#0)/testsuite
  return
      $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};
