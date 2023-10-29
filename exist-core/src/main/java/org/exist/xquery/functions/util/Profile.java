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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class Profile extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(Profile.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("enable-profiling", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Enable profiling output within the query. The profiling starts with this function call and will " +
            "end with a call to 'disable-profiling'. Argument $verbosity specifies the verbosity. All " +
            "other profiling options can be configured via the 'declare option exist:profiling ...' in the " +
            "query prolog.",
            new SequenceType[] {
                new FunctionParameterSequenceType("verbosity", Type.INT, Cardinality.EXACTLY_ONE, "The verbosity of the profiling"),
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
        new FunctionSignature(
            new QName("disable-profiling", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Disable profiling output within the query.",
            null,
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE))
    };
    
    public Profile(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
    	
        final Profiler profiler = context.getProfiler();
        if (isCalledAs("enable-profiling")) {
            final int verbosity = ((IntegerValue)args[0].itemAt(0)).getInt();
            profiler.setEnabled(true);
            profiler.setVerbosity(verbosity);
        } else {
            profiler.setEnabled(false);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

}
