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

import org.exist.dom.QName;
import org.exist.xquery.ExpressionVisitor;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.junit.runner.notification.RunNotifier;

import javax.xml.XMLConstants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.exist.xquery.FunctionDSL.functionSignature;
import static org.exist.xquery.FunctionDSL.returnsNothing;

/**
 * Base class for XQuery functions that integrate with JUnit.
 *
 * @author Adam Retter
 */
public abstract class JUnitIntegrationFunction extends UserDefinedFunction {

    protected final String suiteName;
    protected final RunNotifier notifier;

    public JUnitIntegrationFunction(final String functionName, final FunctionParameterSequenceType[] paramTypes, final XQueryContext context, final String suiteName, final RunNotifier notifier) {
        super(context,
                functionSignature(
                        new QName(functionName,  XMLConstants.NULL_NS_URI),
                        "External JUnit integration function",
                        returnsNothing(),
                        paramTypes
                ));
        this.suiteName = suiteName;
        this.notifier = notifier;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        if (visited) {
            return;
        }
        visited = true;
    }
}
