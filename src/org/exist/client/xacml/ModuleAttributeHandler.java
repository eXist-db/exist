package org.exist.client.xacml;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.exist.security.xacml.XACMLConstants;
import org.exist.xquery.Function;

//TODO give user more help through this class
//
public class ModuleAttributeHandler implements AttributeHandler
{
	
	public ModuleAttributeHandler()
	{
	}
	public void filterFunctions(Set functions, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.MODULE_CATEGORY_ATTRIBUTE))
		{
			List retain = new ArrayList(2);
			retain.add("=");
			retain.add("equals");
			functions.retainAll(retain);
		}
		else if(id.equals(XACMLConstants.MODULE_SRC_ATTRIBUTE) || id.equals(XACMLConstants.MODULE_NS_ATTRIBUTE))
		{
			//empty filter
		}
	}

	public boolean getAllowedValues(Set values, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.MODULE_CATEGORY_ATTRIBUTE))
		{
			values.add(XACMLConstants.CONSTRUCTED_MAIN_MODULE);
			values.add(XACMLConstants.EXTERNAL_MAIN_MODULE);
			values.add(XACMLConstants.EXTERNAL_LIBRARY_MODULE);
			values.add(XACMLConstants.INTERNAL_LIBRARY_MODULE);
			return false;
		}
		if(id.equals(XACMLConstants.MODULE_SRC_ATTRIBUTE))
		{
			
			return true;
		}
		if(id.equals(XACMLConstants.CLASS_ATTRIBUTE))
		{
			values.add("org.exist.xquery.functions.ModuleImpl");
			addInternal(values, 1);
			return true;
		}
		if(id.equals(XACMLConstants.MODULE_NS_ATTRIBUTE))
		{
			values.add(Function.BUILTIN_FUNCTION_NS);
			addInternal(values, 0);
			return true;
		}
		return true;
	}
	//TODO: because BrokerPool and thus Configuration are not
	//	available remotely, this is commented until a remote
	//	solution is written
	//index = 0 for namespaces, 1 for the class name
	private void addInternal(Set values, int index)
	{
		/*String modules[][] = (String[][])config.getProperty("xquery.modules");
		if(modules == null)
			return;
		for(int i = 0; i < modules.length; i++)
			values.add(modules[i][index]);*/
	}

	public void checkUserValue(AttributeValue value, AttributeDesignator attribute) throws ParsingException
	{
		
	}

}
