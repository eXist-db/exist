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

    protected static final Logger logger = LogManager.getLogger(FunBaseURI.class);

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
            new SequenceType[] {
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

    public FunBaseURI(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
     * org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        Sequence result = null;
        NodeValue nodeValue = null;
        if (isCalledAs("static-base-uri")) {
            if (context.isBaseURIDeclared()) {
                result = context.getBaseURI();
                if (!((AnyURIValue) result).toURI().isAbsolute()) {
//                    throw new XPathException(this, ErrorCodes.XPST0001, "");
                    LOG.debug("URI is not absolute");
                    result = Sequence.EMPTY_SEQUENCE;
                }
            } else {
                result = Sequence.EMPTY_SEQUENCE;
            }
        } else {
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
                    result = Sequence.EMPTY_SEQUENCE;
                } else {
                    nodeValue = (NodeValue) args[0].itemAt(0);
                }
            }
        }

        if (result == null && nodeValue != null) {
            result = Sequence.EMPTY_SEQUENCE;
            // This is implemented to be a recursive ascent according to
            // section 2.5 in www.w3.org/TR/xpath-functions 
            // see memtree/ElementImpl and dom/ElementImpl. /ljo
            final Node node = nodeValue.getNode();
            final short type = node.getNodeType();
            //A direct processing instruction constructor creates a processing instruction node 
            //whose target property is PITarget and whose content property is DirPIContents. 
            //The base-uri property of the node is empty. 
            //The parent property of the node is empty.


            // Namespace node does not exist in xquery
            final short[] quickStops = { Node.ELEMENT_NODE, Node.ATTRIBUTE_NODE,
                    Node.PROCESSING_INSTRUCTION_NODE, Node.COMMENT_NODE, Node.TEXT_NODE};
            
            if(type == Node.DOCUMENT_NODE){
                final AnyURIValue contextBaseURI = context.getBaseURI();

                if(StringUtils.isBlank(contextBaseURI.getStringValue())){
                    final Document ownerDocument = nodeValue.getOwnerDocument();
                    if(ownerDocument==null){
                        return Sequence.EMPTY_SEQUENCE;
                    } else {
                        return new AnyURIValue(ownerDocument.getDocumentURI());
                    }
                } else {
                    return contextBaseURI;
                }

            }

            if (!ArrayUtils.contains(quickStops, type)) {
                return Sequence.EMPTY_SEQUENCE;
            }


            URI relativeURI = null;
            URI baseURI = null;
            try {
                if (node != null) {
                    final String uri = node.getBaseURI();
                    if (StringUtils.isNotBlank(uri)) {
                        relativeURI = new URI(uri);
                        baseURI = new URI(context.getBaseURI() + "/");
                    }
                }
            } catch (final URISyntaxException e) {
                throw new XPathException(this, ErrorCodes.ERROR, e.getMessage());
            }

            if (relativeURI != null) {
                if (!("".equals(relativeURI.toString()))) {
                    if (relativeURI.isAbsolute()) {
                        result = new AnyURIValue(relativeURI);
                    } else {
                        result = new AnyURIValue(baseURI.resolve(relativeURI));
                    }

                } else {
                    result = new AnyURIValue(baseURI);
                }
            }
        }


        return result;
    }
}
