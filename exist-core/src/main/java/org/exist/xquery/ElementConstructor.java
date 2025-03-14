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
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.util.XMLNames;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;
import java.util.Iterator;

/**
 * Constructor for element nodes. This class handles both, direct and dynamic
 * element constructors.
 * 
 * @author wolf
 */
public class ElementConstructor extends NodeConstructor {

	private Expression qnameExpr;
	private PathExpr content = null;
	private AttributeConstructor attributes[] = null;
	private QName namespaceDecls[] = null;
	
	protected final static Logger LOG =
		LogManager.getLogger(ElementConstructor.class);
	
	public ElementConstructor(final XQueryContext context) {
	    super(context);
	}
	
	public ElementConstructor(final XQueryContext context, final String qname) {
		super(context);
		this.qnameExpr = new LiteralValue(context, new StringValue(this, qname));
	}
	
	public void setContent(final PathExpr path) {
		this.content = path;
        this.content.setUseStaticContext(true);
    }

    public PathExpr getContent() {
        return content;
    }

    public AttributeConstructor[] getAttributes() {
        return attributes;
    }
    
    public void setNameExpr(final Expression expr) {
		//Deferred atomization (we could have a QNameValue)
	    //this.qnameExpr = new Atomize(context, expr);
		this.qnameExpr = expr;
	}

    public Expression getNameExpr() {
        return qnameExpr;
    }
    
    public void addAttribute(final AttributeConstructor attr) throws XPathException {
        if(attr.isNamespaceDeclaration()) {
            if(XMLConstants.XMLNS_ATTRIBUTE.equals(attr.getQName())) {
                addNamespaceDecl("", attr.getLiteralValue());
            } else {
                try {
                    addNamespaceDecl(QName.extractLocalName(attr.getQName()), attr.getLiteralValue());
                } catch (final QName.IllegalQNameException e) {
                    throw new XPathException(this, ErrorCodes.XPST0081, "Invalid qname " + attr.getQName());
                }
            }
        } else  if(attributes == null) {
            attributes = new AttributeConstructor[1];
            attributes[0] = attr;
        } else {
            final AttributeConstructor natts[] = new AttributeConstructor[attributes.length + 1];
            System.arraycopy(attributes, 0, natts, 0, attributes.length);
            natts[attributes.length] = attr;
            attributes = natts;
        }
	}
	
	public void addNamespaceDecl(final String name, final String uri) throws XPathException {
        final QName qn = new QName(name, uri, XMLConstants.XMLNS_ATTRIBUTE);

        if ((XMLConstants.XML_NS_PREFIX.equals(name) && !Namespaces.XML_NS.equals(uri)) || (XMLConstants.XMLNS_ATTRIBUTE.equals(name) && ! XMLConstants.NULL_NS_URI.equals(uri))) {
            throw new XPathException(this, ErrorCodes.XQST0070, "can not redefine '" + qn + "'");
        }
        
        if (Namespaces.XML_NS.equals(uri) && !XMLConstants.XML_NS_PREFIX.equals(name)) {
            throw new XPathException(this, ErrorCodes.XQST0070, "'" + Namespaces.XML_NS + "' can bind only to '" + XMLConstants.XML_NS_PREFIX + "' prefix");
        }
        
        if (Namespaces.XMLNS_NS.equals(uri) && !XMLConstants.XMLNS_ATTRIBUTE.equals(name)) {
            throw new XPathException(this, ErrorCodes.XQST0070, "'" + Namespaces.XMLNS_NS + "' can bind only to '" + XMLConstants.XMLNS_ATTRIBUTE + "' prefix");
        }
        	
        if (name != null && (!name.isEmpty()) && uri.trim().isEmpty()) {
           throw new XPathException(this, ErrorCodes.XQST0085, "cannot undeclare a prefix " + name + ".");
        }
        addNamespaceDecl(qn);
	}

