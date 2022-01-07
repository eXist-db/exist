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
import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.InMemoryNodeSet;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Option;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.util.Properties;

public class Expand extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(Expand.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("expand", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Creates an in-memory copy of the passed node set, using the specified " +
            "serialization options. By default, full-text match terms will be " +
            "tagged with &lt;exist:match&gt; and XIncludes will be expanded.",
            new SequenceType[]{
                new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_MORE, "The node(s) to create in-memory copies of.")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the results")),
        new FunctionSignature(
            new QName("expand", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Creates an in-memory copy of the passed node set, using the specified " +
            "serialization options. By default, full-text match terms will be " +
            "tagged with &lt;exist:match&gt; and XIncludes will be expanded. Serialization " +
            "parameters can be set in the second argument, which accepts the same parameters " +
            "as the exist:serialize option.",
            new SequenceType[]{
                new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_MORE, "The node(s) to create in-memory copies of."),
                new FunctionParameterSequenceType("serialization-parameters", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization parameters")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the results"))
    };

    public Expand(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

    	if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
    	}

        // apply serialization options set on the XQuery context
        final Properties serializeOptions = new Properties();
        serializeOptions.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        serializeOptions.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        if (getArgumentCount() == 2) {
            final String serOpts = args[1].getStringValue();
            final String[] contents = Option.tokenize(serOpts);
            for (String content : contents) {
                final String[] pair = Option.parseKeyValuePair(content);
                if (pair == null) {
                    throw new XPathException(this, "Found invalid serialization option: " + content);
                }
                logger.debug("Setting serialization property: {} = {}", pair[0], pair[1]);
                serializeOptions.setProperty(pair[0], pair[1]);
            }
        } else
            {context.checkOptions(serializeOptions);}

        context.pushDocumentContext();
        try {
            final InMemoryNodeSet result = new InMemoryNodeSet();

            final MemTreeBuilder builder = new MemTreeBuilder(getContext());
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
            for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                final NodeValue next = (NodeValue) i.nextItem();

                builder.startDocument();
                next.toSAX(context.getBroker(), receiver, serializeOptions);
                builder.endDocument();

                final short nodeType = ((INodeHandle) next).getNodeType();
                if (Node.DOCUMENT_NODE != nodeType) {
                    result.add(builder.getDocument().getNode(1));
                } else {
                    result.add(builder.getDocument());
                }

                builder.reset(getContext());
            }
            return result;
        } catch (final SAXException e) {
            throw new XPathException(this, e);
        } finally {
            context.popDocumentContext();
        }
    }

}
