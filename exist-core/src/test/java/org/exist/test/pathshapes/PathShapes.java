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
package org.exist.test.pathshapes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * The shapes a path can have, and a writer of the path-shapes.xqm files that run an index function over each of them.
 * XQSuite needs one declared function per test, so the files are generated: the shapes are written once here, and each
 * index module holds a {@link Matrix} of the functions it tests and their expected answers. A test in each module checks
 * that its file is what the matrix gives; run it with {@code -Dpath-shapes.write=true} to write the file instead.
 */
public final class PathShapes {

    /**
     * Every shape, for a call on the step's own node. The collections, see {@link #fixture}: {@code flat}, documents of
     * {@code wrap} with an {@code item} and an {@code other}; {@code ns}, the same in a namespace; {@code nest} and
     * {@code rootnest}, an element of one name nested in another; {@code plain}, elements no configuration indexes.
     */
    public static final List<Shape> STEP = shapes(
        group("Named steps",
            shape("desc", "{flat}//item[{p}]"),
            shape("child", "{flat}/wrap/item[{p}]"),
            shape("desc-after-child", "{flat}/wrap//item[{p}]"),
            shape("after-wildcard", "{flat}/*/item[{p}]"),
            shape("element-test", "{flat}/wrap/element(item)[{p}]"),
            shape("dos", "{flat}/wrap/descendant-or-self::item[{p}]"),
            shape("self", "{flat}//item/self::item[{p}]")
        ),
        group("Wildcard and kind tests on the step",
            shape("wildcard", "{flat}/wrap/*[{p}]"),
            shape("wildcard-desc", "{flat}//*[{p}]"),
            shape("node", "{flat}/wrap/node()[{p}]"),
            shape("node-desc", "{flat}//node()[{p}]"),
            shape("element", "{flat}/wrap/element()[{p}]")
        ),
        group("Another element of the file",
            shape("other-child", "{flat}/wrap/other[{p}]"),
            shape("other-desc", "{flat}//other[{p}]")
        ),
        group("Namespaces",
            shape("ns-child", "{ns}/h:html/h:body[{p}]"),
            shape("ns-wildcard", "{ns}/h:html/h:*[{p}]"),
            shape("any-ns", "{ns}/*:html/*:body[{p}]")
        ),
        group("Unions, parentheses and variables",
            shape("union", "{flat}/wrap/(item|other)[{p}]"),
            shape("paren-step", "{flat}/wrap/(item)[{p}]"),
            shape("paren-path", "({flat}/wrap/item)[{p}]"),
            shape("variable", "(let $i := {flat}/wrap/item return $i[{p}])"),
            shape("two-filters", "(let $i := {flat}/wrap/item return $i[@n > 1][{p}])"),
            shape("typed-argument", "ps:typed({flat}/wrap/item[{p}])"),
            shape("typed-variable", "(let $i := {flat}/wrap/item return ps:typed($i[{p}]))")
        ),
        group("Reverse and sibling axes",
            shape("parent", "{flat}//t/parent::item[{p}]"),
            shape("ancestor", "{flat}//t/ancestor::item[{p}]"),
            shape("following-sibling", "{flat}//item/following-sibling::other[{p}]"),
            shape("preceding-sibling", "{flat}//other/preceding-sibling::item[{p}]")
        ),
        group("Nested elements of one name",
            shape("nested-child", "{nest}/root/sec[{p}]"),
            shape("nested-desc", "{nest}/root//sec[{p}]"),
            shape("nested-any", "{nest}//sec[{p}]"),
            shape("nested-first", "{rootnest}/part[{p}]")
        ),
        group("Other predicates and narrower input",
            shape("pred-before", "{flat}/wrap/item[@n > 1][{p}]"),
            shape("pred-after", "{flat}/wrap/item[{p}][@n > 1]"),
            shape("narrowed", "{flat}/wrap[item/xs:integer(@n) > 2]/item[{p}]"),
            shape("in-predicate", "{flat}/wrap[item[{p}]]/item"),
            shape("for", "(for $w in {flat}/wrap[item/xs:integer(@n) <= 2] return $w/item[{p}])")
        ),
        group("Documents as candidates",
            shape("doc-filter", "{flat}[{p}]"),
            shape("doc-variable", "(let $d := {flat} return $d[{p}])"),
            shape("doc-self", "{flat}/self::node()[{p}]"),
            shape("doc-one", "doc($ps:COLLECTION || \"/flat/d1.xml\")[{p}]"),
            shape("doc-root", "({flat}/wrap/root())[{p}]"),
            shape("doc-root-step", "{flat}/wrap/root()[{p}]"),
            shape("from-doc-variable", "(let $d := {flat} return $d/wrap/item[{p}])"),
            shape("desc-from-doc-variable", "(let $d := {flat} return $d//item[{p}])")
        ),
        group("Other kinds of node",
            shape("text", "{flat}/wrap/item/t/text()[{p}]"),
            shape("text-desc", "{flat}//text()[{p}]"),
            shape("comment", "{flat}/wrap/comment()[{p}]"),
            shape("comment-desc", "{flat}//comment()[{p}]"),
            shape("pi", "{flat}/wrap/processing-instruction()[{p}]"),
            shape("attribute-no-index", "{flat}/wrap/item/@n[{p}]"),
            shape("attribute", "{flat}/wrap/item/@a[{p}]"),
            shape("attribute-desc", "{flat}//@a[{p}]"),
            shape("attribute-wildcard", "{flat}/wrap/item/@*[{p}]"),
            shape("attribute-vector", "{flat}/wrap/item/@v[{p}]"),
            shape("attribute-vector-desc", "{flat}//@v[{p}]"),
            shape("unindexed", "{plain}/doc/u[{p}]")
        )
    );

