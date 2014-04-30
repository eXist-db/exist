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
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xslt.XSLContext;
import org.w3c.dom.Attr;

/**
 * <!-- Category: declaration -->
 * <xsl:output
 *   name? = qname
 *   method? = "xml" | "html" | "xhtml" | "text" | qname-but-not-ncname
 *   byte-order-mark? = "yes" | "no"
 *   cdata-section-elements? = qnames
 *   doctype-public? = string
 *   doctype-system? = string
 *   encoding? = string
 *   escape-uri-attributes? = "yes" | "no"
 *   include-content-type? = "yes" | "no"
 *   indent? = "yes" | "no"
 *   media-type? = string
 *   normalization-form? = "NFC" | "NFD" | "NFKC" | "NFKD" | "fully-normalized" | "none" | nmtoken
 *   omit-xml-declaration? = "yes" | "no"
 *   standalone? = "yes" | "no" | "omit"
 *   undeclare-prefixes? = "yes" | "no"
 *   use-character-maps? = qnames
 *   version? = nmtoken />
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Output extends Declaration {

    private String name = null;
    private String method = null;
    private Boolean byte_order_mark = null;
    private String cdata_section_elements = null;
    private String doctype_public = null;
    private String doctype_system = null;
    private String encoding = null;
    private Boolean escape_uri_attributes = null;
    private Boolean include_content_type = null;
    private Boolean indent = null;
    private String media_type = null;
    private String normalization_form = null;
    private Boolean omit_xml_declaration = null;
    private String standalone = null;
    private Boolean undeclare_prefixes = null;
    private String use_character_maps = null;
    private String version = null;

    public Output(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    name = null;
	    method = null;
	    byte_order_mark = null;
	    cdata_section_elements = null;
	    doctype_public = null;
	    doctype_system = null;
	    encoding = null;
	    escape_uri_attributes = null;
	    include_content_type = null;
	    indent = null;
	    media_type = null;
	    normalization_form = null;
	    omit_xml_declaration = null;
	    standalone = null;
	    undeclare_prefixes = null;
	    use_character_maps = null;
	    version = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(NAME)) {
			name = attr.getValue();
		} else if (attr_name.equals(METHOD)) {
			method = attr.getValue();
		} else if (attr_name.equals(BYTE_ORDER_MARK)) {
			byte_order_mark = getBoolean(attr.getValue());
		} else if (attr_name.equals(CDATA_SECTION_ELEMENTS)) {
			cdata_section_elements = attr.getValue();
		} else if (attr_name.equals(DOCTYPE_PUBLIC)) {
			doctype_public = attr.getValue();
		} else if (attr_name.equals(DOCTYPE_SYSTEM)) {
			doctype_system = attr.getValue();
		} else if (attr_name.equals(ENCODING)) {
			encoding = attr.getValue();
		} else if (attr_name.equals(ESCAPE_URI_ATTRIBUTES)) {
			escape_uri_attributes = getBoolean(attr.getValue());
		} else if (attr_name.equals(INCLUDE_CONTENT_TYPE)) {
			include_content_type = getBoolean(attr.getValue());
		} else if (attr_name.equals(INDENT)) {
			indent = getBoolean(attr.getValue());
		} else if (attr_name.equals(MEDIA_TYPE)) {
			media_type = attr.getValue();
		} else if (attr_name.equals(NORMALIZATION_FORM)) {
			normalization_form = attr.getValue();
		} else if (attr_name.equals(OMIT_XML_DECLARATION)) {
			omit_xml_declaration = getBoolean(attr.getValue());
		} else if (attr_name.equals(STANDALONE)) {
			standalone = attr.getValue();
		} else if (attr_name.equals(UNDECLARE_PREFIXES)) {
			undeclare_prefixes = getBoolean(attr.getValue());
		} else if (attr_name.equals(USE_CHARACTER_MAPS)) {
			use_character_maps = attr.getValue();
		} else if (attr_name.equals(VERSION)) {
			version = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		//TODO: output eval
    	//throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	    //	default output properties for the XML serialization
//	    public final static Properties OUTPUT_PROPERTIES = new Properties();
//	    static {
//	        OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
//	        OUTPUT_PROPERTIES.setProperty(OutputKeys.ENCODING, "UTF-8");
//	        OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
//	        OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
//	        OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
//	    }
		return null;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:output");
        if (name != null) {
        	dumper.display(" name = ");
        	dumper.display(name);
        }
        if (method != null) {
        	dumper.display(" method = ");
        	dumper.display(method);
        }
        if (byte_order_mark != null) {
        	dumper.display(" byte_order_mark = ");
        	dumper.display(byte_order_mark);
        }
        if (cdata_section_elements != null) {
        	dumper.display(" cdata_section_elements = ");
        	dumper.display(cdata_section_elements);
        }
        if (doctype_public != null) {
        	dumper.display(" doctype_public = ");
        	dumper.display(doctype_public);
        }
        if (doctype_system != null) {
        	dumper.display(" doctype_system = ");
        	dumper.display(doctype_system);
        }
        if (encoding != null) {
        	dumper.display(" encoding = ");
        	dumper.display(encoding);
        }
        if (escape_uri_attributes != null) {
        	dumper.display(" escape_uri_attributes = ");
        	dumper.display(escape_uri_attributes);
        }
        if (include_content_type != null) {
        	dumper.display(" include_content_type = ");
        	dumper.display(include_content_type);
        }
        if (indent != null) {
        	dumper.display(" indent = ");
        	dumper.display(indent);
        }
        if (media_type != null) {
        	dumper.display(" media_type = ");
        	dumper.display(media_type);
        }
        if (normalization_form != null) {
        	dumper.display(" normalization_form = ");
        	dumper.display(normalization_form);
        }
        if (omit_xml_declaration != null) {
        	dumper.display(" omit_xml_declaration = ");
        	dumper.display(omit_xml_declaration);
        }
        if (standalone != null) {
        	dumper.display(" standalone = ");
        	dumper.display(standalone);
        }
        if (undeclare_prefixes != null) {
        	dumper.display(" undeclare_prefixes = ");
        	dumper.display(undeclare_prefixes);
        }
        if (use_character_maps != null) {
        	dumper.display(" use_character_maps = ");
        	dumper.display(use_character_maps);
        }
        if (version != null) {
        	dumper.display(" version = ");
        	dumper.display(version);
        }

        super.dump(dumper);

        dumper.display("</xsl:output>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:output");
        
    	if (name != null)
        	result.append(" name = "+name.toString());    
    	if (method != null)
        	result.append(" method = "+method.toString());    
    	if (byte_order_mark != null)
        	result.append(" byte_order_mark = "+byte_order_mark.toString());    
    	if (cdata_section_elements != null)
        	result.append(" cdata_section_elements = "+cdata_section_elements.toString());    
    	if (doctype_public != null)
        	result.append(" doctype_public = "+doctype_public.toString());    
    	if (doctype_system != null)
        	result.append(" doctype_system = "+doctype_system.toString());    
    	if (encoding != null)
        	result.append(" encoding = "+encoding.toString());    
    	if (escape_uri_attributes != null)
        	result.append(" escape_uri_attributes = "+escape_uri_attributes.toString());    
    	if (include_content_type != null)
        	result.append(" include_content_type = "+include_content_type.toString());    
    	if (indent != null)
        	result.append(" indent = "+indent.toString());    
    	if (media_type != null)
        	result.append(" media_type = "+media_type.toString());    
    	if (normalization_form != null)
        	result.append(" normalization_form = "+normalization_form.toString());    
    	if (omit_xml_declaration != null)
        	result.append(" omit_xml_declaration = "+omit_xml_declaration.toString());    
    	if (standalone != null)
        	result.append(" standalone = "+standalone.toString());    
    	if (undeclare_prefixes != null)
        	result.append(" undeclare_prefixes = "+undeclare_prefixes.toString());    
    	if (use_character_maps != null)
        	result.append(" use_character_maps = "+use_character_maps.toString());    
    	if (version != null)
        	result.append(" version = "+version.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:output> ");
        return result.toString();
    }    
}
