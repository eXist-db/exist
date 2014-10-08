/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.security.xacml;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.Module;
import org.exist.xquery.XQueryContext;

import com.sun.xacml.attr.AnyURIAttribute;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.ctx.Attribute;
import com.sun.xacml.ctx.RequestCtx;
import com.sun.xacml.ctx.Subject;

/*
* Source.getKey().toString() needs to be unique:
*	potential collision between FileSource and DBSource ??
*/
/**
* This class provides methods for creating an XACML request.  The main methods
* are those that return a <code>RequestCtx</code>.  Links are provided to the
* relevant constants in <code>XACMLConstants</code> to facilitate policy
* writing.
*
* @see XACMLConstants
*/
public class RequestHelper
{
	/**
	 * Creates an XACML request for permission to execute an XQuery main module.
	 * The subjects section will contain a subject for the user obtained from the
	 * specified context.  The resource section will be created by the
	 * createQueryResource method.  The action-id will be
	 * {@link XACMLConstants#EXECUTE_QUERY_ACTION execute query}.  The environment
	 * section will be created by createEnvironment, using the access context
	 * of the query context.
	 *  
	 * @param context The context for this query
	 * @param source The source of this query
	 * @return A <code>RequestCtx</code> that may be evaluated by the PDP to
	 * determine whether the specified user may execute the query represented by
	 * <code>source</code>.
	 */
	public RequestCtx createQueryRequest(XQueryContext context, XACMLSource source)
	{
		final Set<Subject> subjects = createQuerySubjects(context.getUser(), null);
		final Set<Attribute> resourceAttributes = createQueryResource(source);
		final Set<Attribute> actionAttributes = createBasicAction(XACMLConstants.EXECUTE_QUERY_ACTION);
		final Set<Attribute> environmentAttributes = createEnvironment(context.getAccessContext());

		return new RequestCtx(subjects, resourceAttributes, actionAttributes, environmentAttributes);
	}
	
	/**
	* Creates a <code>RequestCtx</code> for a request concerning reflective
	* access to Java code from an XQuery.  This handles occurs when a method
	* is being invoked on the class in question. This method creates a
	* request with the following content:
	* <ul>
	*
	*  <li>Subjects for the contextModule and user are created with the 
	* createQuerySubjects method.</li>
	*
	*  <li>Resource attributes are created with the
	* <code>createReflectionResource</code> method.</li>
	*
	*  <li>Action attributes are created with the
	* <code>createBasicAction</code> method.  The action-id is
	* {@link XACMLConstants#INVOKE_METHOD_ACTION invoke method}.</li>
	*
	*  <li>The {@link XACMLConstants#ACCESS_CONTEXT_ATTRIBUTE} access context 
	* attribute is generated for the environment section.</li>
	*
	* </ul>
	*
	* @param context The <code>XQueryContext</code> for the module making the
	* request.
	* @param contextModule The query containing the reflection.
	* @param className The name of the class that is being accessed or loaded.
	* @param methodName The name of the method that is being invoked
	* @return A <code>RequestCtx</code> that represents the access in question.
	*/
	public RequestCtx createReflectionRequest(XQueryContext context, Module contextModule, String className, String methodName)
	{
		final Account user = context.getUser();
		final Set<Subject> subjects = createQuerySubjects(user, contextModule);
		final Set<Attribute> resourceAttributes = createReflectionResource(className, methodName);
		final Set<Attribute> actionAttributes = createBasicAction(XACMLConstants.INVOKE_METHOD_ACTION);
		final Set<Attribute> environmentAttributes = createEnvironment(context.getAccessContext());

		return new RequestCtx(subjects, resourceAttributes, actionAttributes, environmentAttributes);
	}
	