    /** The shapes for a call on a node the step's node leads to, such as {@code t} or {@code @a}: one of each kind. */
    public static final List<Shape> ARGUMENT = only("desc", "child", "desc-after-child", "self", "wildcard", "wildcard-desc",
            "union", "variable", "two-filters", "typed-argument", "parent", "following-sibling", "nested-child", "nested-desc",
            "pred-before", "narrowed", "for", "doc-filter");

    /**
     * The same predicate evaluated again over a let-bound sequence of stored nodes that was empty in an earlier evaluation, as
     * in a loop that takes one document at a time: an expression that kept what it found, or decided to optimize, for an empty
     * context gives wrong nodes in the next. A variable is the only way to give a pre-selecting expression an empty context.
     * The keys 11 and 12 match no item, so the order does not depend on the order the collection is walked in; items 1 and 2
     * are the nodes, both hits of the predicates tested.
     */
    public static final List<Shape> AFTER_NOTHING = shapes(
        group("After a sequence that was empty",
            shape("empty-first", "(for $i in (11, 1, 12, 2) let $d := {flat}/wrap/item[xs:integer(@n) = $i] return $d[{p}])"),
            shape("empty-between", "(for $i in (1, 11, 2) let $d := {flat}/wrap/item[xs:integer(@n) = $i] return $d[{p}])"),
            shape("typed-count", "(for $i in (11, 1, 12, 2) let $d := {flat}/wrap/item[xs:integer(@n) = $i] "
                    + "return if (count($d[{p}]) > 0) then $d else ())")
        )
    );

    /**
     * The same predicate evaluated again over another collection of the same documents, in one loop: {@code flat} has the
     * matrix's configuration, {@code -bare} none, {@code -partial} only the one given to {@link Matrix#withStateCollections}.
     * A predicate that read what the collection before left behind finds nothing, or something it must not, in the next. The
     * path is pre-selected from the index, so this is the case the state is kept for. State belongs to the expression, not
     * to the path, so a few paths stand for all.
     */
    public static final List<Shape> ELSEWHERE = shapes(
        group("After another collection",
            shape("indexed-then-bare", "(for $c in ($ps:COLLECTION || \"/flat\", $ps:COLLECTION || \"-bare\") return collection($c)//item[{p}])"),
            shape("bare-then-indexed", "(for $c in ($ps:COLLECTION || \"-bare\", $ps:COLLECTION || \"/flat\") return collection($c)//item[{p}])"),
            shape("indexed-then-partial", "(for $c in ($ps:COLLECTION || \"/flat\", $ps:COLLECTION || \"-partial\") return collection($c)//item[{p}][t = \"hit\"])"),
            shape("partial-then-indexed", "(for $c in ($ps:COLLECTION || \"-partial\", $ps:COLLECTION || \"/flat\") return collection($c)//item[{p}][t = \"hit\"])")
        )
    );

    /** An answer that starts with this is the error code the shape raises, such as {@code error:EXXQDYFT0006}. */
    private static final String ERROR_PREFIX = "error:";

