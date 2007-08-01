package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.exist.client.ClientFrame;
import org.exist.security.User;
import org.exist.security.xacml.XACMLConstants;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

public class UserAttributeHandler implements AttributeHandler
{
	private Collection collection;

	private UserAttributeHandler() {}
	public UserAttributeHandler(DatabaseInterface dbInterface)
	{
		if(dbInterface == null)
			throw new NullPointerException("Database interface cannot be null");
		this.collection = dbInterface.getPolicyCollection();
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
			User[] users = getUsers();
			for(int i = 0; i < users.length; ++i)
				values.add(new Integer(users[i].getUID()));
			return false;
		}
		if(id.equals(XACMLConstants.USER_NAME_ATTRIBUTE))
		{
			User[] users = getUsers();
			for(int i = 0; i < users.length; ++i)
				values.add(users[i].getName());
			return false;
		}
		if(id.equals(XACMLConstants.GROUP_ATTRIBUTE))
		{
			String[] groupNames = getGroups();
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
	
	private User[] getUsers()
	{
		UserManagementService service = getUserService();
		if(service == null)
			return new User[0];
		try
		{
			return service.getUsers();
		}
		catch (XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Could not get list of users: user attributes will be invalid", xe);
			return new User[0];
		}
	}
	private String[] getGroups()
	{
		UserManagementService service = getUserService();
		if(service == null)
			return new String[0];
		try
		{
			return service.getGroups();
		}
		catch (XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Could not get list of groups: group attributes will be invalid", xe);
			return new String[0];
		}
	}
	private UserManagementService getUserService()
	{
		try
		{
			return (UserManagementService)collection.getService("UserManagementService", "1.0");
		}
		catch (XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Could not get user management service: user and group attributes will be invalid.", xe);
			return null;
		}
	}

	public void checkUserValue(AttributeValue value, AttributeDesignator attribute) throws ParsingException
	{
		//user is not allowed to edit any of the handled attributes, so this
		//method will not be called for those attributes
	}

}
