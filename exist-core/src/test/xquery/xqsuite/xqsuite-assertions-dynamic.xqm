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
 : Dynamic Tests for XQSuite assertions.
 :
 : @author Adam Retter
 :)
module namespace xqtad = "http://exist-db.org/xquery/xqsuite/test/assertions-dynamic";

(:~
 : Resource functions to test through XQSuite.
 :)
declare namespace xqtares = "http://exist-db.org/xquery/xqsuite/test/assertions/resources";

import module namespace test = "http://exist-db.org/xquery/xqsuite"
    at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %private
function xqtad:get-resource-function($name as xs:string, $arity as xs:integer) as function() as item()* {
  let $resource-functions :=
      fn:load-xquery-module(
          "http://exist-db.org/xquery/xqsuite/test/assertions/resources",
          map {
            "location-hints": "xqsuite-assertions.resources.xqm.ignore"
          }
      )?functions
  let $resource-function := $resource-functions(xs:QName("xqtares:" || $name))($arity)
  return
    $resource-function
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtad:test-assertTrue-assertion-when-true() {
  let $fn-to-test := xqtad:get-resource-function("assertTrue-assertion-when-true", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertTrue-assertion-when-false() {
  let $fn-to-test := xqtad:get-resource-function("assertTrue-assertion-when-false", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 1, 0)
function xqtad:test-assertTrue-assertion-when-error() {
  let $fn-to-test := xqtad:get-resource-function("assertTrue-assertion-when-error", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertFalse-assertion-when-true() {
  let $fn-to-test := xqtad:get-resource-function("assertFalse-assertion-when-true", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtad:test-assertFalse-assertion-when-false() {
  let $fn-to-test := xqtad:get-resource-function("assertFalse-assertion-when-false", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 1, 0)
function xqtad:test-assertFalse-assertion-when-error() {
  let $fn-to-test := xqtad:get-resource-function("assertFalse-assertion-when-error", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtad:test-assertError-assertion-when-error() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-error", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-unexpected-error() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-unexpected-error", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 0, 0, 0)
function xqtad:test-assertError-assertion-when-expected-error() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-expected-error", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-different-error() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-different-error", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-true() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-true", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-false() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-false", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-empty() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-empty", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-non-empty() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-non-empty", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};


declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-element() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-element", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-string() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-string", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};

declare
    %test:assertEquals(1, 1, 0, 0)
function xqtad:test-assertError-assertion-when-integer() {
  let $fn-to-test := xqtad:get-resource-function("assertError-assertion-when-integer", 0)
  return
    let $testsuite-result as element(testsuite) := test:suite($fn-to-test)/testsuite
    return
        $testsuite-result/(@tests|@failures|@errors|@pending)/xs:integer(.)
};