    private static final String HEADER = """
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
             : Each index function over the shapes a path can have, and the kinds of node it can be given. The suites run this file
             : with and without the optimizer against one expected answer, so the two have to agree and be right. An answer is the
             : sorted @n of the nodes returned, or of an attribute's parent, then # and how many nodes there were, since some nodes
             : have no @n. See the other path-shapes.xqm files.
             :
             : Generated by org.exist.test.pathshapes.PathShapes from the matrix in this module's PathShapesTest: edit the matrix,
             : then run PathShapesTest with -Dpath-shapes.write=true.
             :)
            module namespace ps="http://exist-db.org/xquery/test/path-shapes/{name}";

            {imports}declare namespace test="http://exist-db.org/xquery/xqsuite";
            declare namespace stats="http://exist-db.org/xquery/profiling";
            declare namespace h="http://www.w3.org/1999/xhtml";

            declare variable $ps:COLLECTION_NAME := "test-path-shapes-{name}";
            declare variable $ps:COLLECTION := "/db/" || $ps:COLLECTION_NAME;
            declare variable $ps:CONF := "/db/system/config/db/" || $ps:COLLECTION_NAME;

            """;

    private static final String TERM = """
            (: the term is in the first 3 of 10 :)
            declare function ps:term($i) {
                <t xmlns="">{if ($i <= 3) then "hit" else "miss"}</t>
            };

            """;

    private static final String VECTOR_FUNCTIONS = """
            (: an element below item with its own field and vector :)
            declare function ps:sub($i) {
                <sub n="s{$i}"><st>{if ($i <= 3) then "hit" else "miss"}</st><embedding>{"1 " || $i div 10 || " 0 0"}</embedding></sub>
            };

            (: its own element holds the vector, a field without an expression: the only kind pre-selected :)
            declare function ps:fast($i) {
                <fv n="v{$i}">{"1 " || $i div 10 || " 0 0 0 0"}</fv>
            };

            (: the vector moves away from the query as $seed grows :)
            declare function ps:vector($seed) {
                <embedding xmlns="">{"1 " || $seed div 10 || " 0"}</embedding>
            };

            """;

    private static final String SET_UP = """
            declare
                %test:setUp
            function ps:setUp() {
                xmldb:create-collection("/db/system", "config"),
                xmldb:create-collection("/db/system/config", "db"),
                xmldb:create-collection("/db/system/config/db", $ps:COLLECTION_NAME),
                xmldb:store($ps:CONF, "collection.xconf", $ps:XCONF),
                xmldb:create-collection("/db", $ps:COLLECTION_NAME),
                for $c in ("flat", "ns", "nest", "rootnest", "plain") return xmldb:create-collection($ps:COLLECTION, $c),
                for $i in 1 to 10
                return (
                    xmldb:store($ps:COLLECTION || "/flat", "d" || $i || ".xml",
                        <wrap><!--c-->{processing-instruction pi {"x"}}<item n="{$i}" a="{ps:term($i)}" v="1 {$i div 10} 0{attribute-vector}">{ps:term($i){item-vectors}}</item><other n="o{$i}">{ps:term($i){other-vector}}</other></wrap>),
                    xmldb:store($ps:COLLECTION || "/ns", "h" || $i || ".xml",
                        <html xmlns="http://www.w3.org/1999/xhtml"><head/><body n="{$i}">{ps:term($i){vector}}</body></html>),
                    (: the term is only in the nested element{outer-vectors} :)
                    xmldb:store($ps:COLLECTION || "/nest", "n" || $i || ".xml",
                        <root><sec n="t{$i}">{ps:term(10){far-vector}}<sec n="c{$i}">{ps:term($i){vector}}</sec></sec></root>),
                    xmldb:store($ps:COLLECTION || "/rootnest", "r" || $i || ".xml",
                        <part n="t{$i}">{ps:term(10){far-vector}}<part n="c{$i}">{ps:term($i){vector}}</part></part>),
                    (: no configuration indexes doc or u :)
                    xmldb:store($ps:COLLECTION || "/plain", "p" || $i || ".xml",
                        <doc><u n="u{$i}">{if ($i <= 3) then "hit" else "miss"}</u></doc>)
                )
            };

            declare
                %test:tearDown
            function ps:tearDown() {
                xmldb:remove($ps:COLLECTION),
                xmldb:remove($ps:CONF)
            };

            declare function ps:typed($nodes as element()*) as element()* {
                $nodes
            };

            declare function ps:answer($nodes) {
                string-join((sort(($nodes/@n, $nodes[self::attribute()]/../@n)/string()), "#" || count($nodes)), " ")
            };
            """;

