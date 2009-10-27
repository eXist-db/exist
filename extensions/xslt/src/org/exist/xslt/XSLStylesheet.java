/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xslt;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;

import org.exist.memtree.MemTreeBuilder;
import org.exist.security.xacml.XACMLSource;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Expression;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.dom.QName;
import org.exist.dom.i.ElementAtExist;
import org.exist.dom.i.NamespaceNodeAtExist;
import org.exist.dom.i.NodeAtExist;
import org.exist.dom.om.Validation;
import org.exist.xslt.expression.AttributeSet;
import org.exist.xslt.expression.Declaration;
import org.exist.xslt.expression.Param;
import org.exist.xslt.expression.Template;
import org.exist.xslt.expression.XSLExpression;
import org.exist.xslt.expression.i.Parameted;
import org.w3c.dom.Attr;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * <(xsl:stylesheet|xsl:transform)
 *   id? = id
 *   extension-element-prefixes? = tokens
 *   exclude-result-prefixes? = tokens
 *   version = number
 *   xpath-default-namespace? = uri
 *   default-validation? = "preserve" | "strip"
 *   default-collation? = uri-list
 *   input-type-annotations? = "preserve" | "strip" | "unspecified">
 *   <!-- Content: (xsl:import*, other-declarations) -->
 * </(xsl:stylesheet|xsl:transform)>
 * 
 * @author shabanovd
 *
 */
