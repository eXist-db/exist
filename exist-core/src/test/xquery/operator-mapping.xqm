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
 : Test for Operator Mapping.
 : See: https://www.w3.org/TR/xquery-31/#mapping
 :
 : @author Adam Retter
 :)
module namespace om = "http://exist-db.org/xquery/test/operator-mapping";

import module namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-multiply-numericA-numericB() {
  (xs:numeric(1) * xs:numeric(2)) instance of xs:numeric,
  (xs:numeric(1) * xs:numeric(2)) instance of xs:integer,
  (xs:numeric(1) * xs:numeric(2)) instance of xs:decimal,
  (xs:numeric(1) * xs:numeric(2)) instance of xs:float,
  (xs:numeric(1) * xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-multiply-numericA-integerB() {
  (xs:numeric(1) * xs:integer(2)) instance of xs:numeric,
  (xs:numeric(1) * xs:integer(2)) instance of xs:integer,
  (xs:numeric(1) * xs:integer(2)) instance of xs:decimal,
  (xs:numeric(1) * xs:integer(2)) instance of xs:float,
  (xs:numeric(1) * xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-multiply-numericA-decimalB() {
  (xs:numeric(1) * xs:decimal(2.0)) instance of xs:numeric,
  (xs:numeric(1) * xs:decimal(2.0)) instance of xs:integer,
  (xs:numeric(1) * xs:decimal(2.0)) instance of xs:decimal,
  (xs:numeric(1) * xs:decimal(2.0)) instance of xs:float,
  (xs:numeric(1) * xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-numericA-floatB() {
  (xs:numeric(1) * xs:float(2.0)) instance of xs:numeric,
  (xs:numeric(1) * xs:float(2.0)) instance of xs:integer,
  (xs:numeric(1) * xs:float(2.0)) instance of xs:decimal,
  (xs:numeric(1) * xs:float(2.0)) instance of xs:float,
  (xs:numeric(1) * xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-numericA-doubleB() {
  (xs:numeric(1) * xs:double(2.0)) instance of xs:numeric,
  (xs:numeric(1) * xs:double(2.0)) instance of xs:integer,
  (xs:numeric(1) * xs:double(2.0)) instance of xs:decimal,
  (xs:numeric(1) * xs:double(2.0)) instance of xs:float,
  (xs:numeric(1) * xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-multiply-integerA-numericB() {
  (xs:integer(1) * xs:numeric(2)) instance of xs:numeric,
  (xs:integer(1) * xs:numeric(2)) instance of xs:integer,
  (xs:integer(1) * xs:numeric(2)) instance of xs:decimal,
  (xs:integer(1) * xs:numeric(2)) instance of xs:float,
  (xs:integer(1) * xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-multiply-integerA-integerB() {
  (xs:integer(1) * xs:integer(2)) instance of xs:numeric,
  (xs:integer(1) * xs:integer(2)) instance of xs:integer,
  (xs:integer(1) * xs:integer(2)) instance of xs:decimal,
  (xs:integer(1) * xs:integer(2)) instance of xs:float,
  (xs:integer(1) * xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-multiply-integerA-decimalB() {
  (xs:integer(1) * xs:decimal(2.0)) instance of xs:numeric,
  (xs:integer(1) * xs:decimal(2.0)) instance of xs:integer,
  (xs:integer(1) * xs:decimal(2.0)) instance of xs:decimal,
  (xs:integer(1) * xs:decimal(2.0)) instance of xs:float,
  (xs:integer(1) * xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-integerA-floatB() {
  (xs:integer(1) * xs:float(2.0)) instance of xs:numeric,
  (xs:integer(1) * xs:float(2.0)) instance of xs:integer,
  (xs:integer(1) * xs:float(2.0)) instance of xs:decimal,
  (xs:integer(1) * xs:float(2.0)) instance of xs:float,
  (xs:integer(1) * xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-integerA-doubleB() {
  (xs:integer(1) * xs:double(2.0)) instance of xs:numeric,
  (xs:integer(1) * xs:double(2.0)) instance of xs:integer,
  (xs:integer(1) * xs:double(2.0)) instance of xs:decimal,
  (xs:integer(1) * xs:double(2.0)) instance of xs:float,
  (xs:integer(1) * xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-multiply-decimalA-numericB() {
  (xs:decimal(1.0) * xs:numeric(2)) instance of xs:numeric,
  (xs:decimal(1.0) * xs:numeric(2)) instance of xs:integer,
  (xs:decimal(1.0) * xs:numeric(2)) instance of xs:decimal,
  (xs:decimal(1.0) * xs:numeric(2)) instance of xs:float,
  (xs:decimal(1.0) * xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-multiply-decimalA-integerB() {
  (xs:decimal(1.0) * xs:integer(2)) instance of xs:numeric,
  (xs:decimal(1.0) * xs:integer(2)) instance of xs:integer,
  (xs:decimal(1.0) * xs:integer(2)) instance of xs:decimal,
  (xs:decimal(1.0) * xs:integer(2)) instance of xs:float,
  (xs:decimal(1.0) * xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-multiply-decimalA-decimalB() {
  (xs:decimal(1.0) * xs:decimal(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) * xs:decimal(2.0)) instance of xs:integer,
  (xs:decimal(1.0) * xs:decimal(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) * xs:decimal(2.0)) instance of xs:float,
  (xs:decimal(1.0) * xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-decimalA-floatB() {
  (xs:decimal(1.0) * xs:float(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) * xs:float(2.0)) instance of xs:integer,
  (xs:decimal(1.0) * xs:float(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) * xs:float(2.0)) instance of xs:float,
  (xs:decimal(1.0) * xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-decimalA-doubleB() {
  (xs:decimal(1.0) * xs:double(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) * xs:double(2.0)) instance of xs:integer,
  (xs:decimal(1.0) * xs:double(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) * xs:double(2.0)) instance of xs:float,
  (xs:decimal(1.0) * xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-floatA-numericB() {
  (xs:float(1.0) * xs:numeric(2)) instance of xs:numeric,
  (xs:float(1.0) * xs:numeric(2)) instance of xs:integer,
  (xs:float(1.0) * xs:numeric(2)) instance of xs:decimal,
  (xs:float(1.0) * xs:numeric(2)) instance of xs:float,
  (xs:float(1.0) * xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-floatA-integerB() {
  (xs:float(1.0) * xs:integer(2)) instance of xs:numeric,
  (xs:float(1.0) * xs:integer(2)) instance of xs:integer,
  (xs:float(1.0) * xs:integer(2)) instance of xs:decimal,
  (xs:float(1.0) * xs:integer(2)) instance of xs:float,
  (xs:float(1.0) * xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-floatA-decimalB() {
  (xs:float(1.0) * xs:decimal(2.0)) instance of xs:numeric,
  (xs:float(1.0) * xs:decimal(2.0)) instance of xs:integer,
  (xs:float(1.0) * xs:decimal(2.0)) instance of xs:decimal,
  (xs:float(1.0) * xs:decimal(2.0)) instance of xs:float,
  (xs:float(1.0) * xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-multiply-floatA-floatB() {
  (xs:float(1.0) * xs:float(2.0)) instance of xs:numeric,
  (xs:float(1.0) * xs:float(2.0)) instance of xs:integer,
  (xs:float(1.0) * xs:float(2.0)) instance of xs:decimal,
  (xs:float(1.0) * xs:float(2.0)) instance of xs:float,
  (xs:float(1.0) * xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-floatA-doubleB() {
  (xs:float(1.0) * xs:double(2.0)) instance of xs:numeric,
  (xs:float(1.0) * xs:double(2.0)) instance of xs:integer,
  (xs:float(1.0) * xs:double(2.0)) instance of xs:decimal,
  (xs:float(1.0) * xs:double(2.0)) instance of xs:float,
  (xs:float(1.0) * xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-doubleA-numericB() {
  (xs:double(1.0) * xs:numeric(2)) instance of xs:numeric,
  (xs:double(1.0) * xs:numeric(2)) instance of xs:integer,
  (xs:double(1.0) * xs:numeric(2)) instance of xs:decimal,
  (xs:double(1.0) * xs:numeric(2)) instance of xs:float,
  (xs:double(1.0) * xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-doubleA-integerB() {
  (xs:double(1.0) * xs:integer(2)) instance of xs:numeric,
  (xs:double(1.0) * xs:integer(2)) instance of xs:integer,
  (xs:double(1.0) * xs:integer(2)) instance of xs:decimal,
  (xs:double(1.0) * xs:integer(2)) instance of xs:float,
  (xs:double(1.0) * xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-doubleA-decimalB() {
  (xs:double(1.0) * xs:decimal(2.0)) instance of xs:numeric,
  (xs:double(1.0) * xs:decimal(2.0)) instance of xs:integer,
  (xs:double(1.0) * xs:decimal(2.0)) instance of xs:decimal,
  (xs:double(1.0) * xs:decimal(2.0)) instance of xs:float,
  (xs:double(1.0) * xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-doubleA-floatB() {
  (xs:double(1.0) * xs:float(2.0)) instance of xs:numeric,
  (xs:double(1.0) * xs:float(2.0)) instance of xs:integer,
  (xs:double(1.0) * xs:float(2.0)) instance of xs:decimal,
  (xs:double(1.0) * xs:float(2.0)) instance of xs:float,
  (xs:double(1.0) * xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-multiply-doubleA-doubleB() {
  (xs:double(1.0) * xs:double(2.0)) instance of xs:numeric,
  (xs:double(1.0) * xs:double(2.0)) instance of xs:integer,
  (xs:double(1.0) * xs:double(2.0)) instance of xs:decimal,
  (xs:double(1.0) * xs:double(2.0)) instance of xs:float,
  (xs:double(1.0) * xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-numericA-numericB() {
  (xs:numeric(1) div xs:numeric(2)) instance of xs:numeric,
  (xs:numeric(1) div xs:numeric(2)) instance of xs:integer,
  (xs:numeric(1) div xs:numeric(2)) instance of xs:decimal,
  (xs:numeric(1) div xs:numeric(2)) instance of xs:float,
  (xs:numeric(1) div xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-numericA-integerB() {
  (xs:numeric(1) div xs:integer(2)) instance of xs:numeric,
  (xs:numeric(1) div xs:integer(2)) instance of xs:integer,
  (xs:numeric(1) div xs:integer(2)) instance of xs:decimal,
  (xs:numeric(1) div xs:integer(2)) instance of xs:float,
  (xs:numeric(1) div xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-numericA-decimalB() {
  (xs:numeric(1) div xs:decimal(2.0)) instance of xs:numeric,
  (xs:numeric(1) div xs:decimal(2.0)) instance of xs:integer,
  (xs:numeric(1) div xs:decimal(2.0)) instance of xs:decimal,
  (xs:numeric(1) div xs:decimal(2.0)) instance of xs:float,
  (xs:numeric(1) div xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-numericA-floatB() {
  (xs:numeric(1) div xs:float(2.0)) instance of xs:numeric,
  (xs:numeric(1) div xs:float(2.0)) instance of xs:integer,
  (xs:numeric(1) div xs:float(2.0)) instance of xs:decimal,
  (xs:numeric(1) div xs:float(2.0)) instance of xs:float,
  (xs:numeric(1) div xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-numericA-doubleB() {
  (xs:numeric(1) div xs:double(2.0)) instance of xs:numeric,
  (xs:numeric(1) div xs:double(2.0)) instance of xs:integer,
  (xs:numeric(1) div xs:double(2.0)) instance of xs:decimal,
  (xs:numeric(1) div xs:double(2.0)) instance of xs:float,
  (xs:numeric(1) div xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-integerA-numericB() {
  (xs:integer(1) div xs:numeric(2)) instance of xs:numeric,
  (xs:integer(1) div xs:numeric(2)) instance of xs:integer,
  (xs:integer(1) div xs:numeric(2)) instance of xs:decimal,
  (xs:integer(1) div xs:numeric(2)) instance of xs:float,
  (xs:integer(1) div xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-integerA-integerB() {
  (xs:integer(1) div xs:integer(2)) instance of xs:numeric,
  (xs:integer(1) div xs:integer(2)) instance of xs:integer,
  (xs:integer(1) div xs:integer(2)) instance of xs:decimal,
  (xs:integer(1) div xs:integer(2)) instance of xs:float,
  (xs:integer(1) div xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-integerA-decimalB() {
  (xs:integer(1) div xs:decimal(2.0)) instance of xs:numeric,
  (xs:integer(1) div xs:decimal(2.0)) instance of xs:integer,
  (xs:integer(1) div xs:decimal(2.0)) instance of xs:decimal,
  (xs:integer(1) div xs:decimal(2.0)) instance of xs:float,
  (xs:integer(1) div xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-integerA-floatB() {
  (xs:integer(1) div xs:float(2.0)) instance of xs:numeric,
  (xs:integer(1) div xs:float(2.0)) instance of xs:integer,
  (xs:integer(1) div xs:float(2.0)) instance of xs:decimal,
  (xs:integer(1) div xs:float(2.0)) instance of xs:float,
  (xs:integer(1) div xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-integerA-doubleB() {
  (xs:integer(1) div xs:double(2.0)) instance of xs:numeric,
  (xs:integer(1) div xs:double(2.0)) instance of xs:integer,
  (xs:integer(1) div xs:double(2.0)) instance of xs:decimal,
  (xs:integer(1) div xs:double(2.0)) instance of xs:float,
  (xs:integer(1) div xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-decimalA-numericB() {
  (xs:decimal(1.0) div xs:numeric(2)) instance of xs:numeric,
  (xs:decimal(1.0) div xs:numeric(2)) instance of xs:integer,
  (xs:decimal(1.0) div xs:numeric(2)) instance of xs:decimal,
  (xs:decimal(1.0) div xs:numeric(2)) instance of xs:float,
  (xs:decimal(1.0) div xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-decimalA-integerB() {
  (xs:decimal(1.0) div xs:integer(2)) instance of xs:numeric,
  (xs:decimal(1.0) div xs:integer(2)) instance of xs:integer,
  (xs:decimal(1.0) div xs:integer(2)) instance of xs:decimal,
  (xs:decimal(1.0) div xs:integer(2)) instance of xs:float,
  (xs:decimal(1.0) div xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-divide-decimalA-decimalB() {
  (xs:decimal(1.0) div xs:decimal(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) div xs:decimal(2.0)) instance of xs:integer,
  (xs:decimal(1.0) div xs:decimal(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) div xs:decimal(2.0)) instance of xs:float,
  (xs:decimal(1.0) div xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-decimalA-floatB() {
  (xs:decimal(1.0) div xs:float(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) div xs:float(2.0)) instance of xs:integer,
  (xs:decimal(1.0) div xs:float(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) div xs:float(2.0)) instance of xs:float,
  (xs:decimal(1.0) div xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-decimalA-doubleB() {
  (xs:decimal(1.0) div xs:double(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) div xs:double(2.0)) instance of xs:integer,
  (xs:decimal(1.0) div xs:double(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) div xs:double(2.0)) instance of xs:float,
  (xs:decimal(1.0) div xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-floatA-numericB() {
  (xs:float(1.0) div xs:numeric(2)) instance of xs:numeric,
  (xs:float(1.0) div xs:numeric(2)) instance of xs:integer,
  (xs:float(1.0) div xs:numeric(2)) instance of xs:decimal,
  (xs:float(1.0) div xs:numeric(2)) instance of xs:float,
  (xs:float(1.0) div xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-floatA-integerB() {
  (xs:float(1.0) div xs:integer(2)) instance of xs:numeric,
  (xs:float(1.0) div xs:integer(2)) instance of xs:integer,
  (xs:float(1.0) div xs:integer(2)) instance of xs:decimal,
  (xs:float(1.0) div xs:integer(2)) instance of xs:float,
  (xs:float(1.0) div xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-floatA-decimalB() {
  (xs:float(1.0) div xs:decimal(2.0)) instance of xs:numeric,
  (xs:float(1.0) div xs:decimal(2.0)) instance of xs:integer,
  (xs:float(1.0) div xs:decimal(2.0)) instance of xs:decimal,
  (xs:float(1.0) div xs:decimal(2.0)) instance of xs:float,
  (xs:float(1.0) div xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-divide-floatA-floatB() {
  (xs:float(1.0) div xs:float(2.0)) instance of xs:numeric,
  (xs:float(1.0) div xs:float(2.0)) instance of xs:integer,
  (xs:float(1.0) div xs:float(2.0)) instance of xs:decimal,
  (xs:float(1.0) div xs:float(2.0)) instance of xs:float,
  (xs:float(1.0) div xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-floatA-doubleB() {
  (xs:float(1.0) div xs:double(2.0)) instance of xs:numeric,
  (xs:float(1.0) div xs:double(2.0)) instance of xs:integer,
  (xs:float(1.0) div xs:double(2.0)) instance of xs:decimal,
  (xs:float(1.0) div xs:double(2.0)) instance of xs:float,
  (xs:float(1.0) div xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-doubleA-numericB() {
  (xs:double(1.0) div xs:numeric(2)) instance of xs:numeric,
  (xs:double(1.0) div xs:numeric(2)) instance of xs:integer,
  (xs:double(1.0) div xs:numeric(2)) instance of xs:decimal,
  (xs:double(1.0) div xs:numeric(2)) instance of xs:float,
  (xs:double(1.0) div xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-doubleA-integerB() {
  (xs:double(1.0) div xs:integer(2)) instance of xs:numeric,
  (xs:double(1.0) div xs:integer(2)) instance of xs:integer,
  (xs:double(1.0) div xs:integer(2)) instance of xs:decimal,
  (xs:double(1.0) div xs:integer(2)) instance of xs:float,
  (xs:double(1.0) div xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-doubleA-decimalB() {
  (xs:double(1.0) div xs:decimal(2.0)) instance of xs:numeric,
  (xs:double(1.0) div xs:decimal(2.0)) instance of xs:integer,
  (xs:double(1.0) div xs:decimal(2.0)) instance of xs:decimal,
  (xs:double(1.0) div xs:decimal(2.0)) instance of xs:float,
  (xs:double(1.0) div xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-doubleA-floatB() {
  (xs:double(1.0) div xs:float(2.0)) instance of xs:numeric,
  (xs:double(1.0) div xs:float(2.0)) instance of xs:integer,
  (xs:double(1.0) div xs:float(2.0)) instance of xs:decimal,
  (xs:double(1.0) div xs:float(2.0)) instance of xs:float,
  (xs:double(1.0) div xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-divide-doubleA-doubleB() {
  (xs:double(1.0) div xs:double(2.0)) instance of xs:numeric,
  (xs:double(1.0) div xs:double(2.0)) instance of xs:integer,
  (xs:double(1.0) div xs:double(2.0)) instance of xs:decimal,
  (xs:double(1.0) div xs:double(2.0)) instance of xs:float,
  (xs:double(1.0) div xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-numericA-numericB() {
  (xs:numeric(1) idiv xs:numeric(2)) instance of xs:numeric,
  (xs:numeric(1) idiv xs:numeric(2)) instance of xs:integer,
  (xs:numeric(1) idiv xs:numeric(2)) instance of xs:decimal,
  (xs:numeric(1) idiv xs:numeric(2)) instance of xs:float,
  (xs:numeric(1) idiv xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-numericA-integerB() {
  (xs:numeric(1) idiv xs:integer(2)) instance of xs:numeric,
  (xs:numeric(1) idiv xs:integer(2)) instance of xs:integer,
  (xs:numeric(1) idiv xs:integer(2)) instance of xs:decimal,
  (xs:numeric(1) idiv xs:integer(2)) instance of xs:float,
  (xs:numeric(1) idiv xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-numericA-decimalB() {
  (xs:numeric(1) idiv xs:decimal(2.0)) instance of xs:numeric,
  (xs:numeric(1) idiv xs:decimal(2.0)) instance of xs:integer,
  (xs:numeric(1) idiv xs:decimal(2.0)) instance of xs:decimal,
  (xs:numeric(1) idiv xs:decimal(2.0)) instance of xs:float,
  (xs:numeric(1) idiv xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-numericA-floatB() {
  (xs:numeric(1) idiv xs:float(2.0)) instance of xs:numeric,
  (xs:numeric(1) idiv xs:float(2.0)) instance of xs:integer,
  (xs:numeric(1) idiv xs:float(2.0)) instance of xs:decimal,
  (xs:numeric(1) idiv xs:float(2.0)) instance of xs:float,
  (xs:numeric(1) idiv xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-numericA-doubleB() {
  (xs:numeric(1) idiv xs:double(2.0)) instance of xs:numeric,
  (xs:numeric(1) idiv xs:double(2.0)) instance of xs:integer,
  (xs:numeric(1) idiv xs:double(2.0)) instance of xs:decimal,
  (xs:numeric(1) idiv xs:double(2.0)) instance of xs:float,
  (xs:numeric(1) idiv xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-integerA-numericB() {
  (xs:integer(1) idiv xs:numeric(2)) instance of xs:numeric,
  (xs:integer(1) idiv xs:numeric(2)) instance of xs:integer,
  (xs:integer(1) idiv xs:numeric(2)) instance of xs:decimal,
  (xs:integer(1) idiv xs:numeric(2)) instance of xs:float,
  (xs:integer(1) idiv xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-integerA-integerB() {
  (xs:integer(1) idiv xs:integer(2)) instance of xs:numeric,
  (xs:integer(1) idiv xs:integer(2)) instance of xs:integer,
  (xs:integer(1) idiv xs:integer(2)) instance of xs:decimal,
  (xs:integer(1) idiv xs:integer(2)) instance of xs:float,
  (xs:integer(1) idiv xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-integerA-decimalB() {
  (xs:integer(1) idiv xs:decimal(2.0)) instance of xs:numeric,
  (xs:integer(1) idiv xs:decimal(2.0)) instance of xs:integer,
  (xs:integer(1) idiv xs:decimal(2.0)) instance of xs:decimal,
  (xs:integer(1) idiv xs:decimal(2.0)) instance of xs:float,
  (xs:integer(1) idiv xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-integerA-floatB() {
  (xs:integer(1) idiv xs:float(2.0)) instance of xs:numeric,
  (xs:integer(1) idiv xs:float(2.0)) instance of xs:integer,
  (xs:integer(1) idiv xs:float(2.0)) instance of xs:decimal,
  (xs:integer(1) idiv xs:float(2.0)) instance of xs:float,
  (xs:integer(1) idiv xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-integerA-doubleB() {
  (xs:integer(1) idiv xs:double(2.0)) instance of xs:numeric,
  (xs:integer(1) idiv xs:double(2.0)) instance of xs:integer,
  (xs:integer(1) idiv xs:double(2.0)) instance of xs:decimal,
  (xs:integer(1) idiv xs:double(2.0)) instance of xs:float,
  (xs:integer(1) idiv xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-decimalA-numericB() {
  (xs:decimal(1.0) idiv xs:numeric(2)) instance of xs:numeric,
  (xs:decimal(1.0) idiv xs:numeric(2)) instance of xs:integer,
  (xs:decimal(1.0) idiv xs:numeric(2)) instance of xs:decimal,
  (xs:decimal(1.0) idiv xs:numeric(2)) instance of xs:float,
  (xs:decimal(1.0) idiv xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-decimalA-integerB() {
  (xs:decimal(1.0) idiv xs:integer(2)) instance of xs:numeric,
  (xs:decimal(1.0) idiv xs:integer(2)) instance of xs:integer,
  (xs:decimal(1.0) idiv xs:integer(2)) instance of xs:decimal,
  (xs:decimal(1.0) idiv xs:integer(2)) instance of xs:float,
  (xs:decimal(1.0) idiv xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-decimalA-decimalB() {
  (xs:decimal(1.0) idiv xs:decimal(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) idiv xs:decimal(2.0)) instance of xs:integer,
  (xs:decimal(1.0) idiv xs:decimal(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) idiv xs:decimal(2.0)) instance of xs:float,
  (xs:decimal(1.0) idiv xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-decimalA-floatB() {
  (xs:decimal(1.0) idiv xs:float(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) idiv xs:float(2.0)) instance of xs:integer,
  (xs:decimal(1.0) idiv xs:float(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) idiv xs:float(2.0)) instance of xs:float,
  (xs:decimal(1.0) idiv xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-decimalA-doubleB() {
  (xs:decimal(1.0) idiv xs:double(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) idiv xs:double(2.0)) instance of xs:integer,
  (xs:decimal(1.0) idiv xs:double(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) idiv xs:double(2.0)) instance of xs:float,
  (xs:decimal(1.0) idiv xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-floatA-numericB() {
  (xs:float(1.0) idiv xs:numeric(2)) instance of xs:numeric,
  (xs:float(1.0) idiv xs:numeric(2)) instance of xs:integer,
  (xs:float(1.0) idiv xs:numeric(2)) instance of xs:decimal,
  (xs:float(1.0) idiv xs:numeric(2)) instance of xs:float,
  (xs:float(1.0) idiv xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-floatA-integerB() {
  (xs:float(1.0) idiv xs:integer(2)) instance of xs:numeric,
  (xs:float(1.0) idiv xs:integer(2)) instance of xs:integer,
  (xs:float(1.0) idiv xs:integer(2)) instance of xs:decimal,
  (xs:float(1.0) idiv xs:integer(2)) instance of xs:float,
  (xs:float(1.0) idiv xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-floatA-decimalB() {
  (xs:float(1.0) idiv xs:decimal(2.0)) instance of xs:numeric,
  (xs:float(1.0) idiv xs:decimal(2.0)) instance of xs:integer,
  (xs:float(1.0) idiv xs:decimal(2.0)) instance of xs:decimal,
  (xs:float(1.0) idiv xs:decimal(2.0)) instance of xs:float,
  (xs:float(1.0) idiv xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-floatA-floatB() {
  (xs:float(1.0) idiv xs:float(2.0)) instance of xs:numeric,
  (xs:float(1.0) idiv xs:float(2.0)) instance of xs:integer,
  (xs:float(1.0) idiv xs:float(2.0)) instance of xs:decimal,
  (xs:float(1.0) idiv xs:float(2.0)) instance of xs:float,
  (xs:float(1.0) idiv xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-floatA-doubleB() {
  (xs:float(1.0) idiv xs:double(2.0)) instance of xs:numeric,
  (xs:float(1.0) idiv xs:double(2.0)) instance of xs:integer,
  (xs:float(1.0) idiv xs:double(2.0)) instance of xs:decimal,
  (xs:float(1.0) idiv xs:double(2.0)) instance of xs:float,
  (xs:float(1.0) idiv xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-doubleA-numericB() {
  (xs:double(1.0) idiv xs:numeric(2)) instance of xs:numeric,
  (xs:double(1.0) idiv xs:numeric(2)) instance of xs:integer,
  (xs:double(1.0) idiv xs:numeric(2)) instance of xs:decimal,
  (xs:double(1.0) idiv xs:numeric(2)) instance of xs:float,
  (xs:double(1.0) idiv xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-doubleA-integerB() {
  (xs:double(1.0) idiv xs:integer(2)) instance of xs:numeric,
  (xs:double(1.0) idiv xs:integer(2)) instance of xs:integer,
  (xs:double(1.0) idiv xs:integer(2)) instance of xs:decimal,
  (xs:double(1.0) idiv xs:integer(2)) instance of xs:float,
  (xs:double(1.0) idiv xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-doubleA-decimalB() {
  (xs:double(1.0) idiv xs:decimal(2.0)) instance of xs:numeric,
  (xs:double(1.0) idiv xs:decimal(2.0)) instance of xs:integer,
  (xs:double(1.0) idiv xs:decimal(2.0)) instance of xs:decimal,
  (xs:double(1.0) idiv xs:decimal(2.0)) instance of xs:float,
  (xs:double(1.0) idiv xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-doubleA-floatB() {
  (xs:double(1.0) idiv xs:float(2.0)) instance of xs:numeric,
  (xs:double(1.0) idiv xs:float(2.0)) instance of xs:integer,
  (xs:double(1.0) idiv xs:float(2.0)) instance of xs:decimal,
  (xs:double(1.0) idiv xs:float(2.0)) instance of xs:float,
  (xs:double(1.0) idiv xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-integer-divide-doubleA-doubleB() {
  (xs:double(1.0) idiv xs:double(2.0)) instance of xs:numeric,
  (xs:double(1.0) idiv xs:double(2.0)) instance of xs:integer,
  (xs:double(1.0) idiv xs:double(2.0)) instance of xs:decimal,
  (xs:double(1.0) idiv xs:double(2.0)) instance of xs:float,
  (xs:double(1.0) idiv xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-add-numericA-numericB() {
  (xs:numeric(1) + xs:numeric(2)) instance of xs:numeric,
  (xs:numeric(1) + xs:numeric(2)) instance of xs:integer,
  (xs:numeric(1) + xs:numeric(2)) instance of xs:decimal,
  (xs:numeric(1) + xs:numeric(2)) instance of xs:float,
  (xs:numeric(1) + xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-add-numericA-integerB() {
  (xs:numeric(1) + xs:integer(2)) instance of xs:numeric,
  (xs:numeric(1) + xs:integer(2)) instance of xs:integer,
  (xs:numeric(1) + xs:integer(2)) instance of xs:decimal,
  (xs:numeric(1) + xs:integer(2)) instance of xs:float,
  (xs:numeric(1) + xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-add-numericA-decimalB() {
  (xs:numeric(1) + xs:decimal(2.0)) instance of xs:numeric,
  (xs:numeric(1) + xs:decimal(2.0)) instance of xs:integer,
  (xs:numeric(1) + xs:decimal(2.0)) instance of xs:decimal,
  (xs:numeric(1) + xs:decimal(2.0)) instance of xs:float,
  (xs:numeric(1) + xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-numericA-floatB() {
  (xs:numeric(1) + xs:float(2.0)) instance of xs:numeric,
  (xs:numeric(1) + xs:float(2.0)) instance of xs:integer,
  (xs:numeric(1) + xs:float(2.0)) instance of xs:decimal,
  (xs:numeric(1) + xs:float(2.0)) instance of xs:float,
  (xs:numeric(1) + xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-numericA-doubleB() {
  (xs:numeric(1) + xs:double(2.0)) instance of xs:numeric,
  (xs:numeric(1) + xs:double(2.0)) instance of xs:integer,
  (xs:numeric(1) + xs:double(2.0)) instance of xs:decimal,
  (xs:numeric(1) + xs:double(2.0)) instance of xs:float,
  (xs:numeric(1) + xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-add-integerA-numericB() {
  (xs:integer(1) + xs:numeric(2)) instance of xs:numeric,
  (xs:integer(1) + xs:numeric(2)) instance of xs:integer,
  (xs:integer(1) + xs:numeric(2)) instance of xs:decimal,
  (xs:integer(1) + xs:numeric(2)) instance of xs:float,
  (xs:integer(1) + xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-add-integerA-integerB() {
  (xs:integer(1) + xs:integer(2)) instance of xs:numeric,
  (xs:integer(1) + xs:integer(2)) instance of xs:integer,
  (xs:integer(1) + xs:integer(2)) instance of xs:decimal,
  (xs:integer(1) + xs:integer(2)) instance of xs:float,
  (xs:integer(1) + xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-add-integerA-decimalB() {
  (xs:integer(1) + xs:decimal(2.0)) instance of xs:numeric,
  (xs:integer(1) + xs:decimal(2.0)) instance of xs:integer,
  (xs:integer(1) + xs:decimal(2.0)) instance of xs:decimal,
  (xs:integer(1) + xs:decimal(2.0)) instance of xs:float,
  (xs:integer(1) + xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-integerA-floatB() {
  (xs:integer(1) + xs:float(2.0)) instance of xs:numeric,
  (xs:integer(1) + xs:float(2.0)) instance of xs:integer,
  (xs:integer(1) + xs:float(2.0)) instance of xs:decimal,
  (xs:integer(1) + xs:float(2.0)) instance of xs:float,
  (xs:integer(1) + xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-integerA-doubleB() {
  (xs:integer(1) + xs:double(2.0)) instance of xs:numeric,
  (xs:integer(1) + xs:double(2.0)) instance of xs:integer,
  (xs:integer(1) + xs:double(2.0)) instance of xs:decimal,
  (xs:integer(1) + xs:double(2.0)) instance of xs:float,
  (xs:integer(1) + xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-add-decimalA-numericB() {
  (xs:decimal(1.0) + xs:numeric(2)) instance of xs:numeric,
  (xs:decimal(1.0) + xs:numeric(2)) instance of xs:integer,
  (xs:decimal(1.0) + xs:numeric(2)) instance of xs:decimal,
  (xs:decimal(1.0) + xs:numeric(2)) instance of xs:float,
  (xs:decimal(1.0) + xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-add-decimalA-integerB() {
  (xs:decimal(1.0) + xs:integer(2)) instance of xs:numeric,
  (xs:decimal(1.0) + xs:integer(2)) instance of xs:integer,
  (xs:decimal(1.0) + xs:integer(2)) instance of xs:decimal,
  (xs:decimal(1.0) + xs:integer(2)) instance of xs:float,
  (xs:decimal(1.0) + xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-add-decimalA-decimalB() {
  (xs:decimal(1.0) + xs:decimal(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) + xs:decimal(2.0)) instance of xs:integer,
  (xs:decimal(1.0) + xs:decimal(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) + xs:decimal(2.0)) instance of xs:float,
  (xs:decimal(1.0) + xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-decimalA-floatB() {
  (xs:decimal(1.0) + xs:float(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) + xs:float(2.0)) instance of xs:integer,
  (xs:decimal(1.0) + xs:float(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) + xs:float(2.0)) instance of xs:float,
  (xs:decimal(1.0) + xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-decimalA-doubleB() {
  (xs:decimal(1.0) + xs:double(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) + xs:double(2.0)) instance of xs:integer,
  (xs:decimal(1.0) + xs:double(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) + xs:double(2.0)) instance of xs:float,
  (xs:decimal(1.0) + xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-floatA-numericB() {
  (xs:float(1.0) + xs:numeric(2)) instance of xs:numeric,
  (xs:float(1.0) + xs:numeric(2)) instance of xs:integer,
  (xs:float(1.0) + xs:numeric(2)) instance of xs:decimal,
  (xs:float(1.0) + xs:numeric(2)) instance of xs:float,
  (xs:float(1.0) + xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-floatA-integerB() {
  (xs:float(1.0) + xs:integer(2)) instance of xs:numeric,
  (xs:float(1.0) + xs:integer(2)) instance of xs:integer,
  (xs:float(1.0) + xs:integer(2)) instance of xs:decimal,
  (xs:float(1.0) + xs:integer(2)) instance of xs:float,
  (xs:float(1.0) + xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-floatA-decimalB() {
  (xs:float(1.0) + xs:decimal(2.0)) instance of xs:numeric,
  (xs:float(1.0) + xs:decimal(2.0)) instance of xs:integer,
  (xs:float(1.0) + xs:decimal(2.0)) instance of xs:decimal,
  (xs:float(1.0) + xs:decimal(2.0)) instance of xs:float,
  (xs:float(1.0) + xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-add-floatA-floatB() {
  (xs:float(1.0) + xs:float(2.0)) instance of xs:numeric,
  (xs:float(1.0) + xs:float(2.0)) instance of xs:integer,
  (xs:float(1.0) + xs:float(2.0)) instance of xs:decimal,
  (xs:float(1.0) + xs:float(2.0)) instance of xs:float,
  (xs:float(1.0) + xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-floatA-doubleB() {
  (xs:float(1.0) + xs:double(2.0)) instance of xs:numeric,
  (xs:float(1.0) + xs:double(2.0)) instance of xs:integer,
  (xs:float(1.0) + xs:double(2.0)) instance of xs:decimal,
  (xs:float(1.0) + xs:double(2.0)) instance of xs:float,
  (xs:float(1.0) + xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-doubleA-numericB() {
  (xs:double(1.0) + xs:numeric(2)) instance of xs:numeric,
  (xs:double(1.0) + xs:numeric(2)) instance of xs:integer,
  (xs:double(1.0) + xs:numeric(2)) instance of xs:decimal,
  (xs:double(1.0) + xs:numeric(2)) instance of xs:float,
  (xs:double(1.0) + xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-doubleA-integerB() {
  (xs:double(1.0) + xs:integer(2)) instance of xs:numeric,
  (xs:double(1.0) + xs:integer(2)) instance of xs:integer,
  (xs:double(1.0) + xs:integer(2)) instance of xs:decimal,
  (xs:double(1.0) + xs:integer(2)) instance of xs:float,
  (xs:double(1.0) + xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-doubleA-decimalB() {
  (xs:double(1.0) + xs:decimal(2.0)) instance of xs:numeric,
  (xs:double(1.0) + xs:decimal(2.0)) instance of xs:integer,
  (xs:double(1.0) + xs:decimal(2.0)) instance of xs:decimal,
  (xs:double(1.0) + xs:decimal(2.0)) instance of xs:float,
  (xs:double(1.0) + xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-doubleA-floatB() {
  (xs:double(1.0) + xs:float(2.0)) instance of xs:numeric,
  (xs:double(1.0) + xs:float(2.0)) instance of xs:integer,
  (xs:double(1.0) + xs:float(2.0)) instance of xs:decimal,
  (xs:double(1.0) + xs:float(2.0)) instance of xs:float,
  (xs:double(1.0) + xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-add-doubleA-doubleB() {
  (xs:double(1.0) + xs:double(2.0)) instance of xs:numeric,
  (xs:double(1.0) + xs:double(2.0)) instance of xs:integer,
  (xs:double(1.0) + xs:double(2.0)) instance of xs:decimal,
  (xs:double(1.0) + xs:double(2.0)) instance of xs:float,
  (xs:double(1.0) + xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-subtract-numericA-numericB() {
  (xs:numeric(1) - xs:numeric(2)) instance of xs:numeric,
  (xs:numeric(1) - xs:numeric(2)) instance of xs:integer,
  (xs:numeric(1) - xs:numeric(2)) instance of xs:decimal,
  (xs:numeric(1) - xs:numeric(2)) instance of xs:float,
  (xs:numeric(1) - xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-subtract-numericA-integerB() {
  (xs:numeric(1) - xs:integer(2)) instance of xs:numeric,
  (xs:numeric(1) - xs:integer(2)) instance of xs:integer,
  (xs:numeric(1) - xs:integer(2)) instance of xs:decimal,
  (xs:numeric(1) - xs:integer(2)) instance of xs:float,
  (xs:numeric(1) - xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-subtract-numericA-decimalB() {
  (xs:numeric(1) - xs:decimal(2.0)) instance of xs:numeric,
  (xs:numeric(1) - xs:decimal(2.0)) instance of xs:integer,
  (xs:numeric(1) - xs:decimal(2.0)) instance of xs:decimal,
  (xs:numeric(1) - xs:decimal(2.0)) instance of xs:float,
  (xs:numeric(1) - xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-numericA-floatB() {
  (xs:numeric(1) - xs:float(2.0)) instance of xs:numeric,
  (xs:numeric(1) - xs:float(2.0)) instance of xs:integer,
  (xs:numeric(1) - xs:float(2.0)) instance of xs:decimal,
  (xs:numeric(1) - xs:float(2.0)) instance of xs:float,
  (xs:numeric(1) - xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-numericA-doubleB() {
  (xs:numeric(1) - xs:double(2.0)) instance of xs:numeric,
  (xs:numeric(1) - xs:double(2.0)) instance of xs:integer,
  (xs:numeric(1) - xs:double(2.0)) instance of xs:decimal,
  (xs:numeric(1) - xs:double(2.0)) instance of xs:float,
  (xs:numeric(1) - xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-subtract-integerA-numericB() {
  (xs:integer(1) - xs:numeric(2)) instance of xs:numeric,
  (xs:integer(1) - xs:numeric(2)) instance of xs:integer,
  (xs:integer(1) - xs:numeric(2)) instance of xs:decimal,
  (xs:integer(1) - xs:numeric(2)) instance of xs:float,
  (xs:integer(1) - xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "true", "true", "false", "false")
function om:numeric-subtract-integerA-integerB() {
  (xs:integer(1) - xs:integer(2)) instance of xs:numeric,
  (xs:integer(1) - xs:integer(2)) instance of xs:integer,
  (xs:integer(1) - xs:integer(2)) instance of xs:decimal,
  (xs:integer(1) - xs:integer(2)) instance of xs:float,
  (xs:integer(1) - xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-subtract-integerA-decimalB() {
  (xs:integer(1) - xs:decimal(2.0)) instance of xs:numeric,
  (xs:integer(1) - xs:decimal(2.0)) instance of xs:integer,
  (xs:integer(1) - xs:decimal(2.0)) instance of xs:decimal,
  (xs:integer(1) - xs:decimal(2.0)) instance of xs:float,
  (xs:integer(1) - xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-integerA-floatB() {
  (xs:integer(1) - xs:float(2.0)) instance of xs:numeric,
  (xs:integer(1) - xs:float(2.0)) instance of xs:integer,
  (xs:integer(1) - xs:float(2.0)) instance of xs:decimal,
  (xs:integer(1) - xs:float(2.0)) instance of xs:float,
  (xs:integer(1) - xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-integerA-doubleB() {
  (xs:integer(1) - xs:double(2.0)) instance of xs:numeric,
  (xs:integer(1) - xs:double(2.0)) instance of xs:integer,
  (xs:integer(1) - xs:double(2.0)) instance of xs:decimal,
  (xs:integer(1) - xs:double(2.0)) instance of xs:float,
  (xs:integer(1) - xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-subtract-decimalA-numericB() {
  (xs:decimal(1.0) - xs:numeric(2)) instance of xs:numeric,
  (xs:decimal(1.0) - xs:numeric(2)) instance of xs:integer,
  (xs:decimal(1.0) - xs:numeric(2)) instance of xs:decimal,
  (xs:decimal(1.0) - xs:numeric(2)) instance of xs:float,
  (xs:decimal(1.0) - xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-subtract-decimalA-integerB() {
  (xs:decimal(1.0) - xs:integer(2)) instance of xs:numeric,
  (xs:decimal(1.0) - xs:integer(2)) instance of xs:integer,
  (xs:decimal(1.0) - xs:integer(2)) instance of xs:decimal,
  (xs:decimal(1.0) - xs:integer(2)) instance of xs:float,
  (xs:decimal(1.0) - xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "true", "false", "false")
function om:numeric-subtract-decimalA-decimalB() {
  (xs:decimal(1.0) - xs:decimal(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) - xs:decimal(2.0)) instance of xs:integer,
  (xs:decimal(1.0) - xs:decimal(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) - xs:decimal(2.0)) instance of xs:float,
  (xs:decimal(1.0) - xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-decimalA-floatB() {
  (xs:decimal(1.0) - xs:float(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) - xs:float(2.0)) instance of xs:integer,
  (xs:decimal(1.0) - xs:float(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) - xs:float(2.0)) instance of xs:float,
  (xs:decimal(1.0) - xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-decimalA-doubleB() {
  (xs:decimal(1.0) - xs:double(2.0)) instance of xs:numeric,
  (xs:decimal(1.0) - xs:double(2.0)) instance of xs:integer,
  (xs:decimal(1.0) - xs:double(2.0)) instance of xs:decimal,
  (xs:decimal(1.0) - xs:double(2.0)) instance of xs:float,
  (xs:decimal(1.0) - xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-floatA-numericB() {
  (xs:float(1.0) - xs:numeric(2)) instance of xs:numeric,
  (xs:float(1.0) - xs:numeric(2)) instance of xs:integer,
  (xs:float(1.0) - xs:numeric(2)) instance of xs:decimal,
  (xs:float(1.0) - xs:numeric(2)) instance of xs:float,
  (xs:float(1.0) - xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-floatA-integerB() {
  (xs:float(1.0) - xs:integer(2)) instance of xs:numeric,
  (xs:float(1.0) - xs:integer(2)) instance of xs:integer,
  (xs:float(1.0) - xs:integer(2)) instance of xs:decimal,
  (xs:float(1.0) - xs:integer(2)) instance of xs:float,
  (xs:float(1.0) - xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-floatA-decimalB() {
  (xs:float(1.0) - xs:decimal(2.0)) instance of xs:numeric,
  (xs:float(1.0) - xs:decimal(2.0)) instance of xs:integer,
  (xs:float(1.0) - xs:decimal(2.0)) instance of xs:decimal,
  (xs:float(1.0) - xs:decimal(2.0)) instance of xs:float,
  (xs:float(1.0) - xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "true", "false")
function om:numeric-subtract-floatA-floatB() {
  (xs:float(1.0) - xs:float(2.0)) instance of xs:numeric,
  (xs:float(1.0) - xs:float(2.0)) instance of xs:integer,
  (xs:float(1.0) - xs:float(2.0)) instance of xs:decimal,
  (xs:float(1.0) - xs:float(2.0)) instance of xs:float,
  (xs:float(1.0) - xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-floatA-doubleB() {
  (xs:float(1.0) - xs:double(2.0)) instance of xs:numeric,
  (xs:float(1.0) - xs:double(2.0)) instance of xs:integer,
  (xs:float(1.0) - xs:double(2.0)) instance of xs:decimal,
  (xs:float(1.0) - xs:double(2.0)) instance of xs:float,
  (xs:float(1.0) - xs:double(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-doubleA-numericB() {
  (xs:double(1.0) - xs:numeric(2)) instance of xs:numeric,
  (xs:double(1.0) - xs:numeric(2)) instance of xs:integer,
  (xs:double(1.0) - xs:numeric(2)) instance of xs:decimal,
  (xs:double(1.0) - xs:numeric(2)) instance of xs:float,
  (xs:double(1.0) - xs:numeric(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-doubleA-integerB() {
  (xs:double(1.0) - xs:integer(2)) instance of xs:numeric,
  (xs:double(1.0) - xs:integer(2)) instance of xs:integer,
  (xs:double(1.0) - xs:integer(2)) instance of xs:decimal,
  (xs:double(1.0) - xs:integer(2)) instance of xs:float,
  (xs:double(1.0) - xs:integer(2)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-doubleA-decimalB() {
  (xs:double(1.0) - xs:decimal(2.0)) instance of xs:numeric,
  (xs:double(1.0) - xs:decimal(2.0)) instance of xs:integer,
  (xs:double(1.0) - xs:decimal(2.0)) instance of xs:decimal,
  (xs:double(1.0) - xs:decimal(2.0)) instance of xs:float,
  (xs:double(1.0) - xs:decimal(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-doubleA-floatB() {
  (xs:double(1.0) - xs:float(2.0)) instance of xs:numeric,
  (xs:double(1.0) - xs:float(2.0)) instance of xs:integer,
  (xs:double(1.0) - xs:float(2.0)) instance of xs:decimal,
  (xs:double(1.0) - xs:float(2.0)) instance of xs:float,
  (xs:double(1.0) - xs:float(2.0)) instance of xs:double
};

declare
    %test:assertEquals("true", "false", "false", "false", "true")
function om:numeric-subtract-doubleA-doubleB() {
  (xs:double(1.0) - xs:double(2.0)) instance of xs:numeric,
  (xs:double(1.0) - xs:double(2.0)) instance of xs:integer,
  (xs:double(1.0) - xs:double(2.0)) instance of xs:decimal,
  (xs:double(1.0) - xs:double(2.0)) instance of xs:float,
  (xs:double(1.0) - xs:double(2.0)) instance of xs:double
};