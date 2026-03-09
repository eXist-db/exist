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

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.exist.xquery.FunctionDSL.*;

public class ExtTestErrorFunction extends JUnitIntegrationFunction {

    public ExtTestErrorFunction(final XQueryContext context, final String parentName, final RunNotifier notifier) {
        super("ext-test-error-function",
                params(
                        param("name", Type.STRING, "name of the test"),
                        optParam("error", Type.MAP_ITEM, "error detail of the test. e.g. map { \"code\": $err:code, \"description\": $err:description, \"value\": $err:value, \"module\": $err:module, \"line-number\": $err:line-number, \"column-number\": $err:column-number, \"additional\": $err:additional, \"xquery-stack-trace\": $exerr:xquery-stack-trace, \"java-stack-trace\": $exerr:java-stack-trace}")
                ), context, parentName, notifier);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence arg1 = getCurrentArguments()[0];
        final String name = arg1.itemAt(0).getStringValue();

        final Sequence arg2 = getCurrentArguments().length == 2 ? getCurrentArguments()[1] : null;
        final MapType error = arg2 != null ? (MapType)arg2.itemAt(0) : null;

        final Description description = createTestDescription(name);

        // notify JUnit
        try {
            final XPathException errorReason = errorMapAsXPathException(name, error);
            logOneLineIfXQueryError(name, error);
            notifier.fireTestFailure(new Failure(description, errorReason));
        } catch (final XPathException e) {
            //signal internal failure
            notifier.fireTestFailure(new Failure(description, e));
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private void logOneLineIfXQueryError(final String testName, @Nullable final MapType errorMap) {
        if (errorMap == null) {
            return;
        }
        try {
            final Sequence seqModule = errorMap.get(new StringValue(this, "module"));
            final String modulePath = (seqModule != null && !seqModule.isEmpty())
                ? seqModule.itemAt(0).getStringValue() : null;
            final String file = modulePath != null ? modulePath.replaceFirst("^.*[/\\\\]", "") : "xquery";
            final Sequence seqLine = errorMap.get(new StringValue(this, "line-number"));
            final int line = (seqLine != null && !seqLine.isEmpty())
                ? seqLine.itemAt(0).toJavaObject(int.class) : 0;
            final String oneLine = "XQuery failure: " + file + (line > 0 ? ":" + line + " " : " ") + testName;
            XQueryFailureLog.log(oneLine);
        } catch (final Exception ignored) {
            // do not affect test execution
        }
    }

    private XPathException errorMapAsXPathException(final String testName, final MapType errorMap) throws XPathException {
        if (errorMap == null) {
            final XPathException xpe = new XPathException(-1, -1, ErrorCodes.ERROR, "unknown error");
            xpe.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(suiteName, testName != null ? testName : "eval", "xquery", 0)
            });
            return xpe;
        }
        final String description = getStringFromErrorMap(errorMap, "description", "");
        final ErrorCodes.ErrorCode errorCode = getErrorCodeFromErrorMap(errorMap, description);
        final int lineNumber = getIntFromErrorMap(errorMap, "line-number", -1);
        final int columnNumber = getIntFromErrorMap(errorMap, "column-number", -1);
        final XPathException xpe = new XPathException(lineNumber, columnNumber, errorCode, description);
        final String moduleFileName = getModuleFileNameFromErrorMap(errorMap);
        final StackTraceElement[] javaStack = getJavaStackFromErrorMap(errorMap);
        setStackTraceOnException(xpe, testName, moduleFileName, lineNumber, javaStack);
        return xpe;
    }

    private String getStringFromErrorMap(final MapType errorMap, final String key, final String defaultVal) throws XPathException {
        final Sequence seq = errorMap.get(new StringValue(this, key));
        if (seq != null && !seq.isEmpty()) {
            return seq.itemAt(0).getStringValue();
        }
        return defaultVal;
    }

    private ErrorCodes.ErrorCode getErrorCodeFromErrorMap(final MapType errorMap, final String description) throws XPathException {
        final Sequence seq = errorMap.get(new StringValue(this, "code"));
        if (seq != null && !seq.isEmpty()) {
            return new ErrorCodes.ErrorCode(((QNameValue) seq.itemAt(0)).getQName(), description);
        }
        return ErrorCodes.ERROR;
    }

