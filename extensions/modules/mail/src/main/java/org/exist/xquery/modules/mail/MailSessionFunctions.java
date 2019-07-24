/*
 *  eXist Mail Module Extension MailSessionFunctions
 *  Copyright (C) 2006-09 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */

package org.exist.xquery.modules.mail;


import java.util.Properties;

import javax.mail.Session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.ParametersExtractor;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist Mail Module Extension GetSession
 * 
 * Get a mail session
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:josemariafg@gmail.com">José María Fernández</a>
 * @serial 2009-03-12
 * @version 1.3
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class MailSessionFunctions extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MailSessionFunctions.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "get-mail-session", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Open's a JavaMail session.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "properties", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "An optional JavaMail session properties in the form <properties><property name=\"\" value=\"\"/></properties>.  The JavaMail properties are spelled out in Appendix A of the JavaMail specifications." )
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the session handle." )
			)
		};

	public MailSessionFunctions( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	@Override
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Properties props = new Properties();
		
		if( args.length == 1 ) {
			// try and get the session properties
			props = ParametersExtractor.parseProperties( ((NodeValue) args[0].itemAt(0)).getNode() );
		}
		
		Session session = Session.getInstance( props, null );
		
		// store the session and return the handle of the session

        return new IntegerValue( MailModule.storeSession( context, session ) );
	}
}
