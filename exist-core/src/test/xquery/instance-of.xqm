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
 : Test for instance of operator.
 :
 : @author Adam Retter
 :)
module namespace io = "http://exist-db.org/xquery/test/instance-of";

import module namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("true", "false", "false", "false", "true", "false")
function io:double() {
  xs:double(1.0) instance of xs:double,
  xs:double(1.0) instance of xs:float,               (: false :)
  xs:double(1.0) instance of xs:decimal,             (: false :)
  xs:double(1.0) instance of xs:integer,             (: false :)
  xs:double(1.0) instance of xs:numeric,
  xs:double(-1.0) instance of xs:nonPositiveInteger  (: false :)
};

declare
    %test:assertEquals("false", "true", "false", "false", "true", "false")
function io:float() {
  xs:float(1.0) instance of xs:double,              (: false :)
  xs:float(1.0) instance of xs:float,
  xs:float(1.0) instance of xs:decimal,             (: false :)
  xs:float(1.0) instance of xs:integer,             (: false :)
  xs:float(1.0) instance of xs:numeric,
  xs:float(-1.0) instance of xs:nonPositiveInteger  (: false :)
};

declare
    %test:assertEquals("false", "false", "true", "false", "true", "false")
function io:decimal() {
  xs:decimal(1.0) instance of xs:double,              (: false :)
  xs:decimal(1.0) instance of xs:float,               (: false :)
  xs:decimal(1.0) instance of xs:decimal,
  xs:decimal(1.0) instance of xs:integer,             (: false :)
  xs:decimal(1.0) instance of xs:numeric,
  xs:decimal(-1.0) instance of xs:nonPositiveInteger  (: false :)
};

declare
    %test:assertEquals("false", "false", "true", "true", "true", "false")
function io:integer() {
  xs:integer(1) instance of xs:double,              (: false :)
  xs:integer(1) instance of xs:float,               (: false :)
  xs:integer(1) instance of xs:decimal,
  xs:integer(1) instance of xs:integer,
  xs:integer(1) instance of xs:numeric,
  xs:integer(-1) instance of xs:nonPositiveInteger  (: false :)
};

declare
    %test:assertEquals("false", "false", "true", "true", "true", "true")
function io:nonPositiveInteger() {
  xs:nonPositiveInteger(-1) instance of xs:double,  (: false :)
  xs:nonPositiveInteger(-1) instance of xs:float,   (: false :)
  xs:nonPositiveInteger(-1) instance of xs:decimal,
  xs:nonPositiveInteger(-1) instance of xs:integer,
  xs:nonPositiveInteger(-1) instance of xs:numeric,
  xs:nonPositiveInteger(-1) instance of xs:nonPositiveInteger
};

declare
    %test:assertEquals("false", "false", "true", "false", "true", "false")
function io:numeric-decimal() {
  xs:numeric(1.0) instance of xs:double,              (: false :)
  xs:numeric(1.0) instance of xs:float,               (: false :)
  xs:numeric(1.0) instance of xs:decimal,             (: true - NOTE(AR) for an explanation of why this is true, see: https://xmlcom.slack.com/archives/C011NLXE4DU/p1676291235309039 :)
  xs:numeric(1.0) instance of xs:integer,             (: false :)
  xs:numeric(1.0) instance of xs:numeric,
  xs:numeric(-1.0) instance of xs:nonPositiveInteger  (: false :)
};

declare
    %test:assertEquals("false", "false", "true", "true", "true", "false")
function io:numeric-integer() {
  xs:numeric(1) instance of xs:double,                (: false :)
  xs:numeric(1) instance of xs:float,                 (: false :)
  xs:numeric(1) instance of xs:decimal,
  xs:numeric(1) instance of xs:integer,
  xs:numeric(1) instance of xs:numeric,
  xs:numeric(-1) instance of xs:nonPositiveInteger    (: false :)
};
