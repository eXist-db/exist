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
package org.exist.xquery;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the key of a comparison reports about the focus. A key reads the focus when its value depends on the context item,
 * on the position or on the size of the context: such a key cannot be evaluated once for all the items of a step, and the
 * comparison and the rewriters that look a key up once have to ask. {@link Dependency#CONTEXT_SET} cannot answer: a step or
 * {@code .} reports only that, and a builtin function reports the context item for every call.
 * {@link Dependency#CONTEXT_FOCUS} can.
 *
 * <p>The table is the completeness test: one row per kind of expression a key can be. A new kind of expression that holds
 * other expressions and does not pass the bit on shows up as a row that fails.</p>
 */
public class DependencyFocusTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final List<Key> KEYS = List.of(
        // no focus
        neither("'x'"),
        neither("$v"),
        neither("$v/b"),
        noFocus("$v/b[@x]"),
        noFocus("($v, 'y')[1]"),
        noFocus("($v, 'y')[last()]"),
        // the focus functions
        position("last()"),
        position("position()"),
        // a relative path, ".", an absolute path
        focus("@n"),
        focus("../@n"),
        focus("."),
        focus("/wrap"),
        // operators and sequences that hold them
        position("1 + last()"),
        position("(last(), 'x')"),
        position("-last()"),
        position("('a' || last())"),
        position("(1 to last())"),
        focus("(@n | @m)"),
        position("(last() and true())"),
        focus("(@n or false())"),
        focus("(@m = 'x')"),
        neither("($v = 'x')"),
        position("(last() = 1)"),
        // functions: through their arguments, and the forms that default to the context item
        focus("string(@n)"),
        position("string(last())"),
        neither("string('x')"),
        neither("lower-case('X')"),
        neither("lower-case($v)"),
        focus("concat('a', @n)"),
        neither("concat('a', 'b')"),
        focus("data(@n)"),
        neither("substring($v, 1, 2)"),
        focus("count(@n)"),
        focus("exists(@n)"),
        focus("not(@n)"),
        neither("sum((1, 2))"),
        position("round(last() div 2)"),
        neither("true()"),
        focus("string()"),
        focus("name()"),
        focus("normalize-space()"),
        focus("string-length()"),
        focus("number()"),
        focus("root()"),
        focus("id('x')"),
        neither("current-date()"),
        neither("current-dateTime()"),
        neither("default-collation()"),
        // casts and type tests
        focus("xs:string(@n)"),
        neither("xs:string('x')"),
        position("xs:string(last())"),
        position("(last() castable as xs:string)"),
        position("(last() instance of xs:integer)"),
        position("(last() treat as xs:integer)"),
        // user functions: the arguments, not the body
        focus("local:f(@n)"),
        neither("local:f($v)"),
        position("local:f(last())"),
        // conditionals, FLWOR, quantifiers
        position("(if (last() = 1) then 'a' else 'b')"),
        focus("(if (@n = '1') then 'a' else 'b')"),
        neither("(if ($v = 'x') then 'a' else 'b')"),
        position("(if (true()) then last() else 0)"),
        position("(if (true()) then 0 else position())"),
        position("(let $l := last() return string($l))"),
        neither("(let $l := 1 return $l)"),
        neither("(for $i in (1, 2) return $i)"),
        position("(for $i in (1, 2) return $i + last())"),
        position("(some $i in (1, 2) satisfies $i = last())"),
        position("(try { last() } catch * { 0 })"),
        position("(switch (last()) case 1 return 'a' default return 'b')"),
        // simple map and arrow: the right operand has the focus of the left one
        focus("(@n ! string(.))"),
        neither("($v ! string(.))"),
        position("(last() ! string(.))"),
        position("(last() => string())"),
        neither("($v => string())"),
        // constructors
        position("[last()]"),
        position("map { 'k': last() }"),
        position("array { last() }"),
        position("<e>{last()}</e>"),
        neither("<e>{$v}</e>"),
        position("<e a=\"{last()}\"/>"),
        position("attribute a { last() }"),
        position("text { last() }"),
        position("comment { last() }"),
        position("processing-instruction p { last() }"),
        position("document { last() }"),
        position("element { last() } { }"),
        position("attribute { 'a' } { last() }"),
        // FLWOR clauses
        position("(for $i in (1, 2) where $i = last() return $i)"),
        position("(for $i in (1, 2) order by last() return $i)"),
        position("(for $i in (1, 2) group by $g := last() return $g)"),
        position("(for $i in (1, 2) count $c return $c + last())"),
        position("(for tumbling window $w in (1, 2, 3) start when true() end when last() gt 0 return $w)"),
        // string constructor and lookup
        position("``[`{last()}`]``"),
        neither("``[x]``"),
        position("map { 1: 2 }?(last())"),
        focus("?1"),
        neither("(map { 1: 2 } ! ?1)"),
        // function items: the body does not see the focus of the expression that creates the item
        neither("(function($x) { $x })"),
        neither("(function() { last() })"),
        neither("()"),
        // kinds that do not say what they read are taken to read the focus, the position and the size
        position("string#0"),
        position("(concat('a', ?))")
    );

    /** What a key is expected to report about the position and the size of the context. */
    private enum Position {
        READS, DOES_NOT_READ, NOT_ASSERTED
    }

    /** A key, and whether it is expected to read the focus and the position or the size of the context. */
    private record Key(String form, boolean focus, Position position) {
    }

    /** Reads neither the focus nor the position. */
    private static Key neither(final String form) {
        return new Key(form, false, Position.DOES_NOT_READ);
    }

    /** Reads the focus, not the position or the size. */
    private static Key focus(final String form) {
        return new Key(form, true, Position.DOES_NOT_READ);
    }

    /** Reads the focus and the position or the size. */
    private static Key position(final String form) {
        return new Key(form, true, Position.READS);
    }

    /** Does not read the focus; what it reports about the position is not part of the contract (a nested predicate). */
    private static Key noFocus(final String form) {
        return new Key(form, false, Position.NOT_ASSERTED);
    }

    @Test
    void keysReportTheFocus() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final List<String> wrong = new ArrayList<>();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            for (final Key key : KEYS) {
                final String query = """
                        xquery version '3.1';
                        declare variable $v := 'x';
                        declare function local:f($x) { $x };
                        collection('/db/none')/wrap/item[@a = %s]
                        """.formatted(key.form());
                final Expression compiled;
                try {
                    compiled = (Expression) xquery.compile(new XQueryContext(broker.getBrokerPool()), query);
                } catch (final Exception e) {
                    wrong.add(key.form() + ": does not compile: " + String.valueOf(e.getMessage()).replace('\n', ' '));
                    continue;
                }
                final GeneralComparison[] found = new GeneralComparison[1];
                compiled.accept(new DefaultExpressionVisitor() {
                    @Override
                    public void visitGeneralComparison(final GeneralComparison comparison) {
                        if (found[0] == null) {
                            found[0] = comparison;
                        }
                        super.visitGeneralComparison(comparison);
                    }
                });
                if (found[0] == null) {
                    wrong.add(key.form() + ": no comparison in the compiled query");
                    continue;
                }
                final int deps = found[0].getRight().getDependencies();
                final boolean readsFocus = Dependency.dependsOn(deps, Dependency.CONTEXT_FOCUS);
                final boolean readsPosition = Dependency.dependsOn(deps, Dependency.CONTEXT_POSITION);
                if (readsFocus != key.focus()) {
                    wrong.add(key.form() + ": reads the focus is " + readsFocus + ", expected " + key.focus()
                            + " (" + Dependency.getDependenciesName(deps) + ")");
                }
                final boolean expectedPosition = key.position() == Position.READS;
                if (key.position() != Position.NOT_ASSERTED && readsPosition != expectedPosition) {
                    wrong.add(key.form() + ": reads the position or the size is " + readsPosition + ", expected "
                            + expectedPosition + " (" + Dependency.getDependenciesName(deps) + ")");
                }
            }
        }
        assertTrue(wrong.isEmpty(), "keys that report the focus wrongly:\n" + String.join("\n", wrong));
    }

    /**
     * A clause of a FLWOR expression binds variables, not the focus: what its own expressions read, the context item too, is
     * what the clause reads. The execution mode of a predicate is chosen from the item bit, so a predicate such as
     * {@code [let $q := string(name) return ...]} is evaluated for each item only if the clause reports it (#6863).
     */
    @Test
    void flworClausesHandOnTheItemTheirExpressionsRead() throws Exception {
        final List<String> wrong = new ArrayList<>();
        final List<String> readTheItem = List.of(
                "(for $i in (1, 2) where string(@n) = 'x' return $i)",
                "(for $i in (1, 2) order by string(@n) return $i)",
                "(for $i in (1, 2) group by $g := string(@n) return $g)",
                "(for $i in string(@n) return $i)",
                "(let $q := string(@n) return 'x')",
                "(for tumbling window $w in (1, 2, 3) start when string(@n) = 'x' end when true() return $w)");
        final List<String> doNotReadTheItem = List.of(
                "(for $i in (1, 2) where 'x' return 'x')",
                "(for $i in (1, 2) order by 1 return 'x')",
                "(let $q := 'x' return 'y')");
        for (final String form : readTheItem) {
            if (!Dependency.dependsOn(reportOfKey(form), Dependency.CONTEXT_ITEM)) {
                wrong.add(form + ": does not report the item");
            }
        }
        for (final String form : doNotReadTheItem) {
            if (Dependency.dependsOn(reportOfKey(form), Dependency.CONTEXT_ITEM)) {
                wrong.add(form + ": reports the item although nothing in it reads the focus");
            }
        }
        assertTrue(wrong.isEmpty(), "clauses that report the item wrongly:\n" + String.join("\n", wrong));
    }

    /** The report of the key of the comparison in {@code collection('/db/none')/wrap/item[@a = KEY]}. */
    private static int reportOfKey(final String form) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final String query = """
                    xquery version '3.1';
                    collection('/db/none')/wrap/item[@a = %s]
                    """.formatted(form);
            final Expression compiled = (Expression) pool.getXQueryService().compile(new XQueryContext(broker.getBrokerPool()), query);
            final GeneralComparison[] found = new GeneralComparison[1];
            compiled.accept(new DefaultExpressionVisitor() {
                @Override
                public void visitGeneralComparison(final GeneralComparison comparison) {
                    if (found[0] == null) {
                        found[0] = comparison;
                    }
                    super.visitGeneralComparison(comparison);
                }
            });
            assertTrue(found[0] != null, form + ": no comparison in the compiled query");
            return found[0].getRight().getDependencies();
        }
    }
}
