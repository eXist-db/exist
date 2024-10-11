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

package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;

public class FunctionTrace extends BasicFunction {

    protected final static Logger logger = LogManager.getLogger(FunctionTrace.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName( "trace", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Returns function call statistics gathered by the trace log.",
                null,
                new FunctionParameterSequenceType("call-statistics", Type.NODE, Cardinality.EXACTLY_ONE, "the call statistics gathered by the trace")
        ),
        new FunctionSignature(
                new QName( "enable-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Enable function tracing on the database instance.",
                new SequenceType[] { new FunctionParameterSequenceType("enable", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The boolean flag to enable/disable function tracing") },
                new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        ),
        new FunctionSignature(
                new QName( "enable-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Enable function tracing on the database instance.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("enable", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The enable boolean flag to enable/disable function tracing"),
                    new FunctionParameterSequenceType("tracelog", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                        "The tracelog boolean flag: if set to true, entering/exiting a function will be logged to the logger 'xquery.profiling'")
                },
                new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        ),
        new FunctionSignature(
                new QName( "tracing-enabled", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Returns true if function tracing is currently enabled on the database instance.",
                null,
                new FunctionParameterSequenceType("tracing-enabled", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true is tracing is enabled.")
        ),
        new FunctionSignature(
                new QName( "clear-trace", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Clear the global trace log.",
                null,
                new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        )
    };

    public FunctionTrace(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        logger.info("Entering " + SystemModule.PREFIX + ":{}", getName().getLocalPart());
        if (isCalledAs("clear-trace")) {
        	logger.info("Entering the " + SystemModule.PREFIX + ":clear-trace XQuery function");
            context.getBroker().getBrokerPool().getPerformanceStats().reset();

        } else if (isCalledAs("enable-tracing")) {
        	logger.info("Entering the " + SystemModule.PREFIX + ":enable-tracing XQuery function");
            final boolean enable = args[0].effectiveBooleanValue();
            context.getBroker().getBrokerPool().getPerformanceStats().setEnabled(enable);
            if (getArgumentCount() == 2) {
                if (args[1].effectiveBooleanValue())
                    {context.getBroker().getConfiguration().setProperty(Profiler.CONFIG_PROPERTY_TRACELOG, Boolean.TRUE);}
                else
                    {context.getBroker().getConfiguration().setProperty(Profiler.CONFIG_PROPERTY_TRACELOG, Boolean.FALSE);}
            }

        } else if (isCalledAs("tracing-enabled")) {
        	logger.info("Entering the " + SystemModule.PREFIX + ":tracing-enabled XQuery function");
            logger.info("Exiting " + SystemModule.PREFIX + ":{}", getName().getLocalPart());
            return BooleanValue.valueOf(context.getBroker().getBrokerPool().getPerformanceStats().isEnabled());
            
        } else {
        	logger.info("Entering the " + SystemModule.PREFIX + ":trace XQuery function");
            context.getProfiler().reset();

            context.pushDocumentContext();
            try {
                final MemTreeBuilder builder = context.getDocumentBuilder();

                builder.startDocument();
                final BrokerPool brokerPool = context.getBroker().getBrokerPool();
                brokerPool.getPerformanceStats().serialize(builder);
                builder.endDocument();
                logger.info("Exiting " + SystemModule.PREFIX + ":{}", getName().getLocalPart());
                return (NodeValue) builder.getDocument().getDocumentElement();
            } finally {
                context.popDocumentContext();
            }
        }
        logger.info("Exiting " + SystemModule.PREFIX + ":{}", getName().getLocalPart());
        return Sequence.EMPTY_SEQUENCE;
    }
}