	/**
	* Creates a <code>RequestCtx</code> for a request concerning access
	* to a function in an XQuery library module.  If the function is
	* from a main module, this method returns null to indicate that.
	* The client should interpret this to mean that the request is
	* granted because access to a main module implies access to its
	* functions.
	*
	* <p>
	* This method creates a request with the following content:
	* <ul>
	*
	*  <li>Subjects for the contextModule and user (obtained from the
	* XQueryContext) are created with the createQuerySubjects method.</li>
	*
	*  <li>The specified functionModule parameter is used to generate the
	* {@link XACMLConstants#SOURCE_KEY_ATTRIBUTE source-key},
	* {@link XACMLConstants#SOURCE_TYPE_ATTRIBUTE source-type}, and
	* {@link XACMLConstants#MODULE_CATEGORY_ATTRIBUTE module category}
	* attributes. The functionName parameter is the value of the
	* {@link XACMLConstants#RESOURCE_ID_ATTRIBUTE subject-id} attribute
	* (the local part) and of the 
	* {@link XACMLConstants#MODULE_NS_ATTRIBUTE module namespace}
	*  attribute (the namespace URI part).  The 
	* {@link XACMLConstants#RESOURCE_CATEGORY_ATTRIBUTE resource-category}
	* attribute is {@link XACMLConstants#FUNCTION_RESOURCE function}.
	* 
	*  <li>Action attributes are created with the
	* <code>createBasicAction</code> method.  The action is
	* {@link XACMLConstants#CALL_FUNCTION_ACTION call function}. 
	*
	*  <li>The {@link XACMLConstants#ACCESS_CONTEXT_ATTRIBUTE} access context 
	* attribute is generated for the environment section.</li>
	*
	* </ul>
	*
	* @param context The query context.
	* @param contextModule The query making the access.
	* @param functionName The <code>QName</code> of the function being called.
	* @return A <code>RequestCtx</code> that represents the access in question 
	*	or <code>null</code> if the function belongs to a main module and
	*	not a library module.
	*/
	public RequestCtx createFunctionRequest(XQueryContext context, Module contextModule, QName functionName)
	{
		final String namespaceURI = functionName.getNamespaceURI();
		final Module functionModule = context.getModule(namespaceURI);
		if(functionModule == null)
		{
			//main module, not a library module, so access to function is always allowed
			return null;
		}
		
		final Account user = context.getUser();
		final Set<Subject> subjects = createQuerySubjects(user, contextModule);

		final Set<Attribute> resourceAttributes = new HashSet<Attribute>(8);
		addStringAttribute(resourceAttributes, XACMLConstants.MODULE_CATEGORY_ATTRIBUTE, getModuleCategory(functionModule));
		final XACMLSource moduleSrc = generateModuleSource(functionModule);
		addSourceAttributes(resourceAttributes, moduleSrc);
		addValidURIAttribute(resourceAttributes, XACMLConstants.MODULE_NS_ATTRIBUTE, namespaceURI);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, XACMLConstants.FUNCTION_RESOURCE);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, functionName.getLocalPart());

		final Set<Attribute> actionAttributes = createBasicAction(XACMLConstants.CALL_FUNCTION_ACTION);
		final Set<Attribute> environmentAttributes = createEnvironment(context.getAccessContext());

		return new RequestCtx(subjects, resourceAttributes, actionAttributes, environmentAttributes);
	}
	
	/**
	* Creates a <code>Subject</code> for a <code>User</code>.
	* The user's name is the value of the 
	* {@link XACMLConstants#SUBJECT_ID_ATTRIBUTE subject-id} attribute.  The
	* subject-category is {@link XACMLConstants#ACCESS_SUBJECT access-subject}.
	* The {@link XACMLConstants#GROUP_ATTRIBUTE group} attribute is a bag
	* containing the name of each group of which the user is a member.
	*
	* @param user The user making the request
	* @return A <code>Subject</code> for use in a <code>RequestCtx</code>
	*/
	public Subject createUserSubject(Account user)
	{
		final AttributeValue value = new StringAttribute(user.getName());
		final Attribute attr = new Attribute(XACMLConstants.SUBJECT_ID_ATTRIBUTE, null, null, value);
		return new Subject(XACMLConstants.ACCESS_SUBJECT, Collections.singleton(attr));
	}
	/**
	* Creates the basic attributes needed to describe a simple action
	* in a request.  The <code>action</code> parameter is the value of
	* the {@link XACMLConstants#ACTION_ID_ATTRIBUTE action-id} attribute and the 
	* {@link XACMLConstants#ACTION_NS_ATTRIBUTE namespace} attribute for the
	* action-id is eXist's XACML
	* {@link XACMLConstants#ACTION_NS action namespace}.
	*
	* @param action The {@link XACMLConstants#ACTION_ID_ATTRIBUTE action-id}
	*	of the action.
	* @return A <code>Set</code> that contains attributes describing the
	*	action for use in a <code>RequestCtx</code>
	*/
	public Set<Attribute> createBasicAction(String action)
	{
		if(action == null)
			{return null;}

		final Set<Attribute> attributes = new HashSet<Attribute>(4);
		addStringAttribute(attributes, XACMLConstants.ACTION_ID_ATTRIBUTE, action);
		addValidURIAttribute(attributes, XACMLConstants.ACTION_NS_ATTRIBUTE, XACMLConstants.ACTION_NS);

		return attributes;
	}

	/**
	* Creates a <code>Subject</code> for a <code>Module</code>.
	* If the module is external, its <code>Source</code> is the value of the 
	* {@link XACMLConstants#SUBJECT_ID_ATTRIBUTE subject-id} attribute, otherwise,
	* the name of the implementing class is used.  The subject-category is 
	* {@link XACMLConstants#CODEBASE_SUBJECT codebase}.  The value of the 
	* {@link XACMLConstants#SUBJECT_NS_ATTRIBUTE module namespace} attribute
	* is the namespace URI of the module.  The
	* {@link XACMLConstants#MODULE_CATEGORY_ATTRIBUTE module category}
	* attribute is the type of module, either
	* {@link XACMLConstants#INTERNAL_LIBRARY_MODULE internal} or
	* {@link XACMLConstants#EXTERNAL_LIBRARY_MODULE external}.
	*
	* @param module A query module involved in making the request
	* @return A <code>Subject</code> for use in a <code>RequestCtx</code>
	*/
	public Subject createModuleSubject(Module module)
	{
		if(module == null)
			{return null;}

		final Set<Attribute> attributes = new HashSet<Attribute>(8);
		addValidURIAttribute(attributes, XACMLConstants.SUBJECT_NS_ATTRIBUTE, module.getNamespaceURI());
		addStringAttribute(attributes, XACMLConstants.MODULE_CATEGORY_ATTRIBUTE, getModuleCategory(module));
		final XACMLSource moduleSrc = generateModuleSource(module);
		addSourceAttributes(attributes, moduleSrc);
		addStringAttribute(attributes, XACMLConstants.SUBJECT_ID_ATTRIBUTE, moduleSrc.createId());

		return new Subject(XACMLConstants.CODEBASE_SUBJECT, attributes);
	}


	/**
	* Creates a <code>Set</code> of <code>Attribute</code>s for a resource
	* representing Java reflection in an XQuery.
	* The {@link XACMLConstants#RESOURCE_CATEGORY_ATTRIBUTE resource-category}
	* attribute is {@link XACMLConstants#METHOD_RESOURCE method}.
	* The {@link XACMLConstants#SOURCE_TYPE_ATTRIBUTE source-type} attribute is
	* {@link XACMLConstants#CLASS_SOURCE_TYPE class} and the
	* {@link XACMLConstants#SOURCE_KEY_ATTRIBUTE source-key} attribute is the
	* name of the class.  The
	* {@link XACMLConstants#RESOURCE_ID_ATTRIBUTE resource-id} attribute is the 
	* method name.
	*
	* @param className The name of the Java class
	* @param methodName The name of the method being invoked
	* @return A <code>Set</code> containing the <code>Attribute</code>s
	* describing access to Java code by reflection.
	*/
	public Set<Attribute> createReflectionResource(String className, String methodName)
	{
		if(className == null)
			{throw new NullPointerException("Class name cannot be null");}
		if(methodName == null)
			{throw new NullPointerException("Method name cannot be null");}
		
		final Set<Attribute> resourceAttributes = new HashSet<Attribute>(8);

		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, XACMLConstants.METHOD_RESOURCE);
		final XACMLSource source = XACMLSource.getInstance(className);
		addSourceAttributes(resourceAttributes, source);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, methodName);

		return resourceAttributes;
	}
	
	/**
	 * Creates the Resource section of a request for a main module.
	 * 
	 * @param source The source of the query.
	 * @return A <code>Set</code> containing attributes for the specified
	 * query.
	 */
	public Set<Attribute> createQueryResource(XACMLSource source)
	{
		if(source == null)
			{throw new NullPointerException("Query source cannot be null");}
		
		final Set<Attribute> resourceAttributes = new HashSet<Attribute>(4);
		addSourceAttributes(resourceAttributes, source);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, source.createId());
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, XACMLConstants.MAIN_MODULE_RESOURCE);
		return resourceAttributes;
	}
	
	/**
	* Creates <code>Subject</code>s for the specified user and module.  This is
	* equivalent to putting the <code>Subject</code>s created by the
	* <code>createUserSubject(User user)</code> and
	* <code>createModuleSubject(Module contextModule)</code> methods.  The
	* context module may be null if there is no context module.
	*
	* @param user The user making the access
	* @param contextModule The module involved in the access, if any.  It may
	* be null to indicate the is not an intermediary XQuery module.
	* @return A <code>Set</code> containing a <code>Subject</code> for each
	* the context module if there is one and the user.
	*/
	public Set<Subject> createQuerySubjects(Account user, Module contextModule)
	{
		if(user == null)
			{throw new NullPointerException("User cannot be null");}
		final Set<Subject> subjects = new HashSet<Subject>(4);

		final Subject userSubject = createUserSubject(user);
		subjects.add(userSubject);

		if(contextModule != null)
		{
			final Subject moduleSubject = createModuleSubject(contextModule);
			subjects.add(moduleSubject);
		}
		return subjects;
	}
	
	/**
	 * Creates the environment section of a request for the given
	 * <code>AccessContext</code>.
	 * 
	 * @param accessCtx The context
	 * @return A <code>Set</code> containing one attribute, the
	 * {@link XACMLConstants#ACCESS_CONTEXT_ATTRIBUTE access context}
	 * attribute with the value of the specified access context.
	 */
	public Set<Attribute> createEnvironment(AccessContext accessCtx)
	{
		if(accessCtx == null)
			{throw new NullAccessContextException();}
		final Set<Attribute> environment = new HashSet<Attribute>(4);
		addStringAttribute(environment, XACMLConstants.ACCESS_CONTEXT_ATTRIBUTE, accessCtx.toString());
		return environment;
	}
		
	/**
	* Generates an <code>XACMLSource</code> for a <code>Module</code>
	* based on its implementing class name (if it is an 
	* <code>InternalModule</code>) or its <code>Source</code>
	* (if it is an <code>ExternalModule</code>).
	*
	* @param module the module for which the source should be generated
	* @return an <code>XACMLSource</code> that uniquely defines the source
	* of the given module
	*/
	public static XACMLSource generateModuleSource(Module module)
	{
		if(module == null)
			{throw new NullPointerException("Module cannot be null");}
		if(module.isInternalModule())
			{return XACMLSource.getInstance(module.getClass());}
		return XACMLSource.getInstance(((ExternalModule)module).getSource());
	}

	/**
	 * Returns the module type for the given XQuery library module.  This
	 * is either
	 * {@link XACMLConstants#INTERNAL_LIBRARY_MODULE internal} or
	 * {@link XACMLConstants#EXTERNAL_LIBRARY_MODULE external}
	 * 
	 * @param module The XQuery library module.  If it is null, this method
	 * returns null.
	 * @return null if module is null, the module's category (internal or external)
	 * otherwise
	 */
	public static String getModuleCategory(Module module)
	{
		if(module == null)
			{return null;}
		return module.isInternalModule() ? XACMLConstants.INTERNAL_LIBRARY_MODULE : XACMLConstants.EXTERNAL_LIBRARY_MODULE;
	}
	
	/**
	 * Adds new attributes to the specified <code>Set</code> of attributes
	 * that represent the specified source.  The added attributes are the
	 * {@link XACMLConstants#SOURCE_KEY_ATTRIBUTE source's key} and the
	 * {@link XACMLConstants#SOURCE_TYPE_ATTRIBUTE source's type}.
	 *   
	 * @param attributes The <code>Set</code> to which attributes will be
	 * added.  If null, this method does nothing.
	 * @param source The source for which attributes will be added.  It
	 * cannot be null.
	 */
	public static void addSourceAttributes(Set<Attribute> attributes, XACMLSource source)
	{
		if(source == null)
			{throw new NullPointerException("Source cannot be null");}
		addStringAttribute(attributes, XACMLConstants.SOURCE_KEY_ATTRIBUTE, source.getKey());
		addStringAttribute(attributes, XACMLConstants.SOURCE_TYPE_ATTRIBUTE, source.getType());
	}

	/**
	 * Adds a new attribute of type string to the specified
	 * <code>Set</code> of attributes.  The new attribute's value is
	 * constructed from the attrValue parameter and is given the id
	 * of the attrID parameter. 
	 * 
	 * @param attributes The <code>Set</code> to which the new attribute
	 * should be added.  If it is null, this method does nothing.
	 * @param attrID The ID of the new attribute, cannot be null
	 * @param attrValue The value of the new attribute.  It cannot be null.
	 */
	public static void addStringAttribute(Set<Attribute> attributes, URI attrID, String attrValue)
	{
		if(attributes == null)
			{return;}
		if(attrID == null)
			{throw new NullPointerException("Attribute ID cannot be null");}
		if(attrValue == null)
			{throw new NullPointerException("Attribute value cannot be null");}
		final AttributeValue value = new StringAttribute(attrValue);
		final Attribute attr = new Attribute(attrID, null, null, value);
		attributes.add(attr);
	}
	
	/**
	 * Adds a new attribute of type anyURI to the specified
	 * <code>Set</code> of attributes.  The new attribute's value is
	 * constructed from the uriString parameter and is given the id
	 * of the attrID parameter. 
	 * 
	 * @param attributes The <code>Set</code> to which the new attribute
	 * should be added.  If it is null, this method does nothing.
	 * @param attrID The ID of the new attribute, cannot be null
	 * @param uriString The value of the new attribute.  It must parse into a
	 * valid URI and cannot be null.
	 * @throws URISyntaxException if the specified attribute value is not a
	 * valid URI.
	 */
	public static void addURIAttribute(Set<Attribute> attributes, URI attrID, String uriString) throws URISyntaxException
	{
		if(attributes == null)
			{return;}
		if(attrID == null)
			{throw new NullPointerException("Attribute ID cannot be null");}
		if(uriString == null)
			{throw new NullPointerException("Attribute value cannot be null");}
		final URI uri = new URI(uriString);
		final AttributeValue value = new AnyURIAttribute(uri);
		final Attribute attr = new Attribute(attrID, null, null, value);
		attributes.add(attr);
	}
	
	//wrapper for when the URI is known to be valid, such as when obtained from a source
	//that validates the URI or from a constant
	private static void addValidURIAttribute(Set<Attribute> attributes, URI attrID, String uriString)
	{
		try
		{
			addURIAttribute(attributes, attrID, uriString);
		}
		catch(final URISyntaxException e)
		{
			throw new RuntimeException("URI should never be invalid", e);
		}
	}

	RequestHelper() {}
}