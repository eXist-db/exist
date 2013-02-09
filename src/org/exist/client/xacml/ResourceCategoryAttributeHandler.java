package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.exist.security.xacml.XACMLConstants;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

public class ResourceCategoryAttributeHandler implements AttributeHandler
{
	public void filterFunctions(Set<Object> functions, AttributeDesignator attribute)
	{
		final URI id = attribute.getId();
		if(id.equals(XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE))
		{
			final List<String> retain = new ArrayList<String>(2);
			retain.add("=");
			retain.add("equals");
			functions.retainAll(retain);
		}
	}

	public boolean getAllowedValues(Set<Object> values, AttributeDesignator attribute)
	{
		final URI id = attribute.getId();
		if(id.equals(XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE))
		{
			values.add(XACMLConstants.MAIN_MODULE_RESOURCE);
			values.add(XACMLConstants.FUNCTION_RESOURCE);
			values.add(XACMLConstants.METHOD_RESOURCE);
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
