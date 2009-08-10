/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2004-09 The eXist Team
 *
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
package org.exist.xquery.functions.util;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 */
public class ModuleInfo extends BasicFunction {
	
	protected static final FunctionParameterSequenceType NAMESPACE_URI_PARAMETER = new FunctionParameterSequenceType("namespace-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The namespace URI of the module");

	protected static final Logger logger = Logger.getLogger(ModuleInfo.class);

	public final static FunctionSignature registeredModulesSig =
		new FunctionSignature(
			new QName("registered-modules", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence containing the namespace URIs of all modules " +
			"currently known to the system, including built in and imported modules.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the sequence of all of the active function modules namespace URIs"));
	
	public final static FunctionSignature registeredModuleSig =
		new FunctionSignature(
			new QName("is-module-registered", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a Boolean value if the module identified by the namespace URI is registered.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the namespace URI is registered as an active function module"));
	
	public final static FunctionSignature moduleDescriptionSig =
		new FunctionSignature(
			new QName("get-module-description", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a short description of the module identified by the namespace URI.",
			new SequenceType[] { NAMESPACE_URI_PARAMETER },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the description of the active function module identified by the namespace URI"));
	
	/**
	 * @param context
	 * @param signature
	 */
	public ModuleInfo(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		if("get-module-description".equals(getSignature().getName().getLocalName())) {
			String uri = args[0].getStringValue();
			Module module = context.getModule(uri);
			if(module == null)
				throw new XPathException(this, "No module found matching namespace URI: " + uri);
			return new StringValue(module.getDescription());
		} else if ("is-module-registered".equals(getSignature().getName().getLocalName())) {
			String uri = args[0].getStringValue();
			Module module = context.getModule(uri);
			return new BooleanValue(module != null);
		} else {
			ValueSequence resultSeq = new ValueSequence();
			for(Iterator i = context.getRootModules(); i.hasNext(); ) {
				Module module = (Module)i.next();
				resultSeq.add(new StringValue(module.getNamespaceURI()));
			}
			return resultSeq;
		}
	}

}
