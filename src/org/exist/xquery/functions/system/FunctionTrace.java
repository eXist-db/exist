/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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

import org.exist.xquery.*;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.NodeValue;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;

public class FunctionTrace extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName( "trace", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Get a list of running jobs (dba role only).",
                null,
                new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
        ),
        new FunctionSignature(
                new QName( "enable-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Get a list of running jobs (dba role only).",
                null,
                new SequenceType(Type.ITEM, Cardinality.EMPTY)
        ),
        new FunctionSignature(
                new QName( "disable-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Get a list of running jobs (dba role only).",
                null,
                new SequenceType(Type.ITEM, Cardinality.EMPTY)
        )
    };

    public FunctionTrace(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (isCalledAs("enable-tracing"))
            context.getBroker().getConfiguration().setProperty(Profiler.CONFIG_PROPERTY_TRACE, "functions");
        else if (isCalledAs("disable-tracing"))
            context.getBroker().getConfiguration().setProperty(Profiler.CONFIG_PROPERTY_TRACE, "");
        else {
            MemTreeBuilder builder = context.getDocumentBuilder();

            builder.startDocument();
            BrokerPool brokerPool = context.getBroker().getBrokerPool();
            brokerPool.getPerformanceStats().toXML(builder);
            builder.endDocument();
            return (NodeValue)builder.getDocument().getDocumentElement();
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
