package org.exist.security.xacml;

import com.sun.xacml.attr.AnyURIAttribute;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.attr.BagAttribute;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.ctx.Attribute;
import com.sun.xacml.ctx.RequestCtx;
import com.sun.xacml.ctx.Subject;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.exist.security.User;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.Module;

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
	* Creates a <code>RequestCtx</code> for a request concerning reflective
	* access to Java code from an XQuery.  This handles both the case where
	* the class itself is obtained with <code>Class.forName</code> and
	* when a method is being invoked on the class in question.  In the case
	* where the class is being loaded, <code>methodName</code> should be null.
	* <p>
	* This method creates a request with the following content:
	* <ul>
	*
	*  <li>Subjects for the contextModule and user are created with the 
	* createQuerySubjects method.</li>
	*
	*  <li>Resource attributes are created with the
	* <code>createReflectionResource</code> method.</li>
	*
	*  <li>Action attributes are created with the
	* <code>createBasicAction</code> method.  The action is either
	* {@link XACMLConstants#LOAD_CLASS_ACTION load class} or
	* {@link XACMLConstants#INVOKE_METHOD_ACTION invoke method}.</li>
	*
	*  <li>No environment attributes are explicitly generated; these
	* will be handled by the <code>CurrentEnvModule</code> if a policy
	* requests any of the required environment attributes.</li>
	*
	* </ul>
	*
	* @param user The current user running the query.
	* @param contextModule The query containing the reflection.
	* @param className The name of the class that is being accessed or loaded.
	* @param methodName The name of the method that is being invoked or null
	*	if no method is being invoked.
	* @return A <code>RequestCtx</code> that represents the access in question.
	*/
	public static RequestCtx createReflectionRequest(User user, Module contextModule, String className, String methodName)
	{
		Set subjects = createQuerySubjects(user, contextModule);

		Set resourceAttributes = createReflectionResource(className, methodName);

		String actionID = (methodName == null) ? XACMLConstants.LOAD_CLASS_ACTION : XACMLConstants.INVOKE_METHOD_ACTION;
		Set actionAttributes = createBasicAction(actionID);

		return new RequestCtx(subjects, resourceAttributes, actionAttributes, Collections.EMPTY_SET);
	}
	
	/**
	* Creates a <code>RequestCtx</code> for a request concerning access
	* to an XQuery module.
	* <p>
	* This method creates a request with the following content:
	* <ul>
	*
	*  <li>Subjects for the contextModule and user are created with the 
	* createQuerySubjects method.</li>
	*
	*  <li>The specified <code>moduleID</code> parameter is the value of the
	* {@link XACMLConstants#RESOURCE_ID_ATTRIBUTE subject-id} attribute.  The 
	* {@link XACMLConstants#RESOURCE_CATEGORY_ATTRIBUTE resource-category}
	* attribute is {@link XACMLConstants#QUERY_RESOURCE query}.  The value of
	* the {@link XACMLConstants#MODULE_NS_ATTRIBUTE module namespace} attribute
	* is the namespace URI of the module specified by the parameter
	* <code>moduleNamespaceURI</code>.  The
	* {@link XACMLConstants#MODULE_CATEGORY_ATTRIBUTE module category}
	* attribute is the type of module, either
	* {@link XACMLConstants#INTERNAL_MODULE_ATTR_VALUE internal} or
	* {@link XACMLConstants#EXTERNAL_MODULE_ATTR_VALUE external}.</li>
	*
	*  <li>Action attributes are created with the
	* <code>createBasicAction</code> method using the specified parameter
	* <code>action</code></li>
	*
	*  <li>No environment attributes are explicitly generated; these
	* will be handled by the <code>CurrentEnvModule</code> if a policy
	* requests any of the required environment attributes.</li>
	*
	* </ul>
	*
	* @param user The current user.
	* @param contextModule The query making the access.
	* @param moduleNamespaceURI The namespace URI for the module being accessed.
	* @param moduleID a unique identifier for the module being accessed
	* @param moduleCategory The type of module being accessed.
	* @param action The action-id of the action being taken.
	* @return A <code>RequestCtx</code> that represents the access in question.
	*/
	public static RequestCtx createModuleRequest(User user, Module contextModule, String moduleNamespaceURI, String moduleID, String moduleCategory, String action)
	{
		Set subjects = createQuerySubjects(user, contextModule);

		Set resourceAttributes = new HashSet(8);
		addModuleAttributes(resourceAttributes, moduleNamespaceURI, moduleCategory);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, XACMLConstants.QUERY_RESOURCE);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, moduleID);

		Set actionAttributes = createBasicAction(action);

		return new RequestCtx(subjects, resourceAttributes, actionAttributes, Collections.EMPTY_SET);
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
	public static Subject createUserSubject(User user)
	{
		Set attributes = new HashSet(4);

		addStringAttribute(attributes, XACMLConstants.SUBJECT_ID_ATTRIBUTE, user.getName());

		Set groups = new HashSet(8);
		String[] groupArray = user.getGroups();
		for(int i = 0; i < groupArray.length; ++i)
			groups.add(new StringAttribute(groupArray[i]));

		AttributeValue value = new BagAttribute(XACMLConstants.STRING_TYPE, groups);
		Attribute attr = new Attribute(XACMLConstants.GROUP_ATTRIBUTE, null, null, value);
		attributes.add(attr);

		return new Subject(XACMLConstants.ACCESS_SUBJECT, attributes);
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
	public static Set createBasicAction(String action)
	{
		if(action == null)
			return null;

		Set attributes = new HashSet(4);
		addStringAttribute(attributes, XACMLConstants.ACTION_ID_ATTRIBUTE, action);
		addURIAttribute(attributes, XACMLConstants.ACTION_NS_ATTRIBUTE,XACMLConstants.ACTION_NS);

		return attributes;
	}

	/**
	* Creates a <code>Subject</code> for a <code>Module</code>.
	* If the module is external, its <code>Source</code> is the value of the 
	* {@link XACMLConstants#SUBJECT_ID_ATTRIBUTE subject-id} attribute, otherwise,
	* the name of the implementing class is used.  The subject-category is 
	* {@link XACMLConstants#CODEBASE_SUBJECT codebase}.  The value of the 
	* {@link XACMLConstants#MODULE_NS_ATTRIBUTE module namespace} attribute
	* is the namespace URI of the module.  The
	* {@link XACMLConstants#MODULE_CATEGORY_ATTRIBUTE module category}
	* attribute is the type of module, either
	* {@link XACMLConstants#INTERNAL_MODULE_ATTR_VALUE internal} or
	* {@link XACMLConstants#EXTERNAL_MODULE_ATTR_VALUE external}.
	*
	* @param module A query module involved in making the request
	* @return A <code>Subject</code> for use in a <code>RequestCtx</code>
	*/
	public static Subject createModuleSubject(Module module)
	{
		if(module == null)
			return null;

		Set attributes = new HashSet(8);
		addModuleAttributes(attributes, module);
		addStringAttribute(attributes, XACMLConstants.SUBJECT_ID_ATTRIBUTE, generateModuleID(module));

		return new Subject(XACMLConstants.CODEBASE_SUBJECT, attributes);
	}


	/**
	* Creates a <code>Set</code> of <code>Attribute</code>s for a resource
	* representing Java reflection in an XQuery.  If a method is being
	* invoked, both the fully qualified class name and the method name
	* should be supplied.  If a class is being loaded, the method name
	* should be null.
	* <p>
	* The {@link XACMLConstants#RESOURCE_CATEGORY_ATTRIBUTE resource-category}
	* attribute is either {@link XACMLConstants#CLASS_RESOURCE class} 
	* (for class loading) or
	* {@link XACMLConstants#METHOD_RESOURCE method} (for method invocation).
	* The {@link XACMLConstants#CLASS_ATTRIBUTE class} attribute is
	* the class name.  The
	* {@link XACMLConstants#RESOURCE_ID_ATTRIBUTE resource-id} attribute 
	* for class loading is the class name and is the method name for
	* method invocation.
	*
	* @param className The name of the Java class
	* @param methodName The name of the method being invoked, or null
	* if the class is being loaded.
	* @return A <code>Set</code> containing the <code>Attribute</code>s
	* describing access to Java code by reflection.
	*/
	public static Set createReflectionResource(String className, String methodName)
	{
		if(className == null)
			throw new NullPointerException("Class name cannot be null");
		
		Set resourceAttributes = new HashSet(4);

		String resourceCategory;
		String ID;
		if(methodName == null)
		{
			resourceCategory = XACMLConstants.CLASS_RESOURCE;
			ID = className;
		}
		else
		{
			resourceCategory = XACMLConstants.METHOD_RESOURCE;
			ID = methodName;
		}
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, resourceCategory);
		addStringAttribute(resourceAttributes, XACMLConstants.CLASS_ATTRIBUTE, className);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, ID);

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
	public static Set createQuerySubjects(User user, Module contextModule)
	{
		if(user == null)
			throw new NullPointerException("User cannot be null");
		Set subjects = new HashSet(4);

		Subject userSubject = createUserSubject(user);
		subjects.add(userSubject);

		if(contextModule != null)
		{
			Subject moduleSubject = createModuleSubject(contextModule);
			subjects.add(moduleSubject);
		}
		return subjects;
	}
	
	/**
	* Generates a unique ID for a <code>Module</code> based
	* on its implementing class name (if it is an 
	* <code>InternalModule</code>) or its <code>Source</code>
	* (if it is an <code>ExternalModule</code>).
	*
	* @param module the module for which the ID should be generated
	* @return a module ID that is unique to the module
	*/
	public static String generateModuleID(Module module)
	{
		if(module.isInternalModule())
			return module.getClass().getName();
		else
			return ((ExternalModule)module).getSource().getKey().toString();
	}

	//convenience methods for adding attributes representing a module to a Set
	private static void addModuleAttributes(Set attributes, String namespaceURI, String moduleCategory)
	{
		addURIAttribute(attributes, XACMLConstants.MODULE_NS_ATTRIBUTE, namespaceURI);
		addStringAttribute(attributes, XACMLConstants.MODULE_CATEGORY_ATTRIBUTE, moduleCategory);
	}
	private static void addModuleAttributes(Set attributes, Module module)
	{
		String moduleCategory = module.isInternalModule() ? XACMLConstants.INTERNAL_MODULE_ATTR_VALUE : XACMLConstants.EXTERNAL_MODULE_ATTR_VALUE;
		addModuleAttributes(attributes, module.getNamespaceURI(), moduleCategory);
	}

	//convenience methods for adding an AttributeValue to a Set of attributes
	private static void addStringAttribute(Set attributes, URI attrID, String attrValue)
	{
		AttributeValue value = attrValue == null ? null : new StringAttribute(attrValue);
		Attribute attr = new Attribute(attrID, null, null, value);
		attributes.add(attr);
	}
	private static void addURIAttribute(Set attributes, URI attrID, String uriString)
	{
		URI uri = URI.create(uriString);
		AttributeValue value = new AnyURIAttribute(uri);
		Attribute attr = new Attribute(attrID, null, null, value);
		attributes.add(attr);
	}

	private RequestHelper() {}
}