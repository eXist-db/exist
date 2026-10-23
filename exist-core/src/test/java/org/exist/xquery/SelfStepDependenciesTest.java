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

import javax.annotation.Nullable;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A self step whose predicates all select from the context set as a whole is evaluated once over its context, as a
 * step on another axis is; with a positional or boolean predicate it is evaluated per context item.
 */
class SelfStepDependenciesTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String PROLOG = "declare variable $c external; ";

    @Test
    void nodeSetPredicateNeedsNoContextItem() throws Exception {
        assertFalse(dependsOnContextItem("$c/self::item[@a]"));
    }

    @Test
    void severalNodeSetPredicatesNeedNoContextItem() throws Exception {
        assertFalse(dependsOnContextItem("$c/self::item[@a][b]"));
    }

    @Test
    void positionalPredicateNeedsTheContextItem() throws Exception {
        assertTrue(dependsOnContextItem("$c/self::item[1]"));
    }

    @Test
    void booleanPredicateNeedsTheContextItem() throws Exception {
        assertTrue(dependsOnContextItem("$c/self::item[string-length(.) gt 1]"));
    }

    @Test
    void anyPredicateThatIsNotANodeSetOneNeedsTheContextItem() throws Exception {
        assertTrue(dependsOnContextItem("$c/self::item[@a][1]"));
    }

    private static boolean dependsOnContextItem(final String path) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(broker, new XQueryContext(pool), PROLOG + path);
            final LocationStep step = selfStep((Expression) compiled);
            assertNotNull(step, "no self step with predicates in " + path);
            return Dependency.dependsOn(step.getDependencies(), Dependency.CONTEXT_ITEM);
        }
    }

    private static @Nullable LocationStep selfStep(final Expression expression) {
        if (expression instanceof LocationStep step && step.getAxis() == Constants.SELF_AXIS
                && step.getPredicates() != null && step.getPredicates().length > 0) {
            return step;
        }
        for (int i = 0; i < expression.getSubExpressionCount(); i++) {
            final LocationStep step = selfStep(expression.getSubExpression(i));
            if (step != null) {
                return step;
            }
        }
        return null;
    }
}
