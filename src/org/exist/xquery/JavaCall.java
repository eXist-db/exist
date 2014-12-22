/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.ExistPDP;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import com.sun.xacml.ctx.RequestCtx;

/**
 * A special function call to a Java method or constructor.
 * 
 * This class handles all function calls who's namespace URI
 * starts with "java:".
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class JavaCall extends Function {

	private final QName qname;
	private String name;
	private Class<?> myClass = null;
	private List<AccessibleObject> candidateMethods = new ArrayList<AccessibleObject>(5);

	/**
	 * @param context
	 * @param qname the of the function
	 */
	public JavaCall(XQueryContext context, QName qname) throws XPathException {
		super(context, null);
		this.qname = qname;
		String namespaceURI = context.getURIForPrefix(qname.getPrefix());
		if (!namespaceURI.startsWith("java:"))
			{throw new XPathException(this,
				"Internal error: prefix "
					+ qname.getPrefix()
					+ " does not "
					+ "resolve to a Java class");}
		namespaceURI = namespaceURI.substring("java:".length());

		try {
			LOG.debug("Trying to find class " + namespaceURI);
			
			myClass = Class.forName(namespaceURI);
		} catch (final ClassNotFoundException e) {
			throw new XPathException(this, "Class: " + namespaceURI + " not found", e);
		}

		name = qname.getLocalName();
		// convert hyphens into camelCase
		if (name.indexOf('-') > 0) {
			final StringBuilder buf = new StringBuilder();
			boolean afterHyphen = false;
			char ch;
			for (int i = 0; i < name.length(); i++) {
				ch = name.charAt(i);
				if (ch == '-')
					{afterHyphen = true;}
				else {
					if (afterHyphen) {
						buf.append(Character.toUpperCase(ch));
						afterHyphen = false;
					} else
						{buf.append(ch);}
				}
			}
			name = buf.toString();
			LOG.debug("converted method name to " + name);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#getName()
	 */
	public QName getName() {
		return qname;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.ZERO_OR_MORE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(List<Expression> arguments) throws XPathException {
		final int argCount = arguments.size();
		for (int i = 0; i < argCount; i++)
		    steps.add(arguments.get(i));

		// search for candidate methods matching the given number of arguments
		if ("new".equals(name)) {
			final Constructor<?>[] constructors = myClass.getConstructors();
			for (int i = 0; i < constructors.length; i++) {
				if (Modifier.isPublic(constructors[i].getModifiers())) {
					final Class<?> paramTypes[] = constructors[i].getParameterTypes();
					if (paramTypes.length == argCount) {
						LOG.debug("Found constructor " + constructors[i].toString());
						candidateMethods.add(constructors[i]);
					}
				}
			}
			if (candidateMethods.size() == 0) {
				final String message = "no constructor found with " + argCount + " arguments"; 
				throw new XPathException(this,
					message,
					new NoSuchMethodException(message));
			}
		} else {
			final Method[] methods = myClass.getMethods();
			for (int i = 0; i < methods.length; i++) {
				if (Modifier.isPublic(methods[i].getModifiers())
					&& methods[i].getName().equals(name)) {
					final Class<?> paramTypes[] = methods[i].getParameterTypes();
					if (Modifier.isStatic(methods[i].getModifiers())) {
						if (paramTypes.length == argCount) {
							LOG.debug("Found static method " + methods[i].toString());
							candidateMethods.add(methods[i]);
						}
					} else {
						if (paramTypes.length == argCount - 1) {
							LOG.debug("Found method " + methods[i].toString());
							candidateMethods.add(methods[i]);
						}
					}
				}
			}
			if (candidateMethods.size() == 0) {
				final String message = "no method matches " + name + " with " + argCount + " arguments"; 
				throw new XPathException(this,
					message,
					new NoSuchMethodException(message));
			}
		}
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }

		//check access to the method
		try {
			final ExistPDP pdp = context.getPDP();
			if(pdp != null) {
				final RequestCtx request = pdp.getRequestHelper().createReflectionRequest(context, null, myClass.getName(), name);
				pdp.evaluate(request);
			}
		} catch (final PermissionDeniedException pde) {
			throw new XPathException(this, "Access to method '" + name + "' in class '" + myClass.getName() + "' denied.", pde);
		}
        
		// get the actual arguments
		final Sequence args[] = getArguments(contextSequence, contextItem);

		AccessibleObject bestMethod = candidateMethods.get(0);
		int conversionPrefs[] = getConversionPreferences(bestMethod, args);

		for (AccessibleObject nextMethod : candidateMethods) {
			int prefs[] = getConversionPreferences(nextMethod, args);
			for (int j = 0; j < prefs.length; j++) {
				if (prefs[j] < conversionPrefs[j]) {
					bestMethod = nextMethod;
					conversionPrefs = prefs;
					break;
				}
			}
		}
//		LOG.debug("calling method " + bestMethod.toString());
		Class<?> paramTypes[] = null;
		boolean isStatic = true;
		if (bestMethod instanceof Constructor<?>)
			{paramTypes = ((Constructor<?>) bestMethod).getParameterTypes();}
		else {
			paramTypes = ((Method) bestMethod).getParameterTypes();
			isStatic = Modifier.isStatic(((Method) bestMethod).getModifiers());
		}

		final Object[] params = new Object[isStatic ? args.length : args.length - 1];
		if (isStatic) {
			for (int i = 0; i < args.length; i++) {
				params[i] = args[i].toJavaObject(paramTypes[i]);
			}
		} else {
			for (int i = 1; i < args.length; i++) {
				params[i - 1] = args[i].toJavaObject(paramTypes[i - 1]);
			}
		}
        
        Sequence result;
		if (bestMethod instanceof Constructor<?>) {
			try {
				final Object object = ((Constructor<?>) bestMethod).newInstance(params);
                result = new JavaObjectValue(object);
			} catch (final IllegalArgumentException e) {
				throw new XPathException(this,
					"illegal argument to constructor "
						+ bestMethod.toString()
						+ ": "
						+ e.getMessage(),
					e);
			} catch (final Exception e) {
				if (e instanceof XPathException)
					{throw (XPathException) e;}
				else
					{throw new XPathException(this,
						"exception while calling constructor "
							+ bestMethod.toString()
							+ ": "
							+ e.getMessage(),
						e);}
			}
		} else {
			try {
				Object invocationResult;
				if (isStatic)
                    {invocationResult = ((Method) bestMethod).invoke(null, params);}
				else {
                    invocationResult =
						((Method) bestMethod).invoke(
							args[0].toJavaObject(myClass),
							params);
				}
                result = XPathUtil.javaObjectToXPath(invocationResult, getContext());
			} catch (final IllegalArgumentException e) {
				throw new XPathException(this,
					"illegal argument to method "
						+ bestMethod.toString()
						+ ": "
						+ e.getMessage(),
					e);
			} catch (final Exception e) {
				if (e instanceof XPathException)
					{throw (XPathException) e;}
				else
					{throw new XPathException(this,
						"exception while calling method "
							+ bestMethod.toString()
							+ ": "
							+ e.getMessage(),
						e);}
			}
		}

         if (context.getProfiler().isEnabled())           
                {context.getProfiler().end(this, "", result);} 
        
        if (result==null) {result = Sequence.EMPTY_SEQUENCE;} 
         
        return result;
        
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}
	
	private int[] getConversionPreferences(AccessibleObject method, Sequence[] args) {
		final int prefs[] = new int[args.length];
		Class<?> paramTypes[] = null;
		boolean isStatic = true;
		if (method instanceof Constructor<?>)
			{paramTypes = ((Constructor<?>) method).getParameterTypes();}
		else {
			paramTypes = ((Method) method).getParameterTypes();
			isStatic = Modifier.isStatic(((Method) method).getModifiers());
			if (!isStatic) {
				Class<?> nonStaticTypes[] = new Class[paramTypes.length + 1];
				nonStaticTypes[0] = myClass;
				System.arraycopy(paramTypes, 0, nonStaticTypes, 1, paramTypes.length);
				paramTypes = nonStaticTypes;
			}
		}
		for (int i = 0; i < args.length; i++) {
			prefs[i] = args[i].conversionPreference(paramTypes[i]);
		}
		return prefs;
	}
}
