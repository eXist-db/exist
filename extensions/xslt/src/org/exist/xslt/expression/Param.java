/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt.expression;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xslt.XSLContext;
import org.exist.xslt.expression.i.Parameted;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <xsl:param
 *   name = qname
 *   select? = expression
 *   as? = sequence-type
 *   required? = "yes" | "no"
 *   tunnel? = "yes" | "no">
 *   <!-- Content: sequence-constructor -->
 * </xsl:param>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Param extends Declaration implements Variable {

    private String attr_select = null;

	private QName name = null;
    private XSLPathExpr select = null;
    private String as = null;
    private Boolean required = null;
    private Boolean tunnel = null;

    public Param(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
		attr_select = null;
		
	    name = null;
	    select = null;
	    as = null;
	    required = null;
	    tunnel = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = new QName(attr.getValue());
		} else if (attr_name.equals(SELECT)) {
			attr_select = attr.getValue();
		} else if (attr_name.equals(AS)) {
			as = attr.getValue();
		} else if (attr_name.equals(REQUIRED)) {
			required = getBoolean(attr.getValue());
		} else if (attr_name.equals(TUNNEL)) {
			tunnel = getBoolean(attr.getValue());
		}
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (contextInfo.getParent() instanceof Parameted) {
			Parameted parameted = (Parameted) contextInfo.getParent();
			parameted.addXSLParam(this);
		} else {
			throw new XPathException("wrong parent");//XXX: error
		}
		
    	super.analyze(contextInfo);

    	if (attr_select != null) {
    		select = new XSLPathExpr(getXSLContext());
		    Pattern.parse(contextInfo.getContext(), attr_select, select);

			_check_(select);
			
    	}
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (name != null && select == null) {
			Variable var = getXSLContext().resolveVariable(name);
			
			Sequence result = var.getValue();
			
			if (as != null)
				return result.convertTo(Type.getType(as));
			
			return result;
			
		} else if (select != null) {
			Sequence result = select.eval(contextSequence, contextItem);
			
			if (as != null)
				result = result.convertTo(Type.getType(as));
			
			context.declareVariable(getName(), result);
			return result;
		}
		throw new XPathException("param can't calculated");//TODO: error?
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:param");

        if (getName() != null) {
        	dumper.display(" name = ");
        	dumper.display(getName());
        }
        if (select != null) {
        	dumper.display(" select = ");
        	dumper.display(select);
        }
        if (as != null) {
        	dumper.display(" as = ");
        	dumper.display(as);
        }
        if (required != null) {
        	dumper.display(" required = ");
        	dumper.display(required);
        }
        if (tunnel != null) {
        	dumper.display(" tunnel = ");
        	dumper.display(tunnel);
        }

        super.dump(dumper);

        dumper.display("</xsl:param>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:param");
        
    	if (getName() != null)
        	result.append(" name = "+getName().toString());    
    	if (select != null)
        	result.append(" select = "+select.toString());    
    	if (as != null)
        	result.append(" as = "+as.toString());    
    	if (required != null)
        	result.append(" required = "+required.toString());    
    	if (tunnel != null)
        	result.append(" tunnel = "+tunnel.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:param> ");
        return result.toString();
    }

	/**
	 * @return the name
	 */
	public QName getName() {
		return name;
	}

	@Override
	public void setValue(Sequence val) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Sequence getValue() {
		// TODO Auto-generated method stub
		if (select != null) {
			try {
				return select.eval(null, null);
			} catch (XPathException e) {
			}
		}
		return null;
		//throw new XPathException("param can't calculated");//TODO: error?
	}

	@Override
	public QName getQName() {
		return getName();
	}

	@Override
	public int getType() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setSequenceType(SequenceType type) throws XPathException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SequenceType getSequenceType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setStaticType(int type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getStaticType() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isInitialized() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void setIsInitialized(boolean initialized) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getDependencies(XQueryContext context) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setStackPosition(int position) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DocumentSet getContextDocs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setContextDocs(DocumentSet docs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void checkType() throws XPathException {
		// TODO Auto-generated method stub
		
	}    
}
