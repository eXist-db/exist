package org.exist.security.xacml;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Account;

import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.attr.BagAttribute;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.cond.EvaluationResult;
import com.sun.xacml.ctx.Status;
import com.sun.xacml.finder.AttributeFinderModule;

/**
* This class looks up attributes for a Subject with a subject-category
* of access-subject.  The currently supported attributes are
* {@link XACMLConstants#USER_NAME_ATTRIBUTE user name} and
* {@link XACMLConstants#GROUP_ATTRIBUTE groups}.  This is a possible
* implementation point for LDAP lookup if this is desired
* in the future.
*/
public class UserAttributeModule extends AttributeFinderModule
{
	private static final Logger LOG = LogManager.getLogger(UserAttributeModule.class);
	
	private ExistPDP pdp;
	
	@SuppressWarnings("unused")
	private UserAttributeModule() {}
	
	/**
	* Creates an <code>AttributeFinderModule</code> capable of retrieving attributes
	* for a <code>User</code>.
	*
	* @param pdp The <code>ExistPDP</code> that is used to obtain information
	* about a given <code>User</code>.
	*/
	public UserAttributeModule(ExistPDP pdp)
	{
		this.pdp = pdp;
	}
    @Override
	public EvaluationResult findAttribute(URI attributeType, URI attributeId, URI issuer, URI subjectCategory, EvaluationCtx context, int designatorType)
	{
		if(designatorType != AttributeDesignator.SUBJECT_TARGET)
			{return errorResult("Invalid designator type: UserAttributeModule only handles subjects");}
		if(issuer != null)
			{return errorResult("UserAttributeModule cannot handle requests with an issuer specified.");}
		if(!XACMLConstants.ACCESS_SUBJECT.equals(subjectCategory))
			{return errorResult("UserAttributeModule can only handle subject category '" + XACMLConstants.ACCESS_SUBJECT + "'");}
		if(!XACMLConstants.STRING_TYPE.equals(attributeType))
			{return errorResult("UserAttributeModule can only handle data type '" + XACMLConstants.STRING_TYPE + "'");}
		
		final EvaluationResult subjectID = context.getSubjectAttribute(attributeType, XACMLConstants.SUBJECT_ID_ATTRIBUTE, issuer, subjectCategory);
		if(subjectID.indeterminate())
			{return subjectID;}
		
		AttributeValue value = subjectID.getAttributeValue();
		if(value == null)
			{return errorResult("Could not find user for context: null subject-id");}
		if(value.isBag())
		{
			final BagAttribute bag = (BagAttribute)value;
			if(bag.isEmpty())
				{return errorResult("Could not find user for context: no subject-id found");}
			if(bag.size() > 1)
				{return errorResult("Error finding attribute: Subject-id attribute is not unique.");}
			
			value = (AttributeValue)bag.iterator().next();
		}
		if(!(value instanceof StringAttribute))
			{return errorResult("Error finding attribute: Subject-id attribute must be a string.");}
		
		final String uid = ((StringAttribute)value).getValue();
		final Account user = pdp.getBrokerPool().getSecurityManager().getAccount(uid);
		if(user == null)
			{return errorResult("No user exists for UID '" + uid + "'");}
		
		if(XACMLConstants.GROUP_ATTRIBUTE.equals(attributeId))
			{return getGroups(user);}
		else if(XACMLConstants.USER_NAME_ATTRIBUTE.equals(attributeId))
			{return new EvaluationResult(new StringAttribute(user.getName()));}
		else
			{return errorResult("UserAttributeModule cannot handle attribute '" + attributeId + "'");}
	}
	
	//gets a bag consisting of the groups of the user
	private EvaluationResult getGroups(Account user)
	{
		final String[] groupArray = user.getGroups();
		final int size = (groupArray == null) ? 0 : groupArray.length;
		final Set<StringAttribute> groupAttributes = new HashSet<StringAttribute>(size);
		for(int i = 0; i < size; ++i)
			groupAttributes.add(new StringAttribute(groupArray[i]));
		final AttributeValue value = new BagAttribute(XACMLConstants.STRING_TYPE, groupAttributes);
		return new EvaluationResult(value);
		
	}
	//logs the specified message and exception
	//then, returns a result with status Indeterminate and the given message
	private static EvaluationResult errorResult(String message)
	{
		LOG.warn(message);
		return new EvaluationResult(new Status(Collections.singletonList(Status.STATUS_PROCESSING_ERROR), message));
	}

	/**
	* Indicates support of looking up attributes by
	* data supplied by an AttributeDesignator element,
	* specifically, a SubjectAttributeDesignator element.
	*
	* @return true to indicate that this module supports
	* this method of looking up attributes 
	*/
    @Override
	public boolean isDesignatorSupported()
	{
		return true;
	}
	/**
	* Returns a <code>Set</code> containing
	* <code>AttributeDesignator.SUBJECT_TARGET</code>
	* to indicate that this module only supports
	* <code>Subject</code>s.
	*
	* @return A <code>Set</code> indicating the supported
	* designator type.
	*/
    @Override
	public Set<Integer> getSupportedDesignatorTypes()
	{
		return Collections.singleton(Integer.valueOf(AttributeDesignator.SUBJECT_TARGET));
	}
	
	/**
	* A <code>Set</code> containing the <code>URI</code>s
	* {@link XACMLConstants#USER_NAME_ATTRIBUTE user name} and
	* {@link XACMLConstants#GROUP_ATTRIBUTE groups} to indicate that
	* these are the only attributes supported by this module.
	*
	* @return A <code>Set</code> indicating the supported
	* attribute ids.
	*/
    @Override
	public Set<URI> getSupportedIds()
	{
		final Set<URI> set = new HashSet<URI>(4);
		set.add(XACMLConstants.GROUP_ATTRIBUTE);
		set.add(XACMLConstants.USER_NAME_ATTRIBUTE);
		return set;
	}
}
