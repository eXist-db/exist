/*
 * Created on 10.10.2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xquery.functions.util;

import java.text.Collator;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Library function to return all collation locales currently known to the system.
 * 
 * @author wolf
 */
public class Collations extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(Collations.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collations", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence of strings containing all collation locales that might be " +
			"specified in the '?lang=' parameter of a collation URI.",
			FunctionSignature.NO_ARGS,
			new FunctionParameterSequenceType("results", Type.STRING, Cardinality.ZERO_OR_MORE, "sequence of strings containing all collation locales that might be " +
			"specified in the '?lang=' parameter of a collation URI."));
	
	/**
	 * @param context
	 */
	public Collations(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		ValueSequence result = new ValueSequence();
		Locale[] locales = Collator.getAvailableLocales();
		String locale;
		for(int i = 0; i < locales.length; i++) {
			locale = locales[i].getLanguage();
			if(locales[i].getCountry().length() > 0)
				locale += '-' + locales[i].getCountry();
			result.add(new StringValue(locale));
		}
		return result;
	}

}
