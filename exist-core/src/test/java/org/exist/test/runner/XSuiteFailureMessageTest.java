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

package org.exist.test.runner;

import org.junit.ComparisonFailure;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The XQSuite layer hands {@link ExtTestFailureFunction} values it has already serialized to
 * strings. Serializing them a second time on the Java side escaped their markup, so a test whose
 * result was {@code <doc a="1">text</doc>} reported it as {@code &lt;doc a="1"&gt;text&lt;/doc&gt;}
 * and a string result containing markup was escaped twice over.
 */
class XSuiteFailureMessageTest {

    @Test
    void nodeResultIsNotEscapedIntoTheFailureMessage() {
        final ComparisonFailure failure = runSuiteAndGetComparisonFailure();

        assertEquals("<doc a=\"1\">text</doc>", failure.getActual(),
            "a node-valued result should reach the failure message as markup, not XML-escaped");
    }

    private static ComparisonFailure runSuiteAndGetComparisonFailure() {
        final List<Failure> collected = new ArrayList<>();
        final JUnitCore core = new JUnitCore();
        core.addListener(new RunListener() {
            @Override
            public void testFailure(final Failure failure) {
                collected.add(failure);
            }
        });

        final Result result = core.run(SerializationFailureMessageSuite.class);
        assertFalse(result.wasSuccessful(), "suite is expected to fail (failing-serialization.xqm)");

        final ComparisonFailure comparisonFailure = collected.stream()
            .map(Failure::getException)
            .filter(ComparisonFailure.class::isInstance)
            .map(ComparisonFailure.class::cast)
            .findFirst()
            .orElse(null);
        assertNotNull(comparisonFailure,
            "expected a ComparisonFailure from failing-serialization.xqm; collected: " + collected.size());

        return comparisonFailure;
    }
}
