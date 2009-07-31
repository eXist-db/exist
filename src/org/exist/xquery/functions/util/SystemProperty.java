/*
 * Created on 10.10.2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Libary function to retrieve the value of a system property.
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class SystemProperty extends BasicFunction {

	private static final Logger logger = Logger.getLogger(SystemProperty.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("system-property", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the value of a system property. Similar to the corresponding XSLT function. " +
			"Predefined properties are: vendor, vendor-url, product-name, product-version, product-build, and all Java " +
			"system properties.",
			new SequenceType[] { new FunctionParameterSequenceType("property-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the system property to retrieve the value of.") },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the value of the named system property"));
	
	/**
	 * @param context
	 * @param signature
	 */
	public SystemProperty(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)throws XPathException
	{ 
		
		Properties sysProperties = new Properties();
		try
		{
			sysProperties.load(SystemProperty.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		}
		catch (IOException e)
		{
			logger.debug("Unable to load system.properties from class loader");
		}
		
		String key = args[0].getStringValue();
		String value = sysProperties.getProperty(key);
		if(value == null)
			value = System.getProperty(key);
		return value == null ? Sequence.EMPTY_SEQUENCE : new StringValue(value);
	}
}
