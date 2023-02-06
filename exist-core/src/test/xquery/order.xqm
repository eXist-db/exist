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
xquery version "1.0";

module namespace ord = "http://exist-db.org/test/order";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $ord:items as element(item)+ := (
        <item>
         <ref>e1</ref>
         <msDesc type="Ll"/>
        </item>
        ,
        <item>
         <ref>e2</ref>
         <msDesc type="P"/>
        </item>
        ,
        <item>
         <ref>e3</ref>
         <msDesc type="Ll"/>
        </item>
        ,
        <item>
         <ref>e4</ref>
         <msDesc/>
        </item>
);

declare
    %test:assertEquals("e2", "e1", "e3", "e4")
function ord:default-order-for-empty-sequence-is-empty-least() {
    for $item in $ord:items
    let $sort-condition as xs:boolean? := $item/msDesc/@type eq 'P'
    order by $sort-condition descending
    return
        $item/ref/string()
};

declare
    %test:assertEquals("e2", "e1", "e3", "e4")
function ord:order-empty-sequence-as-empty-least() {
    for $item in $ord:items
    let $sort-condition as xs:boolean? := $item/msDesc/@type eq 'P'
    order by $sort-condition descending empty least
    return
        $item/ref/string()
};

declare
    %test:assertEquals("e4", "e2", "e1", "e3")
function ord:order-empty-sequence-as-empty-greatest() {
    for $item in $ord:items
    let $sort-condition as xs:boolean? := $item/msDesc/@type eq 'P'
    order by $sort-condition descending empty greatest
    return
        $item/ref/string()
};