    private static final String FIXTURE = TERM + SET_UP
            .replace("{item-vectors}", "").replace("{attribute-vector}", "").replace("{other-vector}", "").replace("{vector}", "")
            .replace("{far-vector}", "").replace("{outer-vectors}", "");

    private static final String FIXTURE_WITH_VECTORS = TERM + VECTOR_FUNCTIONS + SET_UP
            .replace("{item-vectors}", ", ps:vector($i), ps:sub($i), ps:fast($i)").replace("{attribute-vector}", " 0 0").replace("{other-vector}", ", ps:vector($i + 0.5)")
            .replace("{vector}", ", ps:vector($i)").replace("{far-vector}", ", ps:vector(21 - $i)")
            .replace("{outer-vectors}", "; the outer elements' vectors are far, in the opposite order");

    /** One shape: a path with {@code {p}} where the predicate goes, and a collection token such as {@code {flat}}. */
    public record Shape(String name, String group, String path) {
    }

    private record Group(String name, List<Shape> shapes) {
    }

    private PathShapes() {
    }

    /** The shapes of {@link #STEP} with these names, in its order. */
    public static List<Shape> only(final String... names) {
        final Set<String> wanted = new LinkedHashSet<>(Arrays.asList(names));
        final List<Shape> shapes = STEP.stream().filter(shape -> wanted.remove(shape.name())).toList();
        if (!wanted.isEmpty()) {
            throw new IllegalArgumentException("No such shapes: " + wanted);
        }
        return shapes;
    }

    private static Shape shape(final String name, final String path) {
        return new Shape(name, null, path);
    }

    private static Group group(final String name, final Shape... shapes) {
        return new Group(name, Arrays.asList(shapes));
    }

    private static List<Shape> shapes(final Group... groups) {
        return Arrays.stream(groups)
                .flatMap(group -> group.shapes().stream().map(shape -> new Shape(shape.name(), group.name(), shape.path())))
                .toList();
    }

    /**
     * Writes a module's path-shapes file from its matrix when the system property {@code path-shapes.write} is
     * {@code true}; the test then checks the file against the matrix.
     */
    public static void writeIfRequested(final Matrix matrix, final Path file) throws IOException {
        if (Boolean.getBoolean("path-shapes.write")) {
            Files.writeString(file, matrix.render(), StandardCharsets.UTF_8);
        }
    }

    /** The failure message of a test that finds a path-shapes file different from its matrix. */
    public static String notGenerated(final Path file) {
        return file + " is not what its matrix gives: edit the matrix, not the file, then run this test with -Dpath-shapes.write=true";
    }

    /** The functions one path-shapes file tests, over which shapes, and their expected answers. */
    public static final class Matrix {
        private final String name;
        private final List<String> imports = new ArrayList<>();
        private String index = "";
        private boolean vectors;
        private String partialIndex;
        private final List<Object> parts = new ArrayList<>();
        private final Map<String, String> pendingCells = new LinkedHashMap<>();

        /** @param name the name of the file's module and collection: {@code path-shapes/name} and {@code test-path-shapes-name} */
        public Matrix(final String name) {
            this.name = name;
        }

        /** Adds an import, such as {@code import module namespace range="...";}. */
        public Matrix imports(final String importDeclaration) {
            imports.add(importDeclaration);
            return this;
        }

        /** The content of the collection configuration's {@code index} element. */
        public Matrix index(final String index) {
            this.index = index;
            return this;
        }

        /**
         * Adds two collections next to the matrix's, with the documents of {@code flat}: {@code -bare}, which no
         * configuration indexes, and {@code -partial}, whose configuration is {@code partialIndex}, the content of its
         * {@code index} element. See {@link #ACROSS_COLLECTIONS}.
         */
        public Matrix withStateCollections(final String partialIndex) {
            this.partialIndex = partialIndex;
            return this;
        }

        /** Gives the documents vectors, and {@code item} a {@code sub} element with a field and a vector of its own. */
        public Matrix withVectors() {
            this.vectors = true;
            return this;
        }

        /**
         * A section running {@code call} as the predicate of each shape listed in {@code answers}, which has a line per
         * shape: its name, then its expected answer. A section over all of {@code shapes} must list each of them.
         *
         * @param title the section's title
         * @param prefix the prefix of its test names, before {@code --} and the shape's name
         * @param call the predicate
         * @param shapes the shapes the section runs over, which fixes their order
         * @param answers the expected answers
         */
        public Section section(final String title, final String prefix, final String call, final List<Shape> shapes,
                final String answers) {
            final Section section = new Section(title, prefix, call, shapes, answers, true);
            parts.add(section);
            return section;
        }

