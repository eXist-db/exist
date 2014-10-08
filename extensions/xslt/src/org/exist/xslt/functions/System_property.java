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
package org.exist.xslt.functions;

import java.io.IOException;
import java.util.Properties;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.system.GetVersion;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * system-property($property-name as xs:string) as xs:string
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class System_property extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("system-property", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
				"The function returns a string representing the value of the system property identified by the name.",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ONE)},
				new SequenceType(Type.STRING, Cardinality.ONE)
		)
	};
	
	/**
	 * @param context
	 */
	public System_property(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		QName name = QName.parse(getContext(), args[0].getStringValue());

		Properties sysProperties = new Properties();
		try {
			sysProperties.load(GetVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		} catch (IOException e) {
			LOG.debug("Unable to load system.properties from class loader");
		}
		return new StringValue(sysProperties.getProperty(name.getStringValue(), ""));

		/*
		if (name.equals("xsl:version")) {
		} else if (name.equals("xsl:vendor")) {
		} else if (name.equals("xsl:vendor-url")) {
		} else if (name.equals("xsl:product-name")) {
		} else if (name.equals("xsl:product-version")) {
		} else if (name.equals("xsl:is-schema-aware")) {
		} else if (name.equals("xsl:supports-serialization")) {
		} else if (name.equals("xsl:supports-backwards-compatibility")) {
		}
		*/
	}
}
