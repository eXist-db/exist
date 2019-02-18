/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.test.runner;

import org.exist.xquery.Annotation;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;

public class ExtTestErrorFunction extends JUnitIntegrationFunction {

    public ExtTestErrorFunction(final XQueryContext context, final String parentName, final RunNotifier notifier) {
        super("ext-test-error-function",
                params(
                        param("name", Type.STRING, "name of the test"),
                        optParam("error", Type.MAP, "error detail of the test. e.g. map { \"code\": $err:code, \"description\": $err:description, \"value\": $err:value, \"module\": $err:module, \"line-number\": $err:line-number, \"column-number\": $err:column-number, \"additional\": $err:additional, \"xquery-stack-trace\": $exerr:xquery-stack-trace, \"java-stack-trace\": $exerr:java-stack-trace}")
                ), context, parentName, notifier);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence arg1 = getCurrentArguments()[0];
        final String name = arg1.itemAt(0).getStringValue();

        final Sequence arg2 = getCurrentArguments().length == 2 ? getCurrentArguments()[1] : null;
        final MapType error = arg2 != null ? (MapType)arg2.itemAt(0) : null;

        final Description description = Description.createTestDescription(suiteName, name, new Annotation[0]);

        // notify JUnit
        try {
            final XPathException errorReason = errorMapAsXPathException(error);
            notifier.fireTestFailure(new Failure(description, errorReason));
        } catch (final XPathException e) {
            //signal internal failure
            notifier.fireTestFailure(new Failure(description, e));
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private XPathException errorMapAsXPathException(final MapType errorMap) throws XPathException {
        final Sequence seqDescription = errorMap.get(new StringValue("description"));
        final String description;
        if(seqDescription != null && !seqDescription.isEmpty()) {
            description = seqDescription.itemAt(0).getStringValue();
        } else {
            description = "";
        }

        final Sequence seqErrorCode = errorMap.get(new StringValue("code"));
        final ErrorCodes.ErrorCode errorCode;
        if(seqErrorCode != null && !seqErrorCode.isEmpty()) {
            errorCode = new ErrorCodes.ErrorCode(((QNameValue)seqErrorCode.itemAt(0)).getQName(), description);
        } else {
            errorCode = ErrorCodes.ERROR;
        }

        final Sequence seqLineNumber = errorMap.get(new StringValue("line-number"));
        final int lineNumber;
        if(seqLineNumber != null && !seqLineNumber.isEmpty()) {
            lineNumber = seqLineNumber.itemAt(0).toJavaObject(int.class);
        } else {
            lineNumber = -1;
        }

        final Sequence seqColumnNumber = errorMap.get(new StringValue("column-number"));
        final int columnNumber;
        if(seqColumnNumber != null && !seqColumnNumber.isEmpty()) {
            columnNumber = seqColumnNumber.itemAt(0).toJavaObject(int.class);
        } else {
            columnNumber = -1;
        }

        return new XPathException(lineNumber, columnNumber, errorCode, description);
    }
}