        /** A section running over only the shapes listed in {@code answers}. */
        public Section partialSection(final String title, final String prefix, final String call, final String answers) {
            final Section section = new Section(title, prefix, call, STEP, answers, false);
            parts.add(section);
            return section;
        }

        /**
         * Marks tests pending, by their name in the generated file, {@code prefix--shape}, for one reason. For the cells a
         * known defect makes fail until its fix is merged: delete the call then. A name that is no cell of this matrix is
         * an error, so a stale list is found.
         *
         * @param reason why they are pending, with the issue
         * @param cells the names of the tests
         */
        public Matrix pendingCells(final String reason, final String... cells) {
            for (final String cell : cells) {
                if (pendingCells.put(cell, reason) != null) {
                    throw new IllegalArgumentException("pending twice: " + cell);
                }
            }
            return this;
        }

        /** A section running over the shapes of {@code shapes} listed in {@code answers}. */
        public Section partialSection(final String title, final String prefix, final String call, final List<Shape> shapes,
                final String answers) {
            final Section section = new Section(title, prefix, call, shapes, answers, false);
            parts.add(section);
            return section;
        }

        /** XQuery written into the file as it is, such as tests of the optimizer. */
        public Matrix raw(final String xquery) {
            parts.add(xquery.strip());
            return this;
        }

        public String render() {
            final StringBuilder out = new StringBuilder();
            final Set<String> usedCells = new HashSet<>();
            final String importLines = imports.stream().map(i -> i + "\n").collect(Collectors.joining());
            out.append(HEADER.replace("{name}", name).replace("{imports}", importLines.isEmpty() ? "" : importLines + "\n"));
            out.append("declare variable $ps:XCONF :=\n");
            out.append("    <collection xmlns=\"http://exist-db.org/collection-config/1.0\" xmlns:h=\"http://www.w3.org/1999/xhtml\">\n");
            out.append("        <index>\n");
            out.append("            ").append(index).append('\n');
            out.append("        </index>\n");
            out.append("    </collection>;\n\n");
            if (partialIndex != null) {
                out.append("declare variable $ps:PARTIAL_XCONF :=\n");
                out.append("    <collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n");
                out.append("        <index>\n");
                out.append("            ").append(partialIndex).append('\n');
                out.append("        </index>\n");
                out.append("    </collection>;\n\n");
            }
            final String fixture = vectors ? FIXTURE_WITH_VECTORS : FIXTURE;
            out.append(partialIndex == null ? fixture : addStateCollections(fixture));
            for (final Object part : parts) {
                out.append('\n');
                if (part instanceof Section section) {
                    section.render(out, pendingCells, usedCells);
                } else {
                    out.append(part).append('\n');
                }
            }
            if (!usedCells.containsAll(pendingCells.keySet())) {
                final Set<String> unknown = new TreeSet<>(pendingCells.keySet());
                unknown.removeAll(usedCells);
                throw new IllegalArgumentException("pending cells that are no test of this matrix: " + unknown);
            }
            return out.toString();
        }
    }

    /** A function over shapes, see {@link Matrix#section}. */
    public static final class Section {
        private final String title;
        private final String prefix;
        private final String call;
        private final Map<Shape, String> answers = new LinkedHashMap<>();
        private final Map<String, String> notes = new LinkedHashMap<>();
        private final Map<String, String> pending = new LinkedHashMap<>();

        private Section(final String title, final String prefix, final String call, final List<Shape> shapes,
                final String answers, final boolean complete) {
            this.title = title;
            this.prefix = prefix;
            this.call = call;
            final Map<String, String> byName = new LinkedHashMap<>();
            for (final String line : answers.strip().split("\n")) {
                final String[] cells = line.strip().split("\\s+", 2);
                if (cells.length != 2 || byName.put(cells[0], cells[1]) != null) {
                    throw new IllegalArgumentException(prefix + ": a shape and an answer per line, once each: " + line);
                }
            }
            for (final Shape shape : shapes) {
                final String answer = byName.remove(shape.name());
                if (answer != null) {
                    this.answers.put(shape, answer);
                } else if (complete) {
                    throw new IllegalArgumentException(prefix + ": no answer for " + shape.name());
                }
            }
            if (!byName.isEmpty()) {
                throw new IllegalArgumentException(prefix + ": not shapes of this section: " + byName.keySet());
            }
        }

