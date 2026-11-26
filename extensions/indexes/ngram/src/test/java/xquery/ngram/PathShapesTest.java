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
package xquery.ngram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.test.pathshapes.PathShapes;
import org.exist.test.pathshapes.PathShapes.Matrix;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ngram:contains over the shapes a path can have: the matrix {@code path-shapes.xqm} is generated from, see {@link PathShapes}.
 */
class PathShapesTest {

    @Test
    void ngramIsGenerated() throws IOException {
        final Path file = Path.of("src/test/xquery/ngram/path-shapes.xqm");
        final Matrix matrix = ngram();
        PathShapes.writeIfRequested(matrix, file);
        assertEquals(matrix.render(), Files.readString(file, StandardCharsets.UTF_8), PathShapes.notGenerated(file));
    }

    static Matrix ngram() {
        final Matrix matrix = new Matrix("ngram")
                .imports("import module namespace ngram=\"http://exist-db.org/xquery/ngram\";")
                .index("<ngram qname=\"item\"/><ngram qname=\"other\"/><ngram qname=\"sec\"/><ngram qname=\"part\"/><ngram qname=\"h:body\"/><ngram qname=\"@a\"/><ngram qname=\"t\"/>")
                .withStateCollections("<ngram qname=\"item\"/>");

        matrix.section("ngram:contains", "ngram", "ngram:contains(., \"hit\")", PathShapes.STEP, """
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
                node-desc               1 2 3 o1 o2 o3 #12
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
                text                    #0
                text-desc               #0
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes");
        matrix.section("ngram:contains on a child", "ngram-child", "ngram:contains(t, \"hit\")", PathShapes.ARGUMENT, """
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
        matrix.section("ngram:contains on an attribute", "ngram-attribute", "ngram:contains(@a, \"hit\")", PathShapes.ARGUMENT, """
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
        matrix.section("After a sequence that was empty: ngram:contains", "after-nothing-contains", "ngram:contains(., \"hit\")", PathShapes.AFTER_NOTHING, """
                empty-first            1 2 #2
                empty-between          1 2 #2
                typed-count            1 2 #2
                """);
        matrix.partialSection("After another collection: ngram:contains, which has no fallback", "elsewhere-contains", "ngram:contains(., \"hit\")", PathShapes.ELSEWHERE, """
                indexed-then-bare      1 2 3 #3
                bare-then-indexed      1 2 3 #3
                """);
        matrix.raw("""
                (: Fast forms stay pre-selected, and forms that cannot be stay evaluated as written: the answers cannot show a
                   lost optimization. Without the optimizer, the fast group is on the known failures list. :)
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'ngram'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--ngram-desc() { collection($ps:COLLECTION || "/flat")//item[ngram:contains(., "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'ngram'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--ngram-child() { collection($ps:COLLECTION || "/flat")/wrap/item[ngram:contains(., "hit")] };
                """);
        return matrix;
    }
}
