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
 : Tests for issue 5052: fn:transform does not resolve relative URIs
 : against the database.
 :
 : Covers:
 : - relative xsl:include / xsl:import hrefs in stylesheets stored in the
 :   database (resolved from the containing collection of the stylesheet)
 : - a relative "stylesheet-location" in a query stored in the database
 :   (resolved from the containing collection of the query, consistent
 :   with fn:doc and transform:transform)
 :
 : @see https://github.com/eXist-db/exist/issues/5052
 :)
module namespace t5052="http://exist-db.org/xquery/test/fn-transform-5052";

import module namespace xmldb="http://exist-db.org/xquery/xmldb";
import module namespace util="http://exist-db.org/xquery/util";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";

declare variable $t5052:coll-name := "fn-transform-5052";
declare variable $t5052:coll := "/db/" || $t5052:coll-name;

declare variable $t5052:plain-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:template match="/"><plain-ok/></xsl:template>
    </xsl:stylesheet>;

declare variable $t5052:included-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:template name="hello"><hello>included</hello></xsl:template>
    </xsl:stylesheet>;

declare variable $t5052:main-include-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:include href="included.xsl"/>
        <xsl:template match="/"><result><xsl:call-template name="hello"/></result></xsl:template>
    </xsl:stylesheet>;

declare variable $t5052:imported-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:template name="greet"><greet>imported</greet></xsl:template>
    </xsl:stylesheet>;

declare variable $t5052:main-import-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:import href="sub/imported.xsl"/>
        <xsl:template match="/"><result><xsl:call-template name="greet"/></result></xsl:template>
    </xsl:stylesheet>;

declare variable $t5052:nested-a-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:include href="nested-b.xsl"/>
    </xsl:stylesheet>;

declare variable $t5052:nested-b-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:template name="deep"><deep>nested</deep></xsl:template>
    </xsl:stylesheet>;

declare variable $t5052:main-nested-xsl :=
    <xsl:stylesheet version="3.0">
        <xsl:include href="sub/nested-a.xsl"/>
        <xsl:template match="/"><result><xsl:call-template name="deep"/></result></xsl:template>
    </xsl:stylesheet>;

(: a query stored in the database, using a stylesheet-location relative to its collection :)
declare variable $t5052:relative-location-xq :=
    'xquery version "3.1";
    fn:transform(map{
        "stylesheet-location": "plain.xsl",
        "source-node": document { <input/> }
    })?output';

(: as above, but with a base-uri declared in the prolog: the collection URI without a
 : trailing slash, as reported in https://github.com/eXist-db/exist/issues/5052 :)
declare variable $t5052:relative-location-base-uri-xq :=
    'xquery version "3.1";
    declare base-uri "/db/fn-transform-5052";
    fn:transform(map{
        "stylesheet-location": "plain.xsl",
        "source-node": document { <input/> }
    })?output';

declare
    %test:setUp
function t5052:setup() {
    xmldb:create-collection("/db", $t5052:coll-name),
    xmldb:create-collection($t5052:coll, "sub"),
    xmldb:store($t5052:coll, "plain.xsl", $t5052:plain-xsl),
    xmldb:store($t5052:coll, "included.xsl", $t5052:included-xsl),
    xmldb:store($t5052:coll, "main-include.xsl", $t5052:main-include-xsl),
    xmldb:store($t5052:coll || "/sub", "imported.xsl", $t5052:imported-xsl),
    xmldb:store($t5052:coll, "main-import.xsl", $t5052:main-import-xsl),
    xmldb:store($t5052:coll || "/sub", "nested-a.xsl", $t5052:nested-a-xsl),
    xmldb:store($t5052:coll || "/sub", "nested-b.xsl", $t5052:nested-b-xsl),
    xmldb:store($t5052:coll, "main-nested.xsl", $t5052:main-nested-xsl),
    xmldb:store($t5052:coll, "relative-location.xq", $t5052:relative-location-xq, "application/xquery"),
    xmldb:store($t5052:coll, "relative-location-base-uri.xq", $t5052:relative-location-base-uri-xq, "application/xquery")
};

declare
    %test:tearDown
function t5052:tearDown() {
    xmldb:remove($t5052:coll)
};

(:~ Control: absolute stylesheet-location without includes works before and after the fix. :)
declare
    %test:assertEquals("<plain-ok/>")
function t5052:absolute-location-no-include() {
    fn:transform(map{
        "stylesheet-location": $t5052:coll || "/plain.xsl",
        "source-node": document { <input/> }
    })?output
};

(:~ Relative xsl:include, resolved from the collection containing the stylesheet. :)
declare
    %test:assertEquals("<result><hello>included</hello></result>")
function t5052:include-relative-same-collection() {
    fn:serialize(fn:transform(map{
        "stylesheet-location": $t5052:coll || "/main-include.xsl",
        "source-node": document { <input/> }
    })?output)
};

(:~ Relative xsl:import into a sub-collection. :)
declare
    %test:assertEquals("<result><greet>imported</greet></result>")
function t5052:import-relative-sub-collection() {
    fn:serialize(fn:transform(map{
        "stylesheet-location": $t5052:coll || "/main-import.xsl",
        "source-node": document { <input/> }
    })?output)
};

(:~ Base URI must be propagated per stylesheet module: sub/nested-a.xsl includes
 : nested-b.xsl which lives next to it in sub/. :)
declare
    %test:assertEquals("<result><deep>nested</deep></result>")
function t5052:include-relative-nested() {
    fn:serialize(fn:transform(map{
        "stylesheet-location": $t5052:coll || "/main-nested.xsl",
        "source-node": document { <input/> }
    })?output)
};

(:~ Relative xsl:include where the stylesheet is passed as a persistent
 : stylesheet-node stored in the database. :)
declare
    %test:assertEquals("<result><hello>included</hello></result>")
function t5052:stylesheet-node-stored-include() {
    fn:serialize(fn:transform(map{
        "stylesheet-node": doc($t5052:coll || "/main-include.xsl"),
        "source-node": document { <input/> }
    })?output)
};

(:~ A query stored in the database resolves a relative stylesheet-location
 : from its own collection (as fn:doc and transform:transform do). :)
declare
    %test:assertEquals("<plain-ok/>")
function t5052:relative-location-stored-query() {
    fn:serialize(util:eval(xs:anyURI($t5052:coll || "/relative-location.xq")))
};

(:~ As reported in issue 5052: a declared base-uri pointing to a collection
 : (no trailing slash) must not lose its last path segment when a relative
 : stylesheet-location is resolved against it. :)
declare
    %test:assertEquals("<plain-ok/>")
function t5052:relative-location-collection-base-uri() {
    fn:serialize(util:eval(xs:anyURI($t5052:coll || "/relative-location-base-uri.xq")))
};
