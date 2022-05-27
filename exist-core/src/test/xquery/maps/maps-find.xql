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

module namespace mft="http://exist-db.org/xquery/test/maps";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:assertEmpty
function mft:find-map-001() {
    let $s := ()
    return array:flatten(map:find($s, 17))
};

declare
    %test:args(1)
    %test:assertEquals("Sunday", "Dimanche")
    %test:args(8)
    %test:assertEmpty
function mft:find-map-004($param) {
    let $m1 := map{1:"Sunday",2:"Monday",3:"Tuesday",4:"Wednesday",5:"Thursday",6:"Friday",7:"Saturday"}
    let $m2 := map{2:"Lundi",3:"Mardi",4:"Mercredi",5:"Jeudi",6:"Vendredi",7:"Samedi",1:"Dimanche"}
    let $s := ($m1,$m2)
    return array:flatten(map:find($s, $param))
};

declare
    %test:args(1)
    %test:assertEquals("Sunday", "Dimanche")
    %test:args(8)
    %test:assertEmpty
function mft:find-map-004-all-values($param) {
    let $m1 := map{1:"Sunday",2:"Monday",3:"Tuesday",4:"Wednesday",5:"Thursday",6:"Friday",7:"Saturday"}
    let $m2 := map{2:"Lundi",3:"Mardi",4:"Mercredi",5:"Jeudi",6:"Vendredi",7:"Samedi",1:"Dimanche"}
    let $s := ($m1,$m2)
    return array:flatten(map:find($s, $param))
};

declare
    %test:args(1)
    %test:assertEquals("Sunday", "Dimanche")
    %test:args(8)
    %test:assertEmpty
function mft:find-map-006-ignore-non-collection-in-seq($param) {
    let $m1 := map{1:"Sunday",2:"Monday",3:"Tuesday",4:"Wednesday",5:"Thursday",6:"Friday",7:"Saturday"}
    let $m2 := map{2:"Lundi",3:"Mardi",4:"Mercredi",5:"Jeudi",6:"Vendredi",7:"Samedi",1:"Dimanche"}
    let $s := ($m1,$m2,1)
    return array:flatten(map:find($s, $param))
};

declare
    %test:args(1)
    %test:assertEquals("Sunday", "Dimanche")
    %test:args(8)
    %test:assertEmpty
function mft:find-map-006-ignore-non-collection-in-array($param) {
    let $m1 := map{1:"Sunday",2:"Monday",3:"Tuesday",4:"Wednesday",5:"Thursday",6:"Friday",7:"Saturday"}
    let $m2 := map{2:"Lundi",3:"Mardi",4:"Mercredi",5:"Jeudi",6:"Vendredi",7:"Samedi",1:"Dimanche"}
    let $s := [$m1,$m2,1]
    return array:flatten(map:find($s, $param))
};

declare
    %test:args(1)
    %test:assertEquals("Sunday", "Dimanche")
    %test:args(8)
    %test:assertEmpty
function mft:find-map-007-inner-maps($param) {
    let $m1 := map{1:"Sunday",2:"Monday",3:"Tuesday",4:"Wednesday",5:"Thursday",6:"Friday",7:"Saturday"}
    let $m2 := map{2:"Lundi",3:"Mardi",4:"Mercredi",5:"Jeudi",6:"Vendredi",7:"Samedi",1:"Dimanche"}
    let $m3 := map{"fr":$m2}
    let $s := [$m1,$m3]
    return array:flatten(map:find($s, $param))
};

declare
    %test:args(6)
    %test:assertEmpty
    %test:args(7)
    %test:assertEquals("Saturday")
function mft:find-map($param) {
    let $m := map { 7 : "Saturday" }
    return array:flatten(map:find($m, $param))
};

declare
    %test:args(6)
    %test:assertEmpty
    %test:args(5)
    %test:assertEquals("Thursday")
function mft:find-seq($param) {
    let $m1 := map { 7 : "Saturday" }
    let $m2 := map { 5 : "Thursday" }
    let $s := ($m1,$m2)
    return array:flatten(map:find($s, $param))
};

declare variable $mft:inner-map := map { 5 : "Thursday", 6: "Friday" };

declare
    %test:args(3)
    %test:assertEmpty
    %test:args(5)
    %test:assertEquals("Thursday")
    %test:args(6)
    %test:assertEquals("Friday")
function mft:find-inner($param) {
    let $s := [map { 7 : "Saturday" }, map { 4 : $mft:inner-map}]
    return array:flatten(map:find($s, $param))
};

declare
    %test:args(3)
    %test:assertFalse
    %test:args(4)
    %test:assertTrue
function mft:find-inner-map($param) {
    let $s := [map { 7 : "Saturday" }, map { 4 : $mft:inner-map}]
    return fn:deep-equal(array:flatten(map:find($s, $param)), $mft:inner-map)
};

(: from the xquery spec :)
declare
    %test:args(0)
    %test:assertEquals('no', 'non', 'nein')
    %test:args(1)
    %test:assertEquals('yes', 'oui', 'ja', 'doch')
    %test:args(2)
    %test:assertEmpty
function mft:find-spec($param) {
    let $responses := [map{0:'no', 1:'yes'}, map{0:'non', 1:'oui'},
                          map{0:'nein', 1:('ja', 'doch')}]
    return array:flatten(map:find($responses, $param))
};

