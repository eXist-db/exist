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
  Tests for simple map operator inside predicates.
  https://github.com/eXist-db/exist/issues/3289

  The expression @*[name() ! contains(., 'DateTime')] crashes with
  "Type error: the sequence cannot be converted into a node set.
  Item type is xs:boolean" when used on persistent (stored) documents.
~:)
module namespace smp="http://exist-db.org/xquery/test/simple-map-predicate";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $smp:collection := 'test-simple-map-predicate';
declare variable $smp:doc := 'sts135.xml';
declare variable $smp:data :=
    <launch>
        <event launchDateTime="2011-07-08T11:29:00"
               landDateTime="2011-07-21T05:57:00"
               type="mission"/>
        <event type="test"/>
    </launch>;

declare
    %test:setUp
function smp:setup() {
    xmldb:create-collection('/db', $smp:collection),
    xmldb:store('/db/' || $smp:collection, $smp:doc, $smp:data)
};

declare
    %test:tearDown
function smp:teardown() {
    xmldb:remove('/db/' || $smp:collection)
};

(:~
  #3289 — @*[name() ! contains(., 'DateTime')] on stored doc crashes with
  "Type error: the sequence cannot be converted into a node set."
~:)
declare
    %test:assertEquals(1)
function smp:persistent-simple-map-contains-in-attr-predicate() {
    let $doc := doc('/db/' || $smp:collection || '/' || $smp:doc)
    return count($doc//*[@*[name() ! contains(., 'DateTime')]])
};

(:~
  Workaround from #3289: contains(name(.), ...) — should always work.
~:)
declare
    %test:assertEquals(1)
function smp:persistent-contains-name-dot() {
    let $doc := doc('/db/' || $smp:collection || '/' || $smp:doc)
    return count($doc//*[@*[contains(name(.), 'DateTime')]])
};

(:~
  Another workaround from #3289: @* ! name()[contains(., ...)]
~:)
declare
    %test:assertEquals(2)
function smp:persistent-attr-map-name-predicate() {
    let $doc := doc('/db/' || $smp:collection || '/' || $smp:doc)
    let $event := $doc//event[1]
    return count($event/@* ! name()[contains(., 'DateTime')])
};

(:~
  Workaround from #3289 comment: @*[name()[contains(., ...)]]
~:)
declare
    %test:assertEquals(1)
function smp:persistent-nested-name-predicate() {
    let $doc := doc('/db/' || $smp:collection || '/' || $smp:doc)
    return count($doc//*[@*[name()[contains(., 'DateTime')]]])
};

(:~
  Workaround from #3289 comment: //@*[name() ! contains(., ...)]/..
~:)
declare
    %test:assertEquals(1)
function smp:persistent-deref-attr-parent() {
    let $doc := doc('/db/' || $smp:collection || '/' || $smp:doc)
    return count($doc//@*[name() ! contains(., 'DateTime')]/..)
};

(:~
  #3289 comment pattern 1: @* ! name() ! contains(., ...) is expected to fail
  per spec when multiple attributes produce multiple booleans, since EBV is
  undefined for a sequence of two or more booleans.
~:)
declare
    %test:assertError("FORG0006")
function smp:persistent-double-map-expected-error() {
    let $doc := doc('/db/' || $smp:collection || '/' || $smp:doc)
    return $doc//*[@* ! name() ! contains(., 'DateTime')]
};

(:~
  In-memory version should also work — same pattern, no stored doc.
~:)
declare
    %test:assertEquals(1)
function smp:inmemory-simple-map-contains-in-attr-predicate() {
    let $doc :=
        <launch>
            <event launchDateTime="2011-07-08T11:29:00"
                   landDateTime="2011-07-21T05:57:00"
                   type="mission"/>
        </launch>
    return count($doc//*[@*[name() ! contains(., 'DateTime')]])
};
