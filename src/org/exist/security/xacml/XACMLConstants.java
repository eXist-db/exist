package org.exist.security.xacml;

import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.attr.StringAttribute;
import java.net.URI;
import org.exist.storage.DBBroker;

/**
* This class provides constants for use in creating XACML requests.
* It provides some <code>String</code>s and <code>URI</code>s for
* both constants defined in the XACML specification and ones for
* eXist-specific use.  Because Sun's XACML implementation currently
* supports versions 1.0 and 1.1 of the specification but only
* recognizes the 1.0 version of constants, only the 1.0 strings
* are here.
*/
public final class XACMLConstants
{
	/* **************** XACML constants **************************** */
	//the base to namespaces, attribute ids, etc... in the XACML specification
	public static final String XACML_BASE = "urn:oasis:names:tc:xacml:";
	public static final String VERSION_1_0 = "1.0:";
	public static final String VERSION_1_0_BASE = XACML_BASE + VERSION_1_0;
	
	//XACML namespaces, one for a request context, the other for policies
	public static final String XACML_POLICY_NAMESPACE = VERSION_1_0_BASE + "policy";
	public static final String XACML_REQUEST_NAMESPACE = VERSION_1_0_BASE +  "context";

	//XACML root element names and referencing attribute names
	public static final String POLICY_SET_ELEMENT_LOCAL_NAME = "PolicySet";
	public static final String POLICY_ELEMENT_LOCAL_NAME = "Policy";
	public static final String POLICY_SET_ID_LOCAL_NAME = "PolicySetId";
	public static final String POLICY_ID_LOCAL_NAME = "PolicyId";

	//URIs for some XACML attribute IDs
	public static final URI ACTION_ID_ATTRIBUTE = URI.create(VERSION_1_0_BASE + "action:action-id");
	public static final URI ACTION_NS_ATTRIBUTE = URI.create(VERSION_1_0_BASE + "action:action-namespace");
	public static final URI SUBJECT_ID_ATTRIBUTE = URI.create(VERSION_1_0_BASE + "subject:subject-id");
	public static final URI RESOURCE_ID_ATTRIBUTE = URI.create(EvaluationCtx.RESOURCE_ID);

	//URIs for some XACML subject categories
	public static final URI ACCESS_SUBJECT = URI.create(VERSION_1_0_BASE + "subject-category:access-subject");
	public static final URI CODEBASE_SUBJECT = URI.create(VERSION_1_0_BASE + "subject-category:codebase");

	//datatype URIs
	public static final URI STRING_TYPE = URI.create(StringAttribute.identifier);

	/***************** eXist-specific constants *****************************/
	
	/**
	* The location of the top-level Policy and/or PolicySet documents.
	*/
	public static final String POLICY_COLLECTION = DBBroker.SYSTEM_COLLECTION + "/policies";

	/**
	* The namespace used for eXist-specific XACML constants.
	*/
	public static final String EXIST_XACML_NS = "http://exist-db.org/xacml";
	/**
	* The namespace used for action-related eXist-specific XACML constants
	*/
	public static final String ACTION_NS = EXIST_XACML_NS + "/action";
	/**
	* The namespace used for resource-related eXist-specific XACML
	* constants.
	*/
	public static final String RESOURCE_NS = EXIST_XACML_NS + "/resource";
	/**
	* The namespace used for subject-related eXist-specific XACML
	* constants.
	*/
	public static final String SUBJECT_NS = EXIST_XACML_NS + "/subject";

	/**
	* The attribute ID for the attribute that provides the namespace
	* URI of a module.
	*/
	public static final URI SUBJECT_NS_ATTRIBUTE = URI.create(SUBJECT_NS + "#subject-namespace");
	/**
	* The attribute ID for the attribute that provides the namespace
	* URI of a module.
	*/
	public static final URI RESOURCE_NS_ATTRIBUTE = URI.create(RESOURCE_NS + "#resource-namespace");
	/**
	* The attribute ID for the attribute that provides the category
	* of an XQuery module.
	*/
	public static final URI MODULE_CATEGORY_ATTRIBUTE = URI.create(EXIST_XACML_NS + "#module-category");
	/**
	 * The attribute ID for the attribute the describes a module's source. 
	 */
	public static final URI MODULE_SRC_ATTRIBUTE = URI.create(SUBJECT_NS + "#module-src");
	/**
	* The attribute ID for the attribute that provides the category of
	* a resource.
	*/
	public static final URI RESOURCE_CATEGORY_ATTRIBUTE = URI.create(RESOURCE_NS + "#resource-category");
	/**
	* The attribute ID for the attribute that provides the name of a Java class
	* being reflectively loaded.
	*/
	public static final URI CLASS_ATTRIBUTE = URI.create(RESOURCE_NS + "#class");
	/**
	* The attribute ID for the attribute that provides the name of a user.
	*/
	public static final URI USER_NAME_ATTRIBUTE = URI.create(SUBJECT_NS + "#name");
	/**
	* The attribute ID for the attribute that provides the names of the groups
	* to which a user belongs.
	*/
	public static final URI GROUP_ATTRIBUTE = URI.create(SUBJECT_NS + "#group");


	/**
	* The internal/builtin XQuery library module type.
	*/
	public static final String INTERNAL_LIBRARY_MODULE = "internal library";
	/**
	* The external/non-builtin XQuery library module type.
	*/
	public static final String EXTERNAL_LIBRARY_MODULE = "external library";
	/**
	* The external/non-builtin XQuery library module type.
	*/
	public static final String EXTERNAL_MAIN_MODULE = "external main";
	/**
	* The external/non-builtin XQuery library module type.
	*/
	public static final String CONSTRUCTED_MAIN_MODULE = "constructed main";


	/**
	* The action-id corresponding to a request to reflectively invoke a
	* method of a Java class in XQuery.
	*/
	public static final String INVOKE_METHOD_ACTION = "invoke method";
	/**
	* The action-id corresponding to a request to call a function in an XQuery.
	*/
	public static final String CALL_FUNCTION_ACTION = "call function";

	/**
	* The Java method resource type.
	*/
	public static final String METHOD_RESOURCE = "method";
	/**
	* The query function resource type.
	*/
	public static final String FUNCTION_RESOURCE = "function";
	/**
	* The main XQuery module resource type.
	*/
	public static final String MAIN_MODULE_RESOURCE = "main module";
	
	private XACMLConstants() {}
}