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

import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.junit.ComparisonFailure;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;

public class ExtTestFailureFunction extends JUnitIntegrationFunction {

    public ExtTestFailureFunction(final XQueryContext context, final String parentName, final RunNotifier notifier) {
        super("ext-test-failure-function",
                params(
                        param("name", Type.STRING, "name of the test"),
                        param("expected", Type.MAP_ITEM, "expected result of the test"),
                        param("actual", Type.MAP_ITEM, "actual result of the test")
                ), context, parentName, notifier);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence arg1 = getCurrentArguments()[0];
        final String name = arg1.itemAt(0).getStringValue();

        final Sequence arg2 = getCurrentArguments()[1];
        final MapType expected = (MapType)arg2.itemAt(0);

        final Sequence arg3 = getCurrentArguments()[2];
        final MapType actual = (MapType)arg3.itemAt(0);

        final Description description = createTestDescription(name);

        // notify JUnit
        try {
            final AssertionError failureReason = new ComparisonFailure("", expectedToString(expected), actualToString(actual));

            // NOTE: We remove the StackTrace, because it is not useful to have a Java Stack Trace pointing into the XML XQuery Test Suite code
            failureReason.setStackTrace(new StackTraceElement[0]);

            notifier.fireTestFailure(new Failure(description, failureReason));
        } catch (final XPathException | SAXException | IOException | IllegalStateException e) {
            //signal internal failure
            notifier.fireTestFailure(new Failure(description, e));
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private String expectedToString(final MapType expected) throws XPathException, SAXException, IOException {
        final Sequence seqExpectedValue = expected.get(new StringValue(this, "value"));
        if(!seqExpectedValue.isEmpty()) {
            return seqToString(seqExpectedValue);
        }

        final Sequence seqExpectedXPath = expected.get(new StringValue(this, "xpath"));
        if(!seqExpectedXPath.isEmpty()) {
            return "XPath: " + seqToString(seqExpectedXPath);
        }

        final Sequence seqExpectedError = expected.get(new StringValue(this, "error"));
        if(!seqExpectedError.isEmpty()) {
            return "Error: " + seqToString(seqExpectedError);
        }

        throw new IllegalStateException("Could not extract expected value");
    }

    private String actualToString(final MapType actual) throws XPathException, SAXException, IOException {
        final Sequence seqActualError = actual.get(new StringValue(this, "error"));
        if (!seqActualError.isEmpty()) {
            return errorMapToString(seqActualError);
        }

        final Sequence seqActualResult = actual.get(new StringValue(this, "result"));
        if (!seqActualResult.isEmpty()) {
            return seqToString(seqActualResult);
        } else {
            return "";  // empty-sequence()
        }
    }

    private String seqToString(final Sequence seq) throws IOException, XPathException, SAXException {
        try(final StringWriter writer = new StringWriter()) {
            final XQuerySerializer xquerySerializer = new XQuerySerializer(context.getBroker(), new Properties(), writer);
            xquerySerializer.serialize(seq);
            return writer.toString();
        }
    }

    private String errorMapToString(final Sequence seqErrorMap) throws IOException, XPathException, SAXException {
        try(final StringWriter writer = new StringWriter()) {
            final Properties properties = new Properties();
            properties.setProperty(OutputKeys.METHOD, "adaptive");

            final XQuerySerializer xquerySerializer = new XQuerySerializer(context.getBroker(), properties, writer);
            xquerySerializer.serialize(seqErrorMap);
            return writer.toString();
        }
    }
}
