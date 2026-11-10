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
 : Field expressions that need the indexed node itself, on nodes far below index-depth.
 :
 : The Lucene worker evaluates a field expression against a node proxy built from the node id it kept while
 : indexing. Without the storage address of the node every such evaluation walks the document from the nearest
 : ancestor that has a DOM B-tree entry, which is quadratic in the number of indexed nodes below index-depth
 : (#6959). The worker now keeps the address with the node id. These tests pin that the values are the same as
 : for any other node, when the node is stored, reindexed, inserted by XUpdate, and when it is an attribute.
 :)
module namespace t = "http://exist-db.org/xquery/lucene/field-expression-deep";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $t:COLL      := "/db/test-field-expr-deep";
declare variable $t:CONF_COLL := "/db/system/config/db/" || substring-after($t:COLL, "/db/");

declare variable $t:MODULE := ``[xquery version "3.1";
module namespace idx = "http://exist-db.org/xquery/lucene/field-expr-deep-lib";
declare function idx:get-metadata($node as element(), $key as xs:string) as xs:string {
    local-name($node) || "_" || $key
};
]``;

(:~ Levels count from the root, TEI being level 1: taxonomy is at level 5, category at 6 to 8 and catDesc at 7 to 9, all below the index-depth of 5 in conf.xml and below the code default of 1. :)
declare variable $t:XML := document {
    <TEI>
        <teiHeader>
            <encodingDesc>
                <classDecl>
                    <taxonomy>
                        <category xml:id="c1" n="a"><catDesc>one</catDesc>
                            <category xml:id="c2" n="b"><catDesc>two</catDesc>
                                <category xml:id="c3" n="c"><catDesc>three</catDesc></category>
                            </category>
                        </category>
                    </taxonomy>
                </classDecl>
            </encodingDesc>
        </teiHeader>
    </TEI>
};

declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <module uri="http://exist-db.org/xquery/lucene/field-expr-deep-lib"
                        prefix="idx" at="field-expr-deep-lib.xql"/>
                <text qname="category">
                    <field name="kind"       expression="local-name(.)"/>
                    <field name="firstchild" expression="local-name(*[1])"/>
                    <field name="nchildren"  expression="count(*)"/>
                    <field name="meta"       expression="idx:get-metadata(., 'k')"/>
                    <field name="parentname" expression="local-name(..)"/>
                </text>
                <text qname="@n">
                    <field name="attrparent" expression="local-name(..)"/>
                    <field name="attrself"   expression="local-name(.)"/>
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
        xmldb:store($t:COLL, "field-expr-deep-lib.xql", $t:MODULE, "application/xquery"),
        xmldb:store($t:CONF_COLL, "collection.xconf", $t:xconf),
        xmldb:store($t:COLL, "test.xml", $t:XML)
    )
};

declare %test:tearDown function t:tearDown() {
    if (xmldb:collection-available($t:COLL)) then xmldb:remove($t:COLL) else (),
    if (xmldb:collection-available($t:CONF_COLL)) then xmldb:remove($t:CONF_COLL) else ()
};

declare function t:ids($query as xs:string) as xs:string* {
    collection($t:COLL)//category[ft:query(., $query)]/@xml:id/string()
};

(:~ --- as stored --- :)
declare %test:assertEquals("c1", "c2", "c3") function t:kind()       { t:ids("kind:category") };
declare %test:assertEquals("c1", "c2", "c3") function t:firstchild() { t:ids("firstchild:catDesc") };
declare %test:assertEquals("c3")             function t:nchildren()  { t:ids("nchildren:1") };
declare %test:assertEquals("c1", "c2", "c3") function t:meta()       { t:ids("meta:category_k") };
declare %test:assertEquals("c1")             function t:parent-taxonomy() { t:ids("parentname:taxonomy") };
declare %test:assertEquals("c2", "c3")       function t:parent-category() { t:ids("parentname:category") };

(:~ --- attributes: the indexed node is the attribute, whose parent is another deep node --- :)
declare %test:assertEquals("c1", "c2", "c3") function t:attribute-parent() {
    collection($t:COLL)//category/@n[ft:query(., "attrparent:category")]/../@xml:id/string()
};
declare %test:assertEquals("c1", "c2", "c3") function t:attribute-self() {
    collection($t:COLL)//category/@n[ft:query(., "attrself:n")]/../@xml:id/string()
};

(:~ --- the same after a reindex --- :)
declare %test:assertEquals("c1", "c2", "c3") function t:reindexed-kind() {
    let $_ := xmldb:reindex($t:COLL) return t:ids("kind:category")
};
declare %test:assertEquals("c1") function t:reindexed-parent() {
    let $_ := xmldb:reindex($t:COLL) return t:ids("parentname:taxonomy")
};

(:~ --- a node inserted by XUpdate is indexed with the same values; the fixture is shared, so the node is removed again even when the query fails --- :)
declare %test:assertEquals("c4") function t:inserted-node() {
    let $found := try {
        let $_ := update insert <category xml:id="c4" n="d"><catDesc>four</catDesc></category> into collection($t:COLL)//taxonomy
        return collection($t:COLL)//category[ft:query(., "parentname:taxonomy AND nchildren:1")]/@xml:id/string()[. = "c4"]
    } catch * {
        $err:description
    }
    let $_ := update delete collection($t:COLL)//category[@xml:id = "c4"]
    return $found
};
