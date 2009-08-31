/* eXist Native XML Database
 * Copyright (C) 2006-2009, The eXist Project
 * http://exist-db.org/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Implements fn:normalize-unicode()
 * Uses icu4j by introspection
 *
 * @author perig
 *
 */
public class FunNormalizeUnicode extends Function {
    protected static final Logger logger = Logger.getLogger(FunNormalizeUnicode.class);

	private String normalizationForm = null;
	private Class clazz = null;	
	private Field  modeField = null;
	private Object modeObject = null;
	private static Integer DUMMY_INTEGER = new Integer(0);
	private Constructor constructor = null;
	private Method method = null;
	
	protected static final String FUNCTION_DESCRIPTION_0_PARAM = 
        "Returns the value of the context item normalized according to the " +
		"nomalization form \"NFC\"\n\n";
	protected static final String FUNCTION_DESCRIPTION_1_PARAM = 
		"Returns the value of $arg normalized according to the " +
		"normalization criteria for a normalization form identified " +
		"by the value of $normalization-form. The effective value of " +
		"the $normalization-form is computed by removing leading and " +
		"trailing blanks, if present, and converting to upper case.\n\n" +
		"If the value of $arg is the empty sequence, returns the zero-length string.\n\n" +
		"See [Character Model for the World Wide Web 1.0: Normalization] " +
		"for a description of the normalization forms.\n\n" +

		"- If the effective value of $normalization-form is \"NFC\", then the value " +
		"returned by the function is the value of $arg in Unicode Normalization Form C (NFC).\n" +
		"- If the effective value of $normalization-form is \"NFD\", then the value " +
		"returned by the function is the value of $arg in Unicode Normalization Form D (NFD).\n" +
		"- If the effective value of $normalization-form is \"NFKC\", then the value " +
		"returned by the function is the value of $arg in Unicode Normalization Form KC (NFKC).\n" +
		"- If the effective value of $normalization-form is \"NFKD\", then the value " +
		"returned by the function is the value of $arg in Unicode Normalization Form KD (NFKD).\n" +
		"- If the effective value of $normalization-form is \"FULLY-NORMALIZED\", then the value " +
		"returned by the function is the value of $arg in the fully normalized form.\n" +
		"- If the effective value of $normalization-form is the zero-length string, " +
		"no normalization is performed and $arg is returned.\n\n" +
		"Conforming implementations must support normalization form \"NFC\" and may " +
		"support normalization forms \"NFD\", \"NFKC\", \"NFKD\", \"FULLY-NORMALIZED\". " +
		"They may also support other normalization forms with implementation-defined semantics. " +
		"If the effective value of the $normalization-form is other than one of the values " +
		"supported by the implementation, then an error is raised [err:FOCH0003].";

	protected static final FunctionParameterSequenceType ARG_PARAM = new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The unicode string to normalize");
	protected static final FunctionParameterSequenceType NF_PARAM = new FunctionParameterSequenceType("normalization-form", Type.STRING, Cardinality.ONE, "The normalization form");
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "the normalized text");

	public final static FunctionSignature signatures [] = {
    	new FunctionSignature(
	      new QName("normalize-unicode", Function.BUILTIN_FUNCTION_NS),
	      FUNCTION_DESCRIPTION_0_PARAM,
	      new SequenceType[] { ARG_PARAM },
	      RETURN_TYPE
	    ),
	    new FunctionSignature (
  	      new QName("normalize-unicode", Function.BUILTIN_FUNCTION_NS),
	      FUNCTION_DESCRIPTION_1_PARAM,
	      new SequenceType[] { ARG_PARAM, NF_PARAM },
	      RETURN_TYPE
		)
	};

    public FunNormalizeUnicode(XQueryContext context, FunctionSignature signature) {
    	super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null)
		    contextSequence = contextItem.toSequence();
		
        Sequence result;

        Sequence s1 = getArgument(0).eval(contextSequence);
        if (s1.isEmpty())
            result = StringValue.EMPTY_STRING;
        else {
            String newNormalizationForm = "NFC";
			if (getArgumentCount() > 1)
				newNormalizationForm = getArgument(1).eval(contextSequence).getStringValue().toUpperCase().trim();
			//TODO : handle the "FULLY-NORMALIZED" string...
			if ("".equals(newNormalizationForm))
				result =  new StringValue(s1.getStringValue());
			else {
				Object returnedObject = null;
				try {
	        		if (clazz == null)
	        			clazz = Class.forName("com.ibm.icu.text.Normalizer");
	        		if (modeField == null || !normalizationForm.equals(newNormalizationForm)) {
	        			try {
	        				modeField = clazz.getField(newNormalizationForm);
	        			} catch (NoSuchFieldException e) {
                            logger.error("err:FOCH0003: unknown normalization form");
	        				throw new XPathException(this, "err:FOCH0003: unknown normalization form");
	        			}
	    	        	//com.ibm.icu.text.Normalizer.Mode
	            		modeObject = modeField.get(null);
	        			normalizationForm = newNormalizationForm;
	        		}
	        		if (constructor == null)
	        			//Second argument shouldn't be a problem : modeField always has the same type
	            		constructor = clazz.getConstructor(
	            				new Class[] { String.class, modeField.getType(), Integer.TYPE}
	    	        		);
		        	Object[] args = new Object[] { s1.getStringValue(), modeObject, DUMMY_INTEGER };
		        	if (method == null)
		        		method = clazz.getMethod( "getText", (Class[])null );
	
		        	//Normalizer n = new Normalizer(s1.getStringValue(), Normalizer.NFC, 0);
		        	Object instance = constructor.newInstance(args);
		        	//result = new StringValue(n.getText());
		        	returnedObject = method.invoke( instance, (Object[])null );
        		} catch (Exception e) {
                    logger.error("Can not find the ICU4J library in the classpath " + e.getMessage());
        			throw new XPathException(this, "Can not find the ICU4J library in the classpath " + e.getMessage());
        		}
        		result = new StringValue((String)returnedObject);
			}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;        
    }

}
