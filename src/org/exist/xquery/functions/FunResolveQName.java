/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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

import java.util.Iterator;

import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
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
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class FunResolveQName extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(new QName("resolve-QName",
                                                                                      Function.BUILTIN_FUNCTION_NS), "Returns an xs:QName value (that is, an expanded-QName) by taking an "
                                                                            + "xs:string that has the lexical form of an xs:QName (a string in the form "
                                                                            + "\"prefix:local-name\" or \"local-name\") and resolving it using the in-scope "
                                                                            + "namespaces for a given element.", new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
                                                                                                                                      new SequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE) }, new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE));

    public FunResolveQName(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES",
                                          Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
        }
    
        Sequence qnameSeq = args[0];
        if (qnameSeq.isEmpty()) {
            return EmptySequence.EMPTY_SEQUENCE;
        } else {
        	context.pushInScopeNamespaces();        	        	
            String qnameString = args[0].getStringValue();
            if (QName.isQName(qnameString)) {
                String prefix = QName.extractPrefix(qnameString);

                if (prefix == null) {
                    prefix = "";
                }

                String uri = null;

                NodeValue node = (NodeValue) args[1].itemAt(0);
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    NodeProxy proxy = (NodeProxy) node;
                    NodeSet ancestors = proxy.getAncestors(contextId, true);
                    for (Iterator i = ancestors.iterator(); i.hasNext();) {
                        proxy = (NodeProxy) i.next();
                        ElementImpl e = (ElementImpl) proxy.getNode(); 
                        uri = findNamespaceURI(e, prefix);
                        if (uri != null) {
                            break;
                        }
                    }
                } else {
                    NodeImpl next = (NodeImpl) node;
                    do {
                        uri = findNamespaceURI((org.exist.memtree.ElementImpl) next, prefix);
                        if (uri != null) {
                            break;
                        } else {
                            next = (NodeImpl) next.getParentNode();
                        }
                    } while (next != null && next.getNodeType() == Node.ELEMENT_NODE);
                }

                if (uri == null && prefix != null && !"".equals(prefix)) {
                    throw new XPathException(getASTNode(), "No namespace found for prefix. No binding for prefix '" + prefix
                                             + "' was found. err:FONS0004");
                }
                String localPart = QName.extractLocalName(qnameString);
                QName qn = new QName(localPart, uri);
                qn.setPrefix(prefix);
        
                QNameValue result = new QNameValue(context, qn);
                if (context.getProfiler().isEnabled()) 
                    context.getProfiler().end(this, "", result); 
                
                context.popInScopeNamespaces();
          
                return result;
            } else {
                throw new XPathException(getASTNode(), "Invalid lexical value. '" + qnameString
                                         + "' is not a QName. err:FOCA0002");
            }
        }
    }

    /**
     * The method <code>findNamespaceURI</code>
     *
     * @param element an <code>ElementImpl</code> value
     * @param prefix a <code>String</code> value
     * @return a <code>String</code> value
     */
    public String findNamespaceURI(ElementImpl element, String prefix) {
        String namespaceURI = element.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.length() > 0 && prefix.equals(element.getPrefix())) {
            return namespaceURI;
        }
        if (element.declaresNamespacePrefixes()) {
            for (Iterator i = element.getPrefixes(); i.hasNext();) {            	
                String elementPrefix = (String) i.next();
                context.declareInScopeNamespace(elementPrefix, element.getNamespaceForPrefix(elementPrefix));
                if (prefix.equals(elementPrefix)) {
                    return element.getNamespaceForPrefix(prefix);
                }
            }
        }
        return null;
    }

    /**
     * The method <code>findNamespaceURI</code>
     *
     * @param element an <code>Element</code> value
     * @param prefix a <code>String</code> value
     * @return a <code>String</code> value
     */
    public static String findNamespaceURI(Element element, String prefix) {
        //TODO how do you get to the declared namespaces on plain elements?
        String namespaceURI = element.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.length() > 0 && prefix.equals(element.getPrefix())) {
            return namespaceURI;
        } else {
            return null;
        }
    }

    /**
     * The method <code>findNamespaceURI</code>
     *
     * @param element an <code>org.exist.memtree.ElementImpl</code> value
     * @param prefix a <code>String</code> value
     * @return a <code>String</code> value
     */
    public static String findNamespaceURI(org.exist.memtree.ElementImpl element, String prefix) {
        String namespaceURI = element.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.length() > 0 && prefix.equals(element.getPrefix())) {
            return namespaceURI;
        }
        if (element.declaresNamespacePrefixes()) {
            return (String) element.getNamespaceMap().get(prefix);
        }
        return null;
    }
}
