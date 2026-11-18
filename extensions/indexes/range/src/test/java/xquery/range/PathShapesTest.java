/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package xquery.range;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.test.pathshapes.PathShapes;
import org.exist.test.pathshapes.PathShapes.Matrix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Range comparisons and functions over the shapes a path can have, with the range index and with no index: the matrices {@code path-shapes.xqm} and {@code path-shapes-noindex.xqm} are generated from, see {@link PathShapes}.
 */
class PathShapesTest {

    @Test
    void rangeIsGenerated() throws IOException {
        final Path file = Path.of("src/test/xquery/range/path-shapes.xqm");
        final Matrix matrix = range();
        PathShapes.writeIfRequested(matrix, file);
        assertEquals(matrix.render(), Files.readString(file, StandardCharsets.UTF_8), PathShapes.notGenerated(file));
    }

    @Test
    void noIndexIsGenerated() throws IOException {
        final Path file = Path.of("src/test/xquery/range/path-shapes-noindex.xqm");
        final Matrix matrix = noIndex();
        PathShapes.writeIfRequested(matrix, file);
        assertEquals(matrix.render(), Files.readString(file, StandardCharsets.UTF_8), PathShapes.notGenerated(file));
    }

    static Matrix range() {
        final Matrix matrix = new Matrix("range")
                .imports("import module namespace range=\"http://exist-db.org/xquery/range\";")
                .index("<range><create qname=\"item\" type=\"xs:string\"/><create qname=\"sec\" type=\"xs:string\"/><create qname=\"part\" type=\"xs:string\"/><create qname=\"h:body\" type=\"xs:string\"/><create qname=\"t\" type=\"xs:string\"/><create qname=\"@a\" type=\"xs:string\"/><create qname=\"other\"><field name=\"ps-range\" match=\"t\" type=\"xs:string\"/></create></range>");

        matrix.section("A range comparison", "eq", ". = \"hit\"", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 #12
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 #18
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 #2
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            #0
                nested-desc             c1 c2 c3 #3
                nested-any              c1 c2 c3 #3
                nested-first            #0
                pred-before             2 3 #2
                pred-after              2 3 #2
                narrowed                3 #1
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #0
                doc-variable            #0
                doc-self                #0
                doc-one                 #0
                doc-root                #0
                doc-root-step           #0
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #3
                text-desc               #6
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               u1 u2 u3 #3
                """)
                .note("unindexed", "A comparison is not an index function: a node that no configuration indexes is scanned and found, as XPath says");
        matrix.section("fn:matches", "matches", "matches(., \"hit\")", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 #15
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 #21
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 #2
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            t1 t2 t3 #3
                nested-desc             c1 c2 c3 t1 t2 t3 #6
                nested-any              c1 c2 c3 t1 t2 t3 #6
                nested-first            t1 t2 t3 #3
                pred-before             2 3 #2
                pred-after              2 3 #2
                narrowed                3 #1
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #3
                doc-variable            #3
                doc-self                #3
                doc-one                 #1
                doc-root                #3
                doc-root-step           #3
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #3
                text-desc               #6
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               u1 u2 u3 #3
                """)
                .note("unindexed", "fn:matches is not an index function: a node that no configuration indexes is scanned and found, as XPath says");
        matrix.section("A range comparison on a child", "eq-child", "t = \"hit\"", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("A range comparison on an attribute", "eq-attribute", "@a = \"hit\"", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 #3
                wildcard-desc      1 2 3 #3
                union              1 2 3 #3
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  #0
                nested-child       #0
                nested-desc        #0
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("fn:matches on a child", "matches-child", "matches(t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("fn:contains on a child", "contains-child", "contains(t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.partialSection("range:eq", "range-eq", "range:eq(., \"hit\")", """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 #3
                wildcard-desc      1 2 3 #9
                node               1 2 3 #3
                other-child        #0
                other-desc         #0
                ns-child           1 2 3 #3
                ns-wildcard        1 2 3 #3
                any-ns             1 2 3 #3
                union              1 2 3 #3
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  #0
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                unindexed          #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes")
                .pending("ns-wildcard", "A namespace wildcard searches an index of an element named *, see https://github.com/eXist-db/exist/issues/6990")
                .pending("any-ns", "A namespace wildcard searches an index of an element named *, see https://github.com/eXist-db/exist/issues/6990");
        matrix.section("range:eq on a child", "range-eq-child", "range:eq(t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.partialSection("range:matches", "range-matches", "range:matches(., \"hit\")", """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 #3
                wildcard-desc      1 2 3 #9
                node               1 2 3 #3
                other-child        #0
                other-desc         #0
                ns-child           1 2 3 #3
                ns-wildcard        1 2 3 #3
                any-ns             1 2 3 #3
                union              1 2 3 #3
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  #0
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                unindexed          #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes")
                .pending("ns-wildcard", "A namespace wildcard searches an index of an element named *, see https://github.com/eXist-db/exist/issues/6990")
                .pending("any-ns", "A namespace wildcard searches an index of an element named *, see https://github.com/eXist-db/exist/issues/6990");
        matrix.partialSection("range:field-eq", "range-field", "range:field-eq(\"ps-range\", \"hit\")", """
                wildcard           o1 o2 o3 #3
                wildcard-desc      o1 o2 o3 #6
                node               o1 o2 o3 #3
                element            o1 o2 o3 #3
                other-child        o1 o2 o3 #3
                other-desc         o1 o2 o3 #3
                union              o1 o2 o3 #3
                variable           #0
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                for                #0
                doc-filter         #3
                unindexed          #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes")
                .pending("wildcard", "An element test drops the nodes with their own entry, see https://github.com/eXist-db/exist/issues/6987")
                .pending("wildcard-desc", "An element test drops the nodes with their own entry, see https://github.com/eXist-db/exist/issues/6987")
                .pending("element", "An element test drops the nodes with their own entry, see https://github.com/eXist-db/exist/issues/6987");
        matrix.raw("""
                (: Fast forms stay pre-selected, and forms that cannot be stay evaluated as written: the answers cannot show a
                   lost optimization. Without the optimizer, the fast group is on the known failures list. :)
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--comparison() { collection($ps:COLLECTION || "/flat")//item[. = "hit"] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--comparison-child() { collection($ps:COLLECTION || "/flat")/wrap/item[. = "hit"] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--range-eq() { collection($ps:COLLECTION || "/flat")//item[range:eq(., "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range']) and empty($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:as-written--range-eq-of-a-child-on-a-sibling-axis() { collection($ps:COLLECTION || "/flat")//item/following-sibling::other[range:eq(t, "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--comparison-on-a-field() { collection($ps:COLLECTION || "/flat")//other[t = "hit"] };
                (: A direct range:field-* call is never pre-selected, only a comparison the optimizer rewrites to a field. :)
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range']) and empty($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:as-written--field-eq() { collection($ps:COLLECTION || "/flat")//other[range:field-eq("ps-range", "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'range']) and empty($result//stats:index[@optimization-level eq 'OPTIMIZED'])") function ps:as-written--unanchored-matches-of-self() { collection($ps:COLLECTION || "/flat")//item[matches(., "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'range']) and empty($result//stats:index[@optimization-level eq 'OPTIMIZED'])") function ps:as-written--unanchored-matches-of-a-child() { collection($ps:COLLECTION || "/flat")//item[matches(t, "hit")] };
                (: An anchored pattern means the same in XPath and in Lucene, so the optimizer sends it to the range index; an unanchored one stays with fn:matches. :)
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--anchored-matches-of-self() { collection($ps:COLLECTION || "/flat")//item[matches(., "^hit$")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--anchored-matches-of-a-child() { collection($ps:COLLECTION || "/flat")//item[matches(t, "^hi")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--anchored-matches-of-an-attribute() { collection($ps:COLLECTION || "/flat")//item[matches(@a, "it$")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--anchored-matches-ignoring-case() { collection($ps:COLLECTION || "/flat")//item[matches(t, "^HIT$", "i")] };
                declare %test:assertEquals("1 2 3 #3") function ps:anchored-matches-of-self() { ps:answer(collection($ps:COLLECTION || "/flat")//item[matches(., "^hit$")]) };
                declare %test:assertEquals("1 2 3 #3") function ps:anchored-matches-of-a-child() { ps:answer(collection($ps:COLLECTION || "/flat")//item[matches(t, "^hi")]) };
                declare %test:assertEquals("1 2 3 #3") function ps:anchored-matches-of-an-attribute() { ps:answer(collection($ps:COLLECTION || "/flat")//item[matches(@a, "it$")]) };
                declare %test:assertEquals("1 2 3 #3") function ps:anchored-matches-ignoring-case() { ps:answer(collection($ps:COLLECTION || "/flat")//item[matches(t, "^HIT$", "i")]) };
                """);
        return matrix;
    }

    static Matrix noIndex() {
        final Matrix matrix = new Matrix("noindex")
                .index("");

        matrix.section("A range comparison", "eq", ". = \"hit\"", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 #12
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 #18
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 #2
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            #0
                nested-desc             c1 c2 c3 #3
                nested-any              c1 c2 c3 #3
                nested-first            #0
                pred-before             2 3 #2
                pred-after              2 3 #2
                narrowed                3 #1
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #0
                doc-variable            #0
                doc-self                #0
                doc-one                 #0
                doc-root                #0
                doc-root-step           #0
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #3
                text-desc               #6
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               u1 u2 u3 #3
                """);
        matrix.section("fn:matches", "matches", "matches(., \"hit\")", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 #15
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 #21
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 #2
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            t1 t2 t3 #3
                nested-desc             c1 c2 c3 t1 t2 t3 #6
                nested-any              c1 c2 c3 t1 t2 t3 #6
                nested-first            t1 t2 t3 #3
                pred-before             2 3 #2
                pred-after              2 3 #2
                narrowed                3 #1
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #3
                doc-variable            #3
                doc-self                #3
                doc-one                 #1
                doc-root                #3
                doc-root-step           #3
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #3
                text-desc               #6
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               u1 u2 u3 #3
                """);
        matrix.section("A range comparison on a child", "eq-child", "t = \"hit\"", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("A range comparison on an attribute", "eq-attribute", "@a = \"hit\"", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 #3
                wildcard-desc      1 2 3 #3
                union              1 2 3 #3
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  #0
                nested-child       #0
                nested-desc        #0
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("fn:matches on a child", "matches-child", "matches(t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("fn:contains on a child", "contains-child", "contains(t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        return matrix;
    }
}
