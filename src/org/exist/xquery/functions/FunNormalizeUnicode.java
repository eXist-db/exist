/* eXist Native XML Database
 * Copyright (C) 2000-2006, The eXist team
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
 * $Id: FunEndsWith.java 4163 2006-08-24 14:23:13Z wolfgang_m $
 */

package org.exist.xquery.functions;

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

public class FunNormalizeUnicode extends CollatingFunction {

    public final static FunctionSignature signatures [] = {
    	new FunctionSignature(
	      new QName("normalize-unicode", Function.BUILTIN_FUNCTION_NS),
	      "Returns the value of $a normalized according to the normalization criteria for a " +
	      "normalization form identified by the value of $b. ",
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

    public int returnsType() {
	return Type.BOOLEAN;
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

		Sequence s1 = getArgument(0).eval(contextSequence);
		//String s2 = getArgument(1).eval(contextSequence).getStringValue();
		
        Sequence result;
        if (s1.isEmpty())
            result = StringValue.EMPTY_STRING;
        else {
        	Object returnedObject = null;
        	try {
        		//TODO : don't recreate each time
	        	Class clazz = Class.forName("com.ibm.icu.text.Normalizer");
	        	java.lang.reflect.Field modeField = clazz.getField("NFC");
	        	java.lang.reflect.Constructor constructor = clazz.getConstructor(
	        			new Class[] { String.class, modeField.getType(), Integer.TYPE}
	        		);
	        	//com.ibm.icu.text.Normalizer.Mode
	        	Object mode = modeField.get(null);
	        	Object[] args = new Object[] { s1.getStringValue(), mode, new Integer(0) };
	        	//Normalizer n = new Normalizer(s1.getStringValue(), Normalizer.NFC, 0);
	        	Object instance = constructor.newInstance(args);
	        	java.lang.reflect.Method method = clazz.getMethod("getText", null);
	        	//result = new StringValue(n.getText());
	        	returnedObject = method.invoke(instance, null);
        	} catch (Exception e) {
        		throw new XPathException("Can not find the ICU4J library in the classpath");
        	}
        	result = new StringValue((String)returnedObject);
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;        
    }

}
