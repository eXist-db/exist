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

import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;

public class ExtTestAssumptionFailedFunction extends JUnitIntegrationFunction {
    public ExtTestAssumptionFailedFunction(final XQueryContext context, final String parentName, final RunNotifier notifier) {
        super("ext-test-assumption-failed-function",
                params(
                        param("name", Type.STRING, "name of the test"),
                        optParam("error", Type.MAP_ITEM, "error detail of the test")
                ), context, parentName, notifier);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence arg1 = getCurrentArguments()[0];
        final String name = arg1.itemAt(0).getStringValue();

        final Sequence arg2 = getCurrentArguments().length == 2 ? getCurrentArguments()[1] : null;
        final MapType assumption = arg2 != null ? (MapType)arg2.itemAt(0) : null;

        final Description description = createTestDescription(name);

        // notify JUnit
        try {
            final AssumptionViolatedException assumptionFailureReason = assumptionMapAsAssumptionViolationException(assumption);

            // NOTE: We remove the StackTrace, because it is not useful to have a Java Stack Trace pointing into the XML XQuery Test Suite code
            assumptionFailureReason.setStackTrace(new StackTraceElement[0]);

            notifier.fireTestAssumptionFailed(new Failure(description, assumptionFailureReason));
        } catch (final XPathException e) {
            //signal internal failure
            notifier.fireTestFailure(new Failure(description, e));
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    public AssumptionViolatedException assumptionMapAsAssumptionViolationException(final MapType assumptionMap) throws XPathException {
        final Sequence seqName = assumptionMap.get(new StringValue(this, "name"));
        final String name;
        if(seqName != null && !seqName.isEmpty()) {
            name = seqName.itemAt(0).getStringValue();
        } else {
            name = "";
        }

        final Sequence seqValue = assumptionMap.get(new StringValue(this, "value"));
        final String value;
        if(seqValue != null && !seqValue.isEmpty()) {
            value = seqValue.itemAt(0).getStringValue();
        } else {
            value = "";
        }

        return new AssumptionViolatedException("Assumption %" + name + " does not hold for: " + value);
    }
}
