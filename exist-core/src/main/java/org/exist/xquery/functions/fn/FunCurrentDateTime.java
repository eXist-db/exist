/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunCurrentDateTime extends Function {

    protected static final Logger logger = LogManager.getLogger(FunCurrentDateTime.class);

    public final static FunctionSignature fnCurrentDateTime =
        new FunctionSignature(
            new QName("current-dateTime", Function.BUILTIN_FUNCTION_NS),
            "Returns the xs:dateTime (with timezone) that is current at some time " +
            "during the evaluation of a query or transformation in which " +
            "fn:current-dateTime() is executed.",
            null,
            //should be xs:dateTimeStamp, need to add support for DATE_TIME_STAMP
            new FunctionReturnSequenceType(Type.DATE_TIME,
                Cardinality.EXACTLY_ONE, "the date-time current " +
                    "within query execution time span"));

    public final static FunctionSignature fnCurrentTime =
        new FunctionSignature(
            new QName("current-time", Function.BUILTIN_FUNCTION_NS),
            "Returns the xs:time (with timezone) that is current at some time " +
            "during the evaluation of a query or transformation in which " +
            "fn:current-time() is executed.",
            null,
            new FunctionReturnSequenceType(Type.TIME,
                Cardinality.EXACTLY_ONE, "the time current " +
                    "within query execution time span"));

    public final static FunctionSignature fnCurrentDate =
        new FunctionSignature(
            new QName("current-date", Function.BUILTIN_FUNCTION_NS),
            "Returns the xs:date (with timezone) that is current at some time " +
            "during the evaluation of a query or transformation in which " +
            "fn:current-date() is executed.",
            null,
            new FunctionReturnSequenceType(Type.DATE, 
                Cardinality.EXACTLY_ONE, "the date current " +
                    "within the query execution time span"));

    public FunCurrentDateTime(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        Sequence result = new DateTimeValue(context.getCalendar());
        if (isCalledAs("current-dateTime")) {
            // do nothing, result already in right form
        } else if (isCalledAs("current-date")) {
            result = result.convertTo(Type.DATE);
        } else if (isCalledAs("current-time")) {
            result = result.convertTo(Type.TIME);
        } else {
            throw new Error("Can't handle function " + mySignature.getName().getLocalPart());
        }
        if (context.getProfiler().isEnabled()) {context.getProfiler().end(this, "", result);}
        return result;
    }
}