public class XSLStylesheet extends Declaration 
	implements CompiledXQuery, Templates, XSLExpression, Parameted {
	
	private Transformer transformer = null; 
	
	public final double version = 2.0;

	private String id = null;
	private String extension_element_prefixes = null;
	private String exclude_result_prefixes = null;
	private String xpath_default_namespace = null;
	private int default_validation = Validation.STRIP;
	private String default_collation = null;
	private String input_type_annotations = null;
	
	private boolean simplified = false; 
	
	private Template rootTemplate = null;
	private Set<Template> templates = new TreeSet<Template>();
	private Map<QName, Template> namedTemplates = new HashMap<QName, Template>();
	
	private Map<String, AttributeSet> attributeSets = new HashMap<String, AttributeSet>();
    
    private Map<QName, org.exist.xquery.Variable> params = null; 

	public XSLStylesheet(XSLContext context) {
		super(context);
		
		//UNDERSTAND: may be better to set at eval???
		context.setXSLStylesheet(this);
		context.setStripWhitespace(true);
	}

	public XSLStylesheet(XSLContext context, boolean embedded) {
		this(context);
		
		this.simplified = embedded;
	}

	public void setToDefaults() {
		id = null;
		extension_element_prefixes = null;
		exclude_result_prefixes = null;
		xpath_default_namespace = null;
		default_validation = Validation.STRIP;
		default_collation = null;
		input_type_annotations = null;
	}

	public void prepareAttribute(Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
		if (attr instanceof NamespaceNodeAtExist) {
			NamespaceNodeAtExist namespace = (NamespaceNodeAtExist) attr;
			if (attr_name.equals(""))
				context.setDefaultElementNamespace(namespace.getValue(), null);

			context.declareInScopeNamespace(attr_name, namespace.getValue());
			return;
		}
		if (attr_name.equals(ID)) {
			id = attr.getValue();
		} else if (attr_name.equals(EXTENSION_ELEMENT_PREFIXES)) {
			extension_element_prefixes = attr.getValue();
		} else if (attr_name.equals(EXCLUDE_RESULT_PREFIXES)) {
			exclude_result_prefixes = attr.getValue();
		} else if (attr_name.equals(VERSION)) {
			if (!Double.valueOf(attr.getValue()).equals(version)) {
				
			}
		} else if (attr_name.equals(XPATH_DEFAULT_NAMESPACE)) {
			xpath_default_namespace = attr.getValue();
		} else if (attr_name.equals(DEFAULT_VALIDATION)) {
			//XXX: fix -> default_validation = attr.getValue();
		} else if (attr_name.equals(DEFAULT_COLLATION)) {
			default_collation = attr.getValue();
		} else if (attr_name.equals(INPUT_TYPE_ANNOTATIONS)) {
			input_type_annotations = attr.getValue();
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.xml.transform.Templates#getOutputProperties()
	 */
	public Properties getOutputProperties() {
    	throw new RuntimeException("Not implemented: getOutputProperties() at "+this.getClass());
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Templates#newTransformer()
	 */
	public Transformer newTransformer() throws TransformerConfigurationException {
        org.exist.xslt.Transformer transformer = new org.exist.xslt.Transformer();
        transformer.setPreparedStylesheet(this);
        return transformer;
	}

	@Override
	public void dump(Writer writer) {
    	throw new RuntimeException("Not implemented: dump(Writer writer) at "+this.getClass());
		
	}

	@Override
	public XACMLSource getSource() {
    	throw new RuntimeException("Not implemented: getSource() at "+this.getClass());
	}

	public boolean isValid() {
    	throw new RuntimeException("Not implemented: isValid() at "+this.getClass());
	}

	@Override
	public void reset() {
    	throw new RuntimeException("Not implemented: reset() at "+this.getClass());
	}

	@Override
	public void setSource(XACMLSource source) {
    	throw new RuntimeException("Not implemented: setSource(XACMLSource source) at "+this.getClass());
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);

		for (Expression expr : steps) {
			if (expr instanceof Template) {
				Template template = (Template) expr;
				if (template.isRootMatch()) {
					if (rootTemplate != null)
						compileError("double root match");//XXX: put error code
					rootTemplate = template;

					if (template.getName() != null)
						namedTemplates.put(template.getName(), template);//UNDERSTAND: check doubles? 

				} else if (template.getName() == null) {
					templates.add(template);
				} else {
					namedTemplates.put(template.getName(), template);//UNDERSTAND: check doubles? 
				}
			} else if (expr instanceof AttributeSet) {
				AttributeSet attributeSet = (AttributeSet) expr;
				attributeSets.put(attributeSet.getName(), attributeSet);
			} 
		}
	}

	public void validate() throws XPathException {
		super.validate();
	}
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	Sequence result;
    	if (simplified)
    		result = super.eval(contextSequence, null);
    	else
    		result = templates(contextSequence, null);
		
		if (result == null)
			result = Sequence.EMPTY_SEQUENCE;
		
		return result;
    }

    public Sequence attributeSet(String name, Sequence contextSequence, Item contextItem) throws XPathException {
    	Sequence result = new ValueSequence();
    	
    	String[] names = name.split(" ");
    	
    	String n;
    	for (int i = 0; i < names.length; i++) {
    		n = names[i];
    		if (attributeSets.containsKey(n)) {
    			result.addAll(attributeSets.get(n).eval(null, null));
    		} else {
    			//UNDERSTAND: error???
    		}
    	}
    	
    	return result;
    }
    
    public Sequence templates(Sequence contextSequence, Item contextItem) throws XPathException {

		boolean matched = false;
		Sequence result = new ValueSequence();

		Sequence currentSequence = contextSequence;
		
		if (contextItem != null)
			currentSequence = contextItem.toSequence();
		
		int pos = context.getContextPosition();
		
//		for (Item item : currentSequence) {
		for (SequenceIterator iterInner = currentSequence.iterate(); iterInner.hasNext();) {
			Item item = iterInner.nextItem();   

			//UNDERSTAND: work around
			if (item instanceof org.w3c.dom.Document) {
				org.w3c.dom.Document document = (org.w3c.dom.Document) item;
				item = (Item) document.getDocumentElement();
			}
			
			context.setContextPosition(pos);
			
			if ((contextItem == null) && (rootTemplate != null)) {
				context.setContextPosition(0);
				Sequence res = rootTemplate.eval(contextSequence, item);
				result.addAll(res);
				matched = true;
			}

			for (Template template : templates) {
				if (template.matched(contextSequence, item)) { //contextSequence
					matched = true;
					
					Sequence res = template.eval(contextSequence, item);
					result.addAll(res);
					if (res.getItemCount() > 0)
						break;
				}
			}
			
			//XXX: performance !?! how to get subelements sequence?? fast...
//			if (!matched) {
//				if (item instanceof ElementAtExist) {
//					ElementAtExist element = (ElementAtExist) item;
//					
//					NodeList children = element.getChildNodes();
//					for (int i=0; i<children.getLength(); i++) {
//						NodeAtExist child = (NodeAtExist)children.item(i);
//						
//						if (child instanceof Text) {
//		                    MemTreeBuilder builder = context.getDocumentBuilder();
//		            		builder.characters(item.getStringValue());
//		            		result.add(item);
//						} else {
//							Sequence res = templates((Sequence)element, (Item)child);
//							if (res != null) {
//								result.addAll(res);
//								matched = true;
//							}
//						}
//					}
//				}
//			}
			
			pos++;
		}
		
		if (matched)
			return result;
		
		return null;
    }
    
    public Sequence template(QName name, Sequence contextSequence, Item contextItem) throws XPathException {
		if (!namedTemplates.containsKey(name))
			throw new XPathException("no template with given name = "+name);//TODO: error?
		
		Sequence result = new ValueSequence();

		Sequence currentSequence = contextSequence;
		
		if (contextItem != null)
			currentSequence = contextItem.toSequence();

		int pos = context.getContextPosition();

		Template template = namedTemplates.get(name);
		for (SequenceIterator iterInner = currentSequence.iterate(); iterInner.hasNext();) {
			Item item = iterInner.nextItem();   
			
			//UNDERSTAND: work around
			if (item instanceof org.w3c.dom.Document) {
				org.w3c.dom.Document document = (org.w3c.dom.Document) item;
				item = (Item) document.getDocumentElement();
			}
			
			context.setContextPosition(pos);

			Sequence res = template.eval(contextSequence, item);
			result.addAll(res);
			if (res.getItemCount() > 0)
				break;

			pos++;
		}
		
		return result;
    }

    public Map<QName, org.exist.xquery.Variable> getXSLParams() {
    	if (params == null)
    		params = new HashMap<QName, org.exist.xquery.Variable>();
    	
    	return params;
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xslt.expression.i.Parameted#addXSLParam(org.exist.xslt.expression.Param)
	 */
	public void addXSLParam(Param param) throws XPathException {
		Map<QName, org.exist.xquery.Variable> params = getXSLParams();
		
		if (params.containsKey(param.getName()))
			compileError(XSLExceptions.ERR_XTSE0580);
		
		 Variable variable = context.declareVariable(param.getName(), param);//UNDERSTAND: global
		 params.put(param.getName(), variable);
	}

	public void setTransformer(Transformer transformer) {
		this.transformer = transformer;
	}

	public Transformer getTransformer() {
		return transformer;
	}
}
