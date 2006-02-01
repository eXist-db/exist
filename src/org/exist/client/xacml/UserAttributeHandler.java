package org.exist.client.xacml;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.xacml.XACMLConstants;

public class UserAttributeHandler implements AttributeHandler
{
	private SecurityManager manager;

	private UserAttributeHandler() {}
	public UserAttributeHandler(SecurityManager manager)
	{
		this.manager = manager;
	}
	public void filterFunctions(Set functions, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.SUBJECT_ID_ATTRIBUTE) ||
				id.equals(XACMLConstants.USER_NAME_ATTRIBUTE) || 
				id.equals(XACMLConstants.GROUP_ATTRIBUTE) || 
				id.equals(XACMLConstants.SUBJECT_NS_ATTRIBUTE))
		{
			List retain = new ArrayList(2);
			retain.add("equals");
			retain.add("=");
			functions.retainAll(retain);
		}
	}

	public boolean getAllowedValues(Set values, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.SUBJECT_ID_ATTRIBUTE))
		{
			User[] users = manager.getUsers();
			for(int i = 0; i < users.length; ++i)
				values.add(new Integer(users[i].getUID()));
			return false;
		}
		if(id.equals(XACMLConstants.USER_NAME_ATTRIBUTE))
		{
			User[] users = manager.getUsers();
			for(int i = 0; i < users.length; ++i)
				values.add(users[i].getName());
			return false;
		}
		if(id.equals(XACMLConstants.GROUP_ATTRIBUTE))
		{
			String[] groupNames = manager.getGroups();
			for(int i = 0; i < groupNames.length; ++i)
				values.add(groupNames[i]);
			return false;
		}
		if(id.equals(XACMLConstants.SUBJECT_NS_ATTRIBUTE))
		{
			values.add(XACMLConstants.SUBJECT_NS);
			return false;
		}
		return true;
	}

	public void checkUserValue(AttributeValue value, AttributeDesignator attribute) throws ParsingException
	{
		//user is not allowed to edit any of the handled attributes, so this
		//method will not be called for those attributes
	}

}
