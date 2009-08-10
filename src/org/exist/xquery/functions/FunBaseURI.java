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
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * @author wolf
 */
public class FunBaseURI extends BasicFunction {
	protected static final Logger logger = Logger.getLogger(FunBaseURI.class);
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("base-uri", Function.BUILTIN_FUNCTION_NS),
            "Returns the value of the base URI property for the context item.",
			null,
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE, "the base URI from the context item")
		),
        new FunctionSignature(
            new QName("base-uri", Function.BUILTIN_FUNCTION_NS),
            "Returns the value of the base URI property for $uri. If $uri is the empty " +
            "sequence, the empty sequence is returned.",
            new SequenceType[] {
                new FunctionParameterSequenceType("uri", Type.NODE, Cardinality.ZERO_OR_ONE, "The URI") },
            new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE, "the base URI from $uri")
        ),
        new FunctionSignature(
            new QName("static-base-uri", Function.BUILTIN_FUNCTION_NS),
            "Returns the value of the base URI property from the static context. " +
            "If the base-uri property is undefined, the empty sequence is returned.",
            null,
            new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE, "the base URI from the static context")
        )
    };
			
    /**
     * 
     * 
     * @param context 
     * @param signature 
     */
	public FunBaseURI(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result = null;
        NodeValue node = null;
        if (isCalledAs("static-base-uri")) {
            if (context.isBaseURIDeclared()) {
                result = context.getBaseURI();
            } else {
                result = Sequence.EMPTY_SEQUENCE;
            }
        } else {
            if (args.length == 0) {
                if (contextSequence == null || contextSequence.isEmpty()) {
                    logger.error("err:XPDY0002: context sequence is empty and no argument specified");
                    throw new XPathException(this, "err:XPDY0002: context sequence is empty and no argument specified");
                }
                Item item = contextSequence.itemAt(0);
                if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                    logger.error("err:XPTY0004: context item is not a node");
                    throw new XPathException(this, "err:XPTY0004: context item is not a node");
                }
                node = (NodeValue) item;
            } else {
                if (args[0].isEmpty()) {
                    result = Sequence.EMPTY_SEQUENCE;
                } else {
                    node = (NodeValue) args[0].itemAt(0);
                }
            }
        }
        if (result == null && node != null) {
            // This is implemented to be a recursive ascent according to
            // section 2.5 in www.w3.org/TR/xpath-functions 
            // see memtree/ElementImpl and dom/ElementImpl. /ljo
            if (node.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                NodeImpl domNode = (NodeImpl) node.getNode();
                short type = domNode.getNodeType();
                //A direct processing instruction constructor creates a processing instruction node 
                //whose target property is PITarget and whose content property is DirPIContents. 
                //The base-uri property of the node is empty. 
                //The parent property of the node is empty.
                if (type == Node.PROCESSING_INSTRUCTION_NODE) {
                	result = Sequence.EMPTY_SEQUENCE;
                } else if (type == Node.ELEMENT_NODE || type == Node.DOCUMENT_NODE) {
                	//Only elements, document nodes have a base-uri
                    URI relativeURI;
                    URI baseURI;
                    try {
                        relativeURI = new URI(domNode.getBaseURI());
                        baseURI = new URI(context.getBaseURI() + "/");
                    } catch (URISyntaxException e) {
                        logger.error(e.getMessage());
                        throw new XPathException(this, e.getMessage(), e);
                    }
                    if (!"".equals(relativeURI.toString())) {
                        if (relativeURI.isAbsolute()) {
                            result = new AnyURIValue(relativeURI);
                        } else {
                            result = new AnyURIValue(baseURI.resolve(relativeURI));
                        }
                    } else {
                        result = Sequence.EMPTY_SEQUENCE;
                    }

                } else
                	result = Sequence.EMPTY_SEQUENCE;
            } else {
                NodeProxy proxy = (NodeProxy) node;
                short type = proxy.getNodeType();
                // Only elements, document nodes and processing instructions have a base-uri
                if (type == Node.ELEMENT_NODE || type == Node.DOCUMENT_NODE ||
                    type == Node.PROCESSING_INSTRUCTION_NODE) {
                    URI relativeURI;
                    URI baseURI;
                    try {
                       org.exist.dom.NodeImpl baseNode = (org.exist.dom.NodeImpl) proxy.getNode();
                        relativeURI = new URI(baseNode.getBaseURI());
                        baseURI = new URI(context.getBaseURI() + "/");
                    } catch (URISyntaxException e) {
                        logger.error(e.getMessage());
                        throw new XPathException(this, e.getMessage(), e);
                    }
                    if (relativeURI.isAbsolute()) {
                        result = new AnyURIValue(relativeURI);
                    } else {
                        result = new AnyURIValue(baseURI.resolve(relativeURI));
                    }
                } else {
                    result = Sequence.EMPTY_SEQUENCE;
                }
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;
    }
}
