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
 : Regression for #6446: field @expression using self::* or . silently produced no
 : indexed content, or threw XPTY0004 when the expression was passed to a module
 : function with an element()-typed parameter.  Root cause: the context NodeProxy was
 : constructed without a node type (UNKNOWN_NODE_TYPE = -1), so NameTest.isOfType(-1)
 : always returned false for wildcard element tests, and Type.NODE was not accepted as
 : a subtype of element() in function-call argument checking.
 :)
module namespace t = "http://exist-db.org/xquery/lucene/field-expression-context";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $t:COLL      := "/db/test-field-expr-ctx";
declare variable $t:CONF_COLL := "/db/system/config/db/" || substring-after($t:COLL, "/db/");

(:~ Strict element() parameter exposes the XPTY0004 failure path from #6446. :)
declare variable $t:MODULE := ``[xquery version "3.1";
module namespace idx = "http://exist-db.org/xquery/lucene/field-expr-ctx-lib";
declare function idx:get-metadata($root as element(), $field as xs:string) as xs:string {
    normalize-space($root)
};
]``;

declare variable $t:XML := document { <entry><form><orth>hello</orth></form></entry> };

declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <module uri="http://exist-db.org/xquery/lucene/field-expr-ctx-lib"
                        prefix="idx" at="field-expr-ctx-lib.xql"/>
                <text qname="entry">
                    <!-- baselines: worked before fix -->
                    <field name="xdot"          expression="."/>
                    <field name="xname"         expression="self::entry"/>
                    <field name="xchildren"     expression="*"/>
                    <field name="xdotchildren"  expression="./*"/>
                    <!-- self::* regressions (#6446) -->
                    <field name="xself"              expression="self::*"/>
                    <field name="xdotself"           expression="./self::*"/>
                    <field name="xdotselfchild"      expression="./self::*/form"/>
                    <!-- element()-typed module call regressions (#6446) -->
                    <field name="mdot"               expression="idx:get-metadata(., 'x')"/>
                    <field name="mname"              expression="idx:get-metadata(self::entry, 'x')"/>
                    <field name="mself"              expression="idx:get-metadata(self::*, 'x')"/>
                    <field name="mdotself"           expression="idx:get-metadata(./self::*, 'x')"/>
                    <field name="mdotselfchild"      expression="idx:get-metadata(./self::*/form, 'x')"/>
                    <field name="mchildren"          expression="idx:get-metadata(*, 'x')"/>
                    <field name="mdotchildren"       expression="idx:get-metadata(./*, 'x')"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare %test:setUp function t:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"),
               xmldb:create-collection("/db/system/config", "db"))
    let $_ := (xmldb:create-collection("/db", substring-after($t:COLL, "/db/")),
               xmldb:create-collection("/db/system/config/db", substring-after($t:COLL, "/db/")))
    return (
        xmldb:store($t:COLL, "field-expr-ctx-lib.xql", $t:MODULE, "application/xquery"),
        xmldb:store($t:CONF_COLL, "collection.xconf", $t:xconf),
        xmldb:store($t:COLL, "test.xml", $t:XML),
        xmldb:reindex($t:COLL)
    )
};

declare %test:tearDown function t:tearDown() {
    if (xmldb:collection-available($t:COLL)) then xmldb:remove($t:COLL) else (),
    if (xmldb:collection-available($t:CONF_COLL)) then xmldb:remove($t:CONF_COLL) else ()
};

declare function t:indexed($field as xs:string) as xs:boolean {
    exists(collection($t:COLL)//entry[ft:query(., $field || ":(hello)")])
};

(:~ --- baselines --- :)
declare %test:assertTrue function t:xdot()         { t:indexed("xdot") };
declare %test:assertTrue function t:xname()        { t:indexed("xname") };
declare %test:assertTrue function t:xchildren()    { t:indexed("xchildren") };
declare %test:assertTrue function t:xdotchildren() { t:indexed("xdotchildren") };

(:~ --- self::* expression regressions (#6446) --- :)
declare %test:assertTrue function t:xself()         { t:indexed("xself") };
declare %test:assertTrue function t:xdotself()      { t:indexed("xdotself") };
declare %test:assertTrue function t:xdotselfchild() { t:indexed("xdotselfchild") };

(:~ --- element()-typed module call regressions (#6446) --- :)
declare %test:assertTrue function t:mdot()          { t:indexed("mdot") };
declare %test:assertTrue function t:mname()         { t:indexed("mname") };
declare %test:assertTrue function t:mself()         { t:indexed("mself") };
declare %test:assertTrue function t:mdotself()      { t:indexed("mdotself") };
declare %test:assertTrue function t:mdotselfchild() { t:indexed("mdotselfchild") };
declare %test:assertTrue function t:mchildren()     { t:indexed("mchildren") };
declare %test:assertTrue function t:mdotchildren()  { t:indexed("mdotchildren") };
