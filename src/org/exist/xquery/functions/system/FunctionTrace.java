/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * \$Id\$
 */

package org.exist.xquery.functions.system;

import org.apache.log4j.Logger;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;

public class FunctionTrace extends BasicFunction {

    protected final static Logger logger = Logger.getLogger(FunctionTrace.class);

    public final static FunctionSignature signatures[] = {
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
                new SequenceType(Type.ITEM, Cardinality.EMPTY)
        ),
        new FunctionSignature(
                new QName( "enable-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Enable function tracing on the database instance.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("enable", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The enable boolean flag to enable/disable function tracing"),
                    new FunctionParameterSequenceType("tracelog", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                        "The tracelog boolean flag: if set to true, entering/exiting a function will be logged to the logger 'xquery.profiling'")
                },
                new SequenceType(Type.ITEM, Cardinality.EMPTY)
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
                new SequenceType(Type.ITEM, Cardinality.EMPTY)
        )
    };

    public FunctionTrace(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	logger.info("Entering " + SystemModule.PREFIX + ":" + getName().getLocalName());
        if (isCalledAs("clear-trace")) {
        	logger.info("Entering the " + SystemModule.PREFIX + ":clear-trace XQuery function");
            context.getBroker().getBrokerPool().getPerformanceStats().clear();

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
        	logger.info("Exiting " + SystemModule.PREFIX + ":" + getName().getLocalName());
            return BooleanValue.valueOf(context.getBroker().getBrokerPool().getPerformanceStats().isEnabled());
            
        } else {
        	logger.info("Entering the " + SystemModule.PREFIX + ":trace XQuery function");
            context.getProfiler().reset();
            final MemTreeBuilder builder = context.getDocumentBuilder();

            builder.startDocument();
            final BrokerPool brokerPool = context.getBroker().getBrokerPool();
            brokerPool.getPerformanceStats().toXML(builder);
            builder.endDocument();
        	logger.info("Exiting " + SystemModule.PREFIX + ":" + getName().getLocalName());
            return (NodeValue)builder.getDocument().getDocumentElement();
        }
    	logger.info("Exiting " + SystemModule.PREFIX + ":" + getName().getLocalName());
        return Sequence.EMPTY_SEQUENCE;
    }
}