    private void addNamespaceDecl(final QName qn) throws XPathException {
        if(namespaceDecls == null) {
            namespaceDecls = new QName[1];
            namespaceDecls[0] = qn;
        } else {
            for (QName namespaceDecl : namespaceDecls) {
                if (qn.equals(namespaceDecl)) {
                    throw new XPathException(this, ErrorCodes.XQST0071, "duplicate definition for '" + qn + "'");
                }
            }
            final QName decls[] = new QName[namespaceDecls.length + 1];
            System.arraycopy(namespaceDecls, 0, decls, 0, namespaceDecls.length);
            decls[namespaceDecls.length] = qn;          
            namespaceDecls = decls;
        }
        //context.inScopeNamespaces.put(qn.getLocalPart(), qn.getNamespaceURI());
	}
	
    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        context.pushInScopeNamespaces();
        // declare namespaces
        if(namespaceDecls != null) {
            for (QName namespaceDecl : namespaceDecls) {
                if (XMLConstants.NULL_NS_URI.equals(namespaceDecl.getNamespaceURI())) {
                    // TODO: the specs are unclear here: should we throw XQST0085 or not?
                    context.inScopeNamespaces.remove(namespaceDecl.getLocalPart());
//					if (context.inScopeNamespaces.remove(namespaceDecls[i].getLocalPart()) == null)
//		        		throw new XPathException(getASTNode(), "XQST0085 : can not undefine '" + namespaceDecls[i] + "'");
                } else {
                    context.declareInScopeNamespace(namespaceDecl.getLocalPart(), namespaceDecl.getNamespaceURI());
                }
            }
        }
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        newContextInfo.addFlag(IN_NODE_CONSTRUCTOR);
        qnameExpr.analyze(newContextInfo);
        if(attributes != null) {
            for (AttributeConstructor attribute : attributes) {
                attribute.analyze(newContextInfo);
            }
        }
        if(content != null) {
            content.analyze(newContextInfo);
        }
        context.popInScopeNamespaces();
    }
    
	@Override
	public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        context.expressionStart(this);
		context.pushInScopeNamespaces();
        if (newDocumentContext) {
            context.pushDocumentContext();
        }
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            // declare namespaces
            if (namespaceDecls != null) {
                for (QName namespaceDecl : namespaceDecls) {
                    //if ("".equals(namespaceDecls[i].getNamespaceURI())) {
                    // TODO: the specs are unclear here: should we throw XQST0085 or not?
                    //	context.inScopeNamespaces.remove(namespaceDecls[i].getLocalPart());
//					if (context.inScopeNamespaces.remove(namespaceDecls[i].getLocalPart()) == null)
//		        		throw new XPathException(getAS      TNode(), "XQST0085 : can not undefine '" + namespaceDecls[i] + "'");
                    //} else
                    context.declareInScopeNamespace(namespaceDecl.getLocalPart(), namespaceDecl.getNamespaceURI());
                }
            }
            // process attributes
            final AttributesImpl attrs = new AttributesImpl();
            if (attributes != null) {
                // first, search for xmlns attributes and declare in-scope namespaces
                for (final AttributeConstructor constructor : attributes) {
                    if (constructor.isNamespaceDeclaration()) {
                        final int p = constructor.getQName().indexOf(':');
                        if (p == Constants.STRING_NOT_FOUND) {
                            context.declareInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX, constructor.getLiteralValue());
                        } else {
                            final String prefix = constructor.getQName().substring(p + 1);
                            context.declareInScopeNamespace(prefix, constructor.getLiteralValue());
                        }
                    }
                }
                String v = null;
                // process the remaining attributes
                for (int i = 0; i < attributes.length; i++) {
                    context.proceed(this, builder);
                    final AttributeConstructor constructor = attributes[i];
                    final Sequence attrValues = constructor.eval(contextSequence, contextItem);
                    QName attrQName;
                    try {
                        attrQName = QName.parse(context, constructor.getQName(), XMLConstants.NULL_NS_URI);
                    } catch (final QName.IllegalQNameException e) {
                        throw new XPathException(this, ErrorCodes.XPTY0004, "'" + constructor.getQName() + "' is not a valid attribute name");
                    }

                    final String namespaceURI = attrQName.getNamespaceURI();
                    if (namespaceURI != null && !namespaceURI.isEmpty() && attrQName.getPrefix() == null) {
                        String prefix = context.getPrefixForURI(namespaceURI);

                        if (prefix != null) {
                            attrQName = new QName(attrQName.getLocalPart(), attrQName.getNamespaceURI(), prefix);
                        } else {
                            //generate prefix
                            for (final int n = 1; i < 100; i++) {
                                prefix = "eXnsp" + n;
                                if (context.getURIForPrefix(prefix) == null) {
                                    attrQName = new QName(attrQName.getLocalPart(), attrQName.getNamespaceURI(), prefix);
                                    break;
                                }

                                prefix = null;
                            }
                            if (prefix == null) {
                                throw new XPathException(this, "Prefix can't be generated.");
                            }
                        }
                    }

                    if (attrs.getIndex(attrQName.getNamespaceURI(), attrQName.getLocalPart()) != -1) {
                        throw new XPathException(this, ErrorCodes.XQST0040, "'" + attrQName.getLocalPart() + "' is a duplicate attribute name");
                    }

                    v = DynamicAttributeConstructor.normalize(this, attrQName, attrValues.getStringValue());

                    attrs.addAttribute(attrQName.getNamespaceURI(), attrQName.getLocalPart(),
                            attrQName.getStringValue(), "CDATA", v);
                }
            }
            context.proceed(this, builder);

            // create the element
            final Sequence qnameSeq = qnameExpr.eval(contextSequence, contextItem);
            if (!qnameSeq.hasOne()) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Type error: the node name should evaluate to a single item");
            }
            final Item qnitem = qnameSeq.itemAt(0);

            QName qn;
            if (qnitem instanceof QNameValue) {
                qn = ((QNameValue) qnitem).getQName();
            } else {
                //Do we have the same result than Atomize there ? -pb
                try {
                    qn = QName.parse(context, qnitem.getStringValue());
                } catch (final QName.IllegalQNameException e) {
                    throw new XPathException(this, ErrorCodes.XPTY0004, "'" + qnitem.getStringValue() + "' is not a valid element name");
                } catch (final XPathException e) {
                    e.setLocation(getLine(), getColumn(), getSource());
                    throw e;
                }

                //Use the default namespace if specified
                /*
                 if (qn.getPrefix() == null && context.inScopeNamespaces.get("xmlns") != null) {
                     qn.setNamespaceURI((String)context.inScopeNamespaces.get("xmlns"));
                 }
                 */
                if (qn.getPrefix() == null && context.getInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX) != null) {
                    qn = new QName(qn.getLocalPart(), context.getInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX), qn.getPrefix());
                }
            }

            //Not in the specs but... makes sense
            if (!XMLNames.isName(qn.getLocalPart())) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "'" + qnitem.getStringValue() + "' is not a valid element name");
            }

            // add namespace declaration nodes
            final int nodeNr = builder.startElement(qn, attrs);
            if (namespaceDecls != null) {
                for (QName namespaceDecl : namespaceDecls) {
                    builder.namespaceNode(namespaceDecl);
                }
            }
            // do we need to add a namespace declaration for the current node?
            if (qn.hasNamespace()) {
                if (context.getInScopePrefix(qn.getNamespaceURI()) == null) {
                    String prefix = qn.getPrefix();
                    if (prefix == null) {
                        prefix = XMLConstants.DEFAULT_NS_PREFIX;
                    }
                    context.declareInScopeNamespace(prefix, qn.getNamespaceURI());
                    builder.namespaceNode(new QName(prefix, qn.getNamespaceURI(), XMLConstants.XMLNS_ATTRIBUTE));
                }
            } else if ((qn.getPrefix() == null || qn.getPrefix().isEmpty()) &&
                    context.getInheritedNamespace(XMLConstants.DEFAULT_NS_PREFIX) != null) {
                context.declareInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI);
                builder.namespaceNode(new QName("", XMLConstants.NULL_NS_URI, XMLConstants.XMLNS_ATTRIBUTE));
            } else if (qn.getPrefix() == null || qn.getPrefix().isEmpty()) {
                context.declareInScopeNamespace(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI);
            }
            // process element contents
            if (content != null) {
                content.eval(contextSequence, contextItem);
            }
            builder.endElement();
            final NodeImpl node = builder.getDocument().getNode(nodeNr);
            return node;
        } finally {
            context.popInScopeNamespaces();
            if (newDocumentContext) {
                context.popDocumentContext();
            }
            context.expressionEnd(this);
        }
    }
	
	@Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("element ");
        //TODO : remove curly braces if Qname
        dumper.display("{");
        qnameExpr.dump(dumper);
        dumper.display("} ");
        dumper.display("{");
        dumper.startIndent();
        if(attributes != null) {
			for(int i = 0; i < attributes.length; i++) {
			    if(i > 0) {
			        dumper.nl();
			    }
                final AttributeConstructor attr = attributes[i];
				attr.dump(dumper);
			}
	        dumper.endIndent();
	        dumper.startIndent();
	    }
        if(content != null) {
            for(final Iterator<Expression> i = content.steps.iterator(); i.hasNext(); ) {
                final Expression expr = i.next();
                expr.dump(dumper);
                if(i.hasNext()) {
                    dumper.nl();
                }
            }
            dumper.endIndent().nl();
        }        
        dumper.display("} ");
    }
    
    public String toString() {
    	final StringBuilder result = new StringBuilder();
    	result.append("element ");
        //TODO : remove curly braces if Qname
        result.append("{");    
    	result.append(qnameExpr.toString());
        result.append("} ");    
        result.append("{");        
        if(attributes != null) {
			for(int i = 0; i < attributes.length; i++) {
			    if(i > 0) {
			        result.append(" ");
			    }
                final AttributeConstructor attr = attributes[i];
				result.append(attr.toString());
			}
		}
        if(content != null) {
            for(final Iterator<Expression> i = content.steps.iterator(); i.hasNext(); ) {
                final Expression expr = i.next();
                result.append(expr.toString());
                if(i.hasNext()) {
                    result.append(" ");
                }
            }
        }        
        result.append("} ");
        return result.toString();
    }    
    
	@Override
	public void setPrimaryAxis(final int axis) {
	}

    @Override
    public int getPrimaryAxis() {
        if (content != null) {
            return content.getPrimaryAxis();
        }
        return Constants.UNKNOWN_AXIS;
    }

    @Override
	public void resetState(final boolean postOptimization) {
		super.resetState(postOptimization);
		qnameExpr.resetState(postOptimization);
		if(content != null) {
		    content.resetState(postOptimization);
		}
		if(attributes != null)
            for (final Expression next : attributes) {
                next.resetState(postOptimization);
            }
	}

	@Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitElementConstructor(this);
    }

    @Override
	public int returnsType() {
		return Type.ELEMENT;
	}

}