        /** A comment above the shape's test. */
        public Section note(final String shape, final String note) {
            requireShape(shape);
            notes.put(shape, note);
            return this;
        }

        /** Marks the shape's test pending, for this reason. */
        public Section pending(final String shape, final String reason) {
            requireShape(shape);
            pending.put(shape, reason);
            return this;
        }

        private void requireShape(final String shape) {
            if (answers.keySet().stream().noneMatch(s -> s.name().equals(shape))) {
                throw new IllegalArgumentException(prefix + ": no test for " + shape);
            }
        }

        void render(final StringBuilder out, final Map<String, String> pendingCells, final Set<String> usedCells) {
            out.append("(: === ").append(title).append(" === :)\n");
            String group = null;
            for (final Map.Entry<Shape, String> entry : answers.entrySet()) {
                final Shape shape = entry.getKey();
                if (!shape.group().equals(group)) {
                    group = shape.group();
                    out.append("\n(: ").append(group).append(" :)\n");
                }
                final String note = notes.get(shape.name());
                if (note != null) {
                    out.append("(: ").append(note).append(" :)\n");
                }
                out.append("declare ");
                final String cell = prefix + "--" + shape.name();
                String reason = pending.get(shape.name());
                if (pendingCells.containsKey(cell)) {
                    usedCells.add(cell);
                    reason = reason == null ? pendingCells.get(cell) : reason;
                }
                if (reason != null) {
                    out.append("%test:pending(\"").append(reason).append("\") ");
                }
                final String answer = entry.getValue();
                out.append(answer.startsWith(ERROR_PREFIX)
                                ? "%test:assertError(\"" + answer.substring(ERROR_PREFIX.length()) + "\")"
                                : "%test:assertEquals(\"" + answer + "\")")
                        .append(" function ps:")
                        .append(prefix).append("--").append(shape.name()).append("() { ps:answer(")
                        .append(expand(shape.path()).replace("{p}", call)).append(") };\n");
            }
        }
    }

    /** The fixture with the collections of {@link Matrix#withStateCollections}, which get the documents of {@code flat} too. */
    private static String addStateCollections(final String fixture) {
        return replaceOnce(replaceOnce(replaceOnce(fixture,
                "    for $c in (\"flat\", \"ns\", \"nest\", \"rootnest\", \"plain\") return xmldb:create-collection($ps:COLLECTION, $c),\n",
                "    for $c in (\"flat\", \"ns\", \"nest\", \"rootnest\", \"plain\") return xmldb:create-collection($ps:COLLECTION, $c),\n"
                        + "    xmldb:create-collection(\"/db\", $ps:COLLECTION_NAME || \"-bare\"),\n"
                        + "    xmldb:create-collection(\"/db/system/config/db\", $ps:COLLECTION_NAME || \"-partial\"),\n"
                        + "    xmldb:store($ps:CONF || \"-partial\", \"collection.xconf\", $ps:PARTIAL_XCONF),\n"
                        + "    xmldb:create-collection(\"/db\", $ps:COLLECTION_NAME || \"-partial\"),\n"),
                "        xmldb:store($ps:COLLECTION || \"/flat\", \"d\" || $i || \".xml\",\n",
                "        for $target in ($ps:COLLECTION || \"/flat\", $ps:COLLECTION || \"-bare\", $ps:COLLECTION || \"-partial\")\n"
                        + "        return xmldb:store($target, \"d\" || $i || \".xml\",\n"),
                "    xmldb:remove($ps:COLLECTION),\n    xmldb:remove($ps:CONF)\n",
                "    xmldb:remove($ps:COLLECTION),\n    xmldb:remove($ps:COLLECTION || \"-bare\"),\n    xmldb:remove($ps:COLLECTION || \"-partial\"),\n"
                        + "    xmldb:remove($ps:CONF),\n    xmldb:remove($ps:CONF || \"-partial\")\n");
    }

    private static String replaceOnce(final String text, final String target, final String replacement) {
        if (!text.contains(target)) {
            throw new IllegalStateException("the fixture has changed, update the state collections: " + target);
        }
        return text.replace(target, replacement);
    }

    private static String expand(final String path) {
        return Stream.of("flat", "ns", "nest", "rootnest", "plain").reduce(path,
                (p, collection) -> p.replace("{" + collection + "}", "collection($ps:COLLECTION || \"/" + collection + "\")"),
                (a, b) -> b);
    }
}
