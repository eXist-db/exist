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
 * <!-- Category: instruction -->
 * <xsl:result-document
 *   format? = { qname }
 *   href? = { uri-reference }
 *   validation? = "strict" | "lax" | "preserve" | "strip"
 *   type? = qname
 *   method? = { "xml" | "html" | "xhtml" | "text" | qname-but-not-ncname }
 *   byte-order-mark? = { "yes" | "no" }
 *   cdata-section-elements? = { qnames }
 *   doctype-public? = { string }
 *   doctype-system? = { string }
 *   encoding? = { string }
 *   escape-uri-attributes? = { "yes" | "no" }
 *   include-content-type? = { "yes" | "no" }
 *   indent? = { "yes" | "no" }
 *   media-type? = { string }
 *   normalization-form? = { "NFC" | "NFD" | "NFKC" | "NFKD" | "fully-normalized" | "none" | nmtoken }
 *   omit-xml-declaration? = { "yes" | "no" }
 *   standalone? = { "yes" | "no" | "omit" }
 *   undeclare-prefixes? = { "yes" | "no" }
 *   use-character-maps? = qnames
 *   output-version? = { nmtoken }>
 *   <!-- Content: sequence-constructor -->
 * </xsl:result-document>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ResultDocument extends Declaration {

    private String format = null;
    private String href = null;
    private String validation = null;
    private String type = null;
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
    private String output_version = null;

    public ResultDocument(XSLContext context) {
		super(context);
	}

	public void setToDefaults() {
	    format = null;
	    href = null;
	    validation = null;
	    type = null;
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
	    output_version = null;
	}

	public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
		String attr_name = attr.getLocalName();
			
		if (attr_name.equals(FORMAT)) {
			format = attr.getValue();
		} else if (attr_name.equals(HREF)) {
			href = attr.getValue();
		} else if (attr_name.equals(VALIDATION)) {
			validation = attr.getValue();
		} else if (attr_name.equals(TYPE)) {
			type = attr.getValue();
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
		} else if (attr_name.equals(OUTPUT_VERSION)) {
			output_version = attr.getValue();
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	throw new RuntimeException("eval(Sequence contextSequence, Item contextItem) at "+this.getClass());
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:result-document");
        if (format != null) {
        	dumper.display(" format = ");
        	dumper.display(format);
        }
        if (href != null) {
        	dumper.display(" href = ");
        	dumper.display(href);
        }
        if (validation != null) {
        	dumper.display(" validation = ");
        	dumper.display(validation);
        }
        if (type != null) {
        	dumper.display(" type = ");
        	dumper.display(type);
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
        if (output_version != null) {
        	dumper.display(" output_version = ");
        	dumper.display(output_version);
        }

        super.dump(dumper);

        dumper.display("</xsl:result-document>");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("<xsl:result-document");
        
    	if (format != null)
        	result.append(" format = "+format.toString());    
    	if (href != null)
        	result.append(" href = "+href.toString());    
    	if (validation != null)
        	result.append(" validation = "+validation.toString());    
    	if (type != null)
        	result.append(" type = "+type.toString());    
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
    	if (output_version != null)
        	result.append(" output_version = "+output_version.toString());    

        result.append("> ");
        
        result.append(super.toString());

        result.append("</xsl:result-document> ");
        return result.toString();
    }    
}
