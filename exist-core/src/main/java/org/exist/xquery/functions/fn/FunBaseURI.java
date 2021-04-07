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
package org.exist.xquery.functions.fn;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author wolf
 */
public class FunBaseURI extends BasicFunction {

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("base-uri", Function.BUILTIN_FUNCTION_NS),
                    "Returns the value of the base URI property for the context item.",
                    null,
                    new FunctionReturnSequenceType(Type.ANY_URI,
                            Cardinality.ZERO_OR_ONE, "The base URI from the context item")
            ),
            new FunctionSignature(
                    new QName("base-uri", Function.BUILTIN_FUNCTION_NS),
                    "Returns the value of the base URI property for $uri. " +
                            "If $uri is the empty sequence, the empty sequence is returned.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("uri", Type.NODE,
                                    Cardinality.ZERO_OR_ONE, "The URI")
                    },
                    new FunctionReturnSequenceType(Type.ANY_URI,
                            Cardinality.ZERO_OR_ONE, "the base URI from $uri")
            ),
            new FunctionSignature(
                    new QName("static-base-uri", Function.BUILTIN_FUNCTION_NS),
                    "Returns the value of the base URI property from the static context. " +
                            "If the base-uri property is undefined, the empty sequence is returned.",
                    null,
                    new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE,
                            "The base URI from the static context")
            )
    };
    protected static final Logger logger = LogManager.getLogger(FunBaseURI.class);

    public FunBaseURI(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
     * org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        if (isCalledAs("static-base-uri")) {
            if (context.isBaseURIDeclared()) {
                // use whatever value is defined, does not need to be absolute
                return context.getBaseURI();

            } else {
                // Quick escape
                return Sequence.EMPTY_SEQUENCE;
            }
        }


        NodeValue nodeValue = null;

        // Called as base-uri
        if (args.length == 0) {
            if (contextSequence == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "The context item is absent");
            }
            if (contextSequence.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final Item item = contextSequence.itemAt(0);
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node");
            }
            nodeValue = (NodeValue) item;


        } else {
            if (args[0].isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            } else {
                nodeValue = (NodeValue) args[0].itemAt(0);
            }
        }

        Sequence result = Sequence.EMPTY_SEQUENCE;

        final Node node = nodeValue.getNode();
        final short type = node.getNodeType();

        // Namespace node does not exist in xquery, hence left out of array.
        final short[] quickStops = {Node.ELEMENT_NODE, Node.ATTRIBUTE_NODE,
                Node.PROCESSING_INSTRUCTION_NODE, Node.COMMENT_NODE, Node.TEXT_NODE,
                Node.DOCUMENT_NODE};

        // Quick escape
        if (!ArrayUtils.contains(quickStops, type)) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Constructed Comment nodes/PIs /Attributes do not have a baseURI accoring tests
        if ((node.getNodeType() == Node.COMMENT_NODE
                || node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                || node.getNodeType() == Node.ATTRIBUTE_NODE)
                && nodeValue.getOwnerDocument().getDocumentElement() == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // null when no document
        final Document ownerDocument = node.getOwnerDocument();
        final String ownerDocumentURI = (ownerDocument == null) ? null : ownerDocument.getDocumentURI();

        // "" when not set // check with isBaseURIDeclared()
        final AnyURIValue contextBaseURI = context.getBaseURI();
        final boolean hasContextBaseURI = context.isBaseURIDeclared();

        // "" when not set, can be null
        final String nodeBaseURI = node.getBaseURI();
        final boolean hasNodeBaseURI = StringUtils.isNotBlank(nodeBaseURI);

        try {
            if (hasNodeBaseURI) {
                // xml:base is defined
                URI nbURI = new URI(nodeBaseURI);
                final boolean nbURIAbsolute = nbURI.isAbsolute();

                if (!nbURIAbsolute && hasContextBaseURI) {
                    // when xml:base is not an absolute URL and there is a contextURI
                    // join them
                    final URI newURI = contextBaseURI.toURI().resolve(nodeBaseURI);
                    result = new AnyURIValue(newURI);

                } else {
                    // just take xml:base value
                    result = new AnyURIValue(nbURI);
                }

            } else if (hasContextBaseURI) {
                // if there is no xml:base, take the root document, if present.
                result = contextBaseURI;
            }

        } catch (URISyntaxException e) {
            LOG.debug(e.getMessage());
        }

        return result;
    }
}