    private int getIntFromErrorMap(final MapType errorMap, final String key, final int defaultVal) throws XPathException {
        final Sequence seq = errorMap.get(new StringValue(this, key));
        if (seq != null && !seq.isEmpty()) {
            return seq.itemAt(0).toJavaObject(int.class);
        }
        return defaultVal;
    }

    private String getModuleFileNameFromErrorMap(final MapType errorMap) throws XPathException {
        final Sequence seq = errorMap.get(new StringValue(this, "module"));
        if (seq == null || seq.isEmpty()) {
            return null;
        }
        final String path = seq.itemAt(0).getStringValue();
        return path != null ? path.replaceFirst("^.*[/\\\\]", "") : null;
    }

    private StackTraceElement[] getJavaStackFromErrorMap(final MapType errorMap) throws XPathException {
        final Sequence seq = errorMap.get(new StringValue(this, "java-stack-trace"));
        if (seq == null || seq.isEmpty()) {
            return null;
        }
        try {
            return convertStackTraceElements(seq);
        } catch (final NullPointerException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setStackTraceOnException(final XPathException xpe, final String testName,
            final String moduleFileName, final int lineNumber, final StackTraceElement[] javaStack) {
        final StackTraceElement xqueryFrame = new StackTraceElement(
            suiteName,
            testName != null ? testName : "eval",
            moduleFileName != null ? moduleFileName : "xquery",
            lineNumber > 0 ? lineNumber : 0);
        if (javaStack != null && javaStack.length > 0) {
            final StackTraceElement[] fullStack = new StackTraceElement[1 + javaStack.length];
            fullStack[0] = xqueryFrame;
            System.arraycopy(javaStack, 0, fullStack, 1, javaStack.length);
            xpe.setStackTrace(fullStack);
        } else {
            xpe.setStackTrace(new StackTraceElement[] { xqueryFrame });
        }
    }

    private static final Pattern PTN_CAUSED_BY = Pattern.compile("Caused by:\\s([a-zA-Z0-9_$\\.]+)(?::\\s(.+))?");
    private static final Pattern PTN_AT = Pattern.compile("at\\s((?:[a-zA-Z0-9_$]+)(?:\\.[a-zA-Z0-9_$]+)*)\\.((?:[a-zA-Z0-9_$-]+)|(?:<init>))\\(([a-zA-Z0-9_]+\\.java):([0-9]+)\\)");

    protected @Nullable StackTraceElement[] convertStackTraceElements(final Sequence seqJavaStackTrace) throws XPathException {
        StackTraceElement[] traceElements = null;

        final Matcher matcherAt = PTN_AT.matcher("");

        // index 0 is the first `Caused by: ...`
        int i = 1;
        for ( ; i < seqJavaStackTrace.getItemCount(); i++) {
            final String item = seqJavaStackTrace.itemAt(i).getStringValue();
            final StackTraceElement stackTraceElement = convertStackTraceElement(matcherAt, item);
            if (stackTraceElement == null) {
                break;
            }

            if (traceElements == null) {
                traceElements = new StackTraceElement[seqJavaStackTrace.getItemCount() - 1];
            }
            traceElements[i - 1] = stackTraceElement;
        }

        if (traceElements != null && i + 1 < seqJavaStackTrace.getItemCount()) {
            traceElements = Arrays.copyOf(traceElements, i - 2);
        }

        return traceElements;
    }

    private @Nullable StackTraceElement convertStackTraceElement(final Matcher matcherAt, final String s) {
        matcherAt.reset(s);
        if (matcherAt.matches()) {
            final String declaringClass = matcherAt.group(1);
            final String methodName = matcherAt.group(2);
            final String fileName = matcherAt.group(3);
            final String lineNumber = matcherAt.group(4);
            return new StackTraceElement(declaringClass, methodName, fileName, Integer.parseInt(lineNumber));
        } else {
            return null;
        }
    }
}
