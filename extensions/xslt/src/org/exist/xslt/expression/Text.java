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

import org.exist.interpreter.ContextAtExist;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: instruction -->
 * <xsl:text
 *   [disable-output-escaping]? = "yes" | "no">
 *   <!-- Content: #PCDATA -->
 * </xsl:text>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Text extends SimpleConstructor {

	private String text = null;

	private Boolean disable_output_escaping = null;

    private boolean isWhitespaceOnly = false;

    public Text(XSLContext context) {
		super(context);
	}

    public Text(XSLContext context, String text) throws XPathException {
		super(context);
		
		this.text = text;
	}

    public void setToDefaults() {
		disable_output_escaping = null;
		
		sequenceItSelf = false;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(DISABLE_OUTPUT_ESCAPING)) {
			disable_output_escaping = getBoolean(attr.getValue());
		}
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (text == null) {
			isWhitespaceOnly = false;
			super.analyze(contextInfo);
		} else {
			isWhitespaceOnly = true;
			this.text = StringValue.expand(text);
			for(int i = 0; i < text.length(); i++)
				if(!isWhiteSpace(text.charAt(i))) {
					isWhitespaceOnly = false;
					break;
				}
		}
	}
	
	//TODO: The text node does not have an ancestor element that has an xml:space attribute with a value of preserve, unless there is a closer ancestor element having an xml:space attribute with a value of default.
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		
		if(isWhitespaceOnly) // && context.stripWhitespace())
			return Sequence.EMPTY_SEQUENCE;
        
		if (sequenceItSelf) {
			if (text != null)
				return new StringValue(text);
				
			getContext().setStripWhitespace(false);
			for (Expression expr : steps) {
				if (expr instanceof Text) {
					return new StringValue(((Text) expr).text);
				}
				throw new XPathException("unsupported subelement");
			}
			getContext().setStripWhitespace(true);
		}


		if (newDocumentContext)
            context.pushDocumentContext();
        
		try {
            MemTreeBuilder builder = context.getDocumentBuilder();
            context.proceed(this, builder);
            int nodeNr = builder.characters(text);
            NodeImpl node = builder.getDocument().getNode(nodeNr);
            return node;
        } finally {
            if (newDocumentContext)
                context.popDocumentContext();
        }

//		return super.eval(contextSequence, contextItem);
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:text");

        if (disable_output_escaping != null) {
        	dumper.display(" disable_output_escaping = ");
        	dumper.display(disable_output_escaping);
        }
        dumper.display(">");
        
        super.dump(dumper);

        dumper.display("</xsl:text>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:text");
        
    	if (disable_output_escaping != null)
        	result.append(" disable_output_escaping = "+disable_output_escaping.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:text> ");
        return result.toString();
    }    

	@Override
	public boolean allowMixedNodesInReturn() {
		return true;
	}

	protected final static boolean isWhiteSpace(char ch) {
		return (ch == 0x20) || (ch == 0x09) || (ch == 0xD) || (ch == 0xA);
	}
}
