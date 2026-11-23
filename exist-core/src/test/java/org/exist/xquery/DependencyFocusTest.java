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
 * What the key of a comparison reports about the focus. A key reads the focus when its value depends on the context item, on the
 * position or on the size of the context: such a key cannot be evaluated once for all the items of a step, and the comparison
 * and the rewriters that look a key up once have to ask. {@link Dependency#CONTEXT_SET} cannot answer: a step or {@code .} reports only
 * that, and a builtin function reports the context item for every call. {@link Dependency#CONTEXT_FOCUS} can.
 *
 * <p>The table is the completeness test: one row per kind of expression a key can be. A new kind of expression that holds
 * other expressions and does not pass the bit on shows up as a row that fails.</p>
 */
public class DependencyFocusTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    /** A key, whether it reads the focus, and whether it reads the position or the size ({@code null}: not asserted). */
    private record Key(String form, boolean focus, Boolean position) {
    }

    private static Key key(final String form, final boolean focus, final Boolean position) {
        return new Key(form, focus, position);
    }

    private static final List<Key> KEYS = List.of(
        // no focus
        key("'x'", false, false),
        key("$v", false, false),
        key("$v/b", false, false),
        key("$v/b[@x]", false, null),
        key("($v, 'y')[1]", false, null),
        key("($v, 'y')[last()]", false, null),
        // the focus functions
        key("last()", true, true),
        key("position()", true, true),
        // a relative path, ".", an absolute path
        key("@n", true, false),
        key("../@n", true, false),
        key(".", true, false),
        key("/wrap", true, false),
        // operators and sequences that hold them
        key("1 + last()", true, true),
        key("(last(), 'x')", true, true)
    );

    @Test
    void keysReportTheFocus() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final List<String> wrong = new ArrayList<>();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            for (final Key key : KEYS) {
                final String query = "xquery version '3.1'; declare variable $v := 'x'; declare function local:f($x) { $x }; "
                        + "collection('/db/none')/wrap/item[@a = " + key.form() + "]";
                final Expression compiled = (Expression) xquery.compile(new XQueryContext(pool), query);
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
                final boolean focus = Dependency.dependsOn(deps, Dependency.CONTEXT_FOCUS);
                final boolean position = Dependency.dependsOn(deps, Dependency.CONTEXT_POSITION);
                if (focus != key.focus()) {
                    wrong.add(key.form() + ": reads the focus is " + focus + ", expected " + key.focus() + " (" + Dependency.getDependenciesName(deps) + ")");
                }
                if (key.position() != null && position != key.position()) {
                    wrong.add(key.form() + ": reads the position or the size is " + position + ", expected " + key.position() + " (" + Dependency.getDependenciesName(deps) + ")");
                }
            }
        }
        assertTrue(wrong.isEmpty(), "keys that report the focus wrongly:\n" + String.join("\n", wrong));
    }
}
