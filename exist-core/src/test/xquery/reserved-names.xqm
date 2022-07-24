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

module namespace rnt = "http://exist-db.org/test/reserved-names";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare
    %test:assertEquals('<x copy-namespaces="a" empty-sequence="b" schema-element="c"/>')
function rnt:attribute-direct-constructor() {
  <x copy-namespaces="a" empty-sequence="b" schema-element="c"/>
};

declare
    %test:assertEquals('<x copy-namespaces="a" empty-sequence="b" schema-element="c"/>')
function rnt:attribute-partial-computed-constructor() {
  element x {
   attribute copy-namespaces { "a" },
   attribute empty-sequence { "b" },
   attribute schema-element { "c" }
  }
};

declare
    %test:assertEquals('<x copy-namespaces="a" empty-sequence="b" schema-element="c"/>')
function rnt:attribute-complete-computed-constructor() {
  element { "x" } {
   attribute { "copy-namespaces" } { "a" },
   attribute { "empty-sequence" } { "b" },
   attribute { "schema-element" } { "c" }
  }
};
