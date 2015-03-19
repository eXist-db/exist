/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2012 The eXist Project
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
package org.exist.xquery.modules.cqlparser;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;

/**
 * Functions for a Contextual Query Language (CQL) parser.
 *
 * @author matej
 * @author ljo
 *
 *
 */
public class ParseCQL extends BasicFunction {

	private static final String OutputTypeString = "string";
	private static final String OutputTypeCQL = "CQL";
	private static final String OutputTypeXCQL = "XCQL";
	
	@SuppressWarnings("unused")
	private static final Logger logger = LogManager.getLogger(ParseCQL.class);
	
    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("parse-cql", CQLParserModule.NAMESPACE_URI, CQLParserModule.PREFIX),
            "Parses expressions in the Contextual Query Language (SRU/CQL) v1.2, " +
	    "returning it back as XCQL or CQL, based on the second parameter, " +
	    "default is XCQL. " +

            "Basic searchClauses (index relation term) can be combined with " + 
	    "boolean operators.",
            new SequenceType[] {
            		new FunctionParameterSequenceType("expression", Type.STRING, Cardinality.ZERO_OR_ONE, "The expression to parse"),
            		new FunctionParameterSequenceType("output-as", Type.STRING, Cardinality.ZERO_OR_ONE, "Output as 'XCQL' or 'CQL'")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the result"));
    
    public ParseCQL(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
    
    	Sequence ret = Sequence.EMPTY_SEQUENCE;
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        String query = args[0].getStringValue();
        
        String output = "XCQL";
        if (!args[1].isEmpty())
            output = args[1].getStringValue();
        
      	  try {
	      CQLParser parser = new CQLParser(CQLParser.V1POINT2);
//      		String local_full_query_string = query;
//      		local_full_query_string = local_full_query_string.replace("-", "%2D");
      		CQLNode query_cql = parser.parse(query);
      		if (output.equals(OutputTypeXCQL)) {
		    String xmlContent = query_cql.toXCQL();
		    if (xmlContent.length() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		    }
		    StringReader reader = new StringReader(xmlContent);
		    SAXAdapter adapter = new SAXAdapter(context);
		    SAXParserFactory factory = SAXParserFactory.newInstance();
		    factory.setNamespaceAware(true);
		    InputSource src = new InputSource(reader);
		    
		    SAXParser saxParser = factory.newSAXParser();
		    XMLReader xr = saxParser.getXMLReader();
		    
		    xr.setContentHandler(adapter);
		    xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
		    xr.parse(src);
		    ret = (DocumentImpl) adapter.getDocument();

      		} else if (output.equals(OutputTypeString)) {
		    ret = new StringValue(query_cql.toString());
      		} else {
		    ret = new StringValue(query_cql.toCQL());
      		}
      		return ret;
      	  }
	  catch (CQLParseException e) {
	      throw new XPathException(this, "An error occurred while parsing the query expression (CQLParseException): " + e.getMessage(), e);      		
	  } catch (SAXException e) {
	      throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
	  } catch (IOException e) {
	      throw new XPathException(this, "An error occurred while parsing the query expression (IOException): " + e.getMessage(), e);
	  } catch (ParserConfigurationException e) {
	      throw new XPathException(this, "Error while constructing XML parser: " + e.getMessage(), e);
        }
    }

}
