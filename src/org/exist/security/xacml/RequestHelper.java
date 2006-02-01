package org.exist.security.xacml;

import com.sun.xacml.attr.AnyURIAttribute;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.ctx.Attribute;
import com.sun.xacml.ctx.RequestCtx;
import com.sun.xacml.ctx.Subject;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.Module;
import org.exist.xquery.XQueryContext;

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
	*  <li>No environment attributes are explicitly generated; these
	* will be handled by the <code>CurrentEnvModule</code> if a policy
	* requests any of the required environment attributes.</li>
	*
	* </ul>
	*
	* @param user The current user executing the query.
	* @param contextModule The query containing the reflection.
	* @param className The name of the class that is being accessed or loaded.
	* @param methodName The name of the method that is being invoked
	* @return A <code>RequestCtx</code> that represents the access in question.
	*/
	public RequestCtx createReflectionRequest(User user, Module contextModule, String className, String methodName)
	{
		Set subjects = createQuerySubjects(user, contextModule);
		Set resourceAttributes = createReflectionResource(className, methodName);
		Set actionAttributes = createBasicAction(XACMLConstants.INVOKE_METHOD_ACTION);

		return new RequestCtx(subjects, resourceAttributes, actionAttributes, Collections.EMPTY_SET);
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
	* {@link XACMLConstants#MODULE_SRC_ATTRIBUTE modules source} and the
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
	*  <li>No environment attributes are explicitly generated; these
	* will be handled by the <code>CurrentEnvModule</code> if a policy
	* requests any of the required environment attributes.</li>
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
		String namespaceURI = functionName.getNamespaceURI();
		Module functionModule = context.getModule(namespaceURI);
		if(functionModule == null)
		{
			//main module, not a library module, so access to function is always allowed
			return null;
		}
		
		User user = context.getUser();
		Set subjects = createQuerySubjects(user, contextModule);

		Set resourceAttributes = new HashSet(8);
		addStringAttribute(resourceAttributes, XACMLConstants.MODULE_CATEGORY_ATTRIBUTE, getModuleCategory(functionModule));
		addStringAttribute(resourceAttributes, XACMLConstants.MODULE_SRC_ATTRIBUTE, generateModuleID(functionModule));
		addURIAttribute(resourceAttributes, XACMLConstants.MODULE_NS_ATTRIBUTE, namespaceURI);		
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, XACMLConstants.FUNCTION_RESOURCE);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, functionName.getLocalName());

		Set actionAttributes = createBasicAction(XACMLConstants.CALL_FUNCTION_ACTION);

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
	public Subject createUserSubject(User user)
	{
		AttributeValue value = new StringAttribute(user.getName());
		Attribute attr = new Attribute(XACMLConstants.SUBJECT_ID_ATTRIBUTE, null, null, value);
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
	public Set createBasicAction(String action)
	{
		if(action == null)
			return null;

		Set attributes = new HashSet(4);
		addStringAttribute(attributes, XACMLConstants.ACTION_ID_ATTRIBUTE, action);
		addURIAttribute(attributes, XACMLConstants.ACTION_NS_ATTRIBUTE, XACMLConstants.ACTION_NS);

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
			return null;

		Set attributes = new HashSet(8);
		addURIAttribute(attributes, XACMLConstants.SUBJECT_NS_ATTRIBUTE, module.getNamespaceURI());
		addStringAttribute(attributes, XACMLConstants.MODULE_CATEGORY_ATTRIBUTE, getModuleCategory(module));
		addStringAttribute(attributes, XACMLConstants.SUBJECT_ID_ATTRIBUTE, generateModuleID(module));

		return new Subject(XACMLConstants.CODEBASE_SUBJECT, attributes);
	}


	/**
	* Creates a <code>Set</code> of <code>Attribute</code>s for a resource
	* representing Java reflection in an XQuery.
	* The {@link XACMLConstants#RESOURCE_CATEGORY_ATTRIBUTE resource-category}
	* attribute is {@link XACMLConstants#METHOD_RESOURCE method}.
	* The {@link XACMLConstants#CLASS_ATTRIBUTE class} attribute is
	* the class name.  The
	* {@link XACMLConstants#RESOURCE_ID_ATTRIBUTE resource-id} attribute 
	* for class loading is the method name.
	*
	* @param className The name of the Java class
	* @param methodName The name of the method being invoked
	* @return A <code>Set</code> containing the <code>Attribute</code>s
	* describing access to Java code by reflection.
	*/
	public Set createReflectionResource(String className, String methodName)
	{
		if(className == null)
			throw new NullPointerException("Class name cannot be null");
		if(methodName == null)
			throw new NullPointerException("Method name cannot be null");
		
		Set resourceAttributes = new HashSet(4);

		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, XACMLConstants.METHOD_RESOURCE);
		addStringAttribute(resourceAttributes, XACMLConstants.CLASS_ATTRIBUTE, className);
		addStringAttribute(resourceAttributes, XACMLConstants.RESOURCE_ID_ATTRIBUTE, methodName);

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
	public Set createQuerySubjects(User user, Module contextModule)
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

	private static String getModuleCategory(Module module)
	{
		if(module == null)
			return null;
		return module.isInternalModule() ? XACMLConstants.INTERNAL_LIBRARY_MODULE : XACMLConstants.EXTERNAL_LIBRARY_MODULE;
	}

	//convenience methods for adding an AttributeValue to a Set of attributes
	private static void addStringAttribute(Set attributes, URI attrID, String attrValue)
	{
		if(attrValue == null)
			throw new NullPointerException("Attribute value cannot be null");
		AttributeValue value = new StringAttribute(attrValue);
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

	RequestHelper() {}
}