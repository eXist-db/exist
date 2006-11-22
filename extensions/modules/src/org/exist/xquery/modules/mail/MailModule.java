/*
 *  eXist Mail Module Extension
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
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

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;


/**
 * eXist Mail Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows email to
 * be sent from XQuery using either SMTP or Sendmail.  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-01
 * @version 1.2
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */
public class MailModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/mail";
	
	public final static String PREFIX = "mail";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(SendEmailFunction.signature, SendEmailFunction.class)
	};
	
	public MailModule()
	{
		super(functions);
	}

	public String getNamespaceURI()
	{
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix()
	{
		return PREFIX;
	}

	public String getDescription()
	{
		return "A module for performing email related functions";
	}
}
