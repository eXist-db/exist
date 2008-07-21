/* eXist Native XML Database
 * Copyright (C) 2000-2006, The eXist Project
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FunNormalizeUnicode extends Function {
	
	private String normalizationForm = null;
	private Class clazz = null;	
	private Field  modeField = null;
	private Object modeObject = null;
	private static Integer DUMMY_INTEGER = new Integer(0);
	private Constructor constructor = null;
	private Method method = null;

    public final static FunctionSignature signatures [] = {
    	new FunctionSignature(
	      new QName("normalize-unicode", Function.BUILTIN_FUNCTION_NS),
	      "Returns the value of $a normalized according to the normalization form NFC. ",
	      new SequenceType[] {
	    	  new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
	      },
	      new SequenceType(Type.BOOLEAN, Cardinality.ONE)
	    ),
	    new FunctionSignature (
	  	      new QName("normalize-unicode", Function.BUILTIN_FUNCTION_NS),
		      "Returns the value of $a normalized according to the normalization criteria for a " +
		      "normalization form identified by the value of $b. ",
		      new SequenceType[] {
		    	  new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
		    	  new SequenceType(Type.STRING, Cardinality.ONE)
			  },
		      new SequenceType(Type.BOOLEAN, Cardinality.ONE)),
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
	        				throw new XPathException(getASTNode(), "err:FOCH0003: unknown normalization form");
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
		        		method = clazz.getMethod("getText", null);
	
		        	//Normalizer n = new Normalizer(s1.getStringValue(), Normalizer.NFC, 0);
		        	Object instance = constructor.newInstance(args);
		        	//result = new StringValue(n.getText());
		        	returnedObject = method.invoke(instance, null);
        		} catch (Exception e) {
        			throw new XPathException(getASTNode(), "Can not find the ICU4J library in the classpath " + e.getMessage());
        		}
        		result = new StringValue((String)returnedObject);
			}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;        
    }

}
