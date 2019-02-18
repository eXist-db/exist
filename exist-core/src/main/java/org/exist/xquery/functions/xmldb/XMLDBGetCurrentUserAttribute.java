/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
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
 *  $Id: XMLDBGetCurrentUser.java 10742 2009-12-15 11:35:15Z shabanovd $
 */
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.security.AXSchemaType;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.GetPrincipalMetadataFunction;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Loren Cahlander
 *
 */
@Deprecated
public class XMLDBGetCurrentUserAttribute extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(XMLDBGetCurrentUserAttribute.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-current-user-attribute", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the attribute of the current user account.",
			new SequenceType[] {
                new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user attribute")
            },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the attribute value of the current user"),
            GetPrincipalMetadataFunction.FNS_GET_ACCOUNT_METADATA
        );
	
	public XMLDBGetCurrentUserAttribute(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException
	{
        if( args[0].isEmpty() ) {
            return Sequence.EMPTY_SEQUENCE;
        }
        
        final String attributeName = args[0].getStringValue();
        
        final AXSchemaType type = AXSchemaType.valueOfNamespace(attributeName);
        
        if (type == null)
        	{return Sequence.EMPTY_SEQUENCE;} //UNDERSTAND: error?
        
        final Object value = context.getSubject().getMetadataValue(type);
        if (value != null) {
            final StringValue stringValue = new StringValue((String) value);
            return stringValue;
        } else {
            return Sequence.EMPTY_SEQUENCE;
        }
	}
}
