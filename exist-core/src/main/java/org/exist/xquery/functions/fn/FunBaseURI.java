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

import org.apache.commons.lang3.StringUtils;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * @author wolf
 */
public class FunBaseURI extends BasicFunction {

    public static final String FS_BASE_URI = "base-uri";
    public static final String FS_STATIC_BASE_URI = "static-base-uri";

    static final FunctionSignature FS_BASE_URI_0 = functionSignature(
            FS_BASE_URI,
            "Returns the base URI of the context node. " +
                    "It is equivalent to calling fn:base-uri(.).",
            returnsOpt(Type.ANY_URI, "The base URI from the context node.")
    );

    static final FunctionSignature FS_STATIC_BASE_URI_0 = functionSignature(
            FS_STATIC_BASE_URI,
            "Returns the value of the static base URI property from the static context. " +
                    "If the base-uri property is undefined, the empty sequence is returned.",
            returnsOpt(Type.ANY_URI, "The base URI from the static context.")
    );

    private static final FunctionParameterSequenceType FS_PARAM_NODE
            = optParam("arg", Type.NODE, "The node.");

    static final FunctionSignature FS_BASE_URI_1 = functionSignature(
            FS_BASE_URI,
            "Returns the base URI of a node." +
                    "If $arg is the empty sequence, the empty sequence is returned.",
            returnsOpt(Type.ANY_URI, "The base URI from $arg."),
            FS_PARAM_NODE
    );

    // Namespace node does not exist in xquery, hence left out of array.
    private static final Set<Short> CANDIDATE_NODE_TYPES = Set.of(
            Node.DOCUMENT_NODE, Node.ELEMENT_NODE, Node.ATTRIBUTE_NODE, Node.TEXT_NODE,
            Node.COMMENT_NODE, Node.PROCESSING_INSTRUCTION_NODE
    );

    public FunBaseURI(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
     * org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        if (isCalledAs(FS_STATIC_BASE_URI)) {
            if (context.isBaseURIDeclared()) {
                // use whatever value is defined, does not need to be absolute
                return context.getBaseURI();

            } else {
                // Quick escape
                return Sequence.EMPTY_SEQUENCE;
            }
        }


        NodeValue nodeValue;

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

        // Quick escape
        if (!CANDIDATE_NODE_TYPES.contains(type)) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Constructed Comment nodes/PIs/Attributes do not have a baseURI according tests
        if ((node.getNodeType() == Node.COMMENT_NODE
                || node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                || node.getNodeType() == Node.ATTRIBUTE_NODE)
                && nodeValue.getOwnerDocument().getDocumentElement() == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

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
                    result = new AnyURIValue(this, newURI);

                } else {
                    // just take xml:base value
                    result = new AnyURIValue(this, nbURI);
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
