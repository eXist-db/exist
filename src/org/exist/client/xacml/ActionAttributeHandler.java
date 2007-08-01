package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.exist.security.xacml.XACMLConstants;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

public class ActionAttributeHandler implements AttributeHandler
{
	public ActionAttributeHandler() {}
	
	public void filterFunctions(Set functions, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.ACTION_ID_ATTRIBUTE) || id.equals(XACMLConstants.ACTION_NS_ATTRIBUTE))
		{
			List retain = new ArrayList(2);
			retain.add("=");
			retain.add("equals");
			functions.retainAll(retain);
		}
	}

	public boolean getAllowedValues(Set values, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.ACTION_NS_ATTRIBUTE))
		{
			values.add(XACMLConstants.ACTION_NS);
			return false;
		}
		if(id.equals(XACMLConstants.ACTION_ID_ATTRIBUTE))
		{
			values.add(XACMLConstants.CALL_FUNCTION_ACTION);
			values.add(XACMLConstants.INVOKE_METHOD_ACTION);
			return false;
		}
		return true;
	}

	public void checkUserValue(AttributeValue value, AttributeDesignator attribute) throws ParsingException
	{
		//this handler restricts user entered values to only those returned in
		//getAllowedValues, so this method does nothing
	}

}
