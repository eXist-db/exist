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
import org.exist.xquery.value.*;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;

public class FunctionTrace extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName( "trace", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Returns function call statistics gathered by the trace log.",
                null,
                new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
        ),
        new FunctionSignature(
                new QName( "enable-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Enable function tracing on the database instance.",
                new SequenceType[] { new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE) },
                new SequenceType(Type.ITEM, Cardinality.EMPTY)
        ),
        new FunctionSignature(
                new QName( "tracing-enabled", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Returns true if function tracing is currently enabled on the database instance.",
                null,
                new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
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
        if (isCalledAs("clear-trace")) {
            context.getBroker().getBrokerPool().getPerformanceStats().clear();

        } else if (isCalledAs("enable-tracing")) {
            boolean enable = args[0].effectiveBooleanValue();
            context.getBroker().getBrokerPool().getPerformanceStats().setEnabled(enable);

        } else if (isCalledAs("tracing-enabled")) {
            return BooleanValue.valueOf(context.getBroker().getBrokerPool().getPerformanceStats().isEnabled());
            
        } else {
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
