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

package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.exist.security.xacml.XACMLConstants;
import org.exist.xquery.Function;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

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
		else if(id.equals(XACMLConstants.SOURCE_KEY_ATTRIBUTE) || id.equals(XACMLConstants.MODULE_NS_ATTRIBUTE) || id.equals(XACMLConstants.SOURCE_TYPE_ATTRIBUTE))
		{
			//empty filter
		}
	}

	public boolean getAllowedValues(Set values, AttributeDesignator attribute)
	{
		URI id = attribute.getId();
		if(id.equals(XACMLConstants.MODULE_CATEGORY_ATTRIBUTE))
		{
			values.add(XACMLConstants.MAIN_MODULE);
			values.add(XACMLConstants.EXTERNAL_LIBRARY_MODULE);
			values.add(XACMLConstants.INTERNAL_LIBRARY_MODULE);
			return false;
		}
		if(id.equals(XACMLConstants.SOURCE_KEY_ATTRIBUTE))
		{	
			return true;
		}
		else if(id.equals(XACMLConstants.SOURCE_TYPE_ATTRIBUTE))
		{
			values.add(XACMLConstants.FILE_SOURCE_TYPE);
			values.add(XACMLConstants.DB_SOURCE_TYPE);
			values.add(XACMLConstants.CLASS_SOURCE_TYPE);
			values.add(XACMLConstants.CLASSLOADER_SOURCE_TYPE);
			values.add(XACMLConstants.URL_SOURCE_TYPE);
			values.add(XACMLConstants.STRING_SOURCE_TYPE);
			values.add(XACMLConstants.COCOON_SOURCE_TYPE);
			return false;
		}	
		/*if(id.equals(XACMLConstants.CLASS_ATTRIBUTE))
		{
			values.add("org.exist.xquery.functions.ModuleImpl");
			addInternal(values, 1);
			return true;
		}*/
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
		/*String modules[][] = (String[][])config.getProperty(XQueryContext.PROPERTY_BULT_IN_MODULES);
		if(modules == null)
			return;
		for(int i = 0; i < modules.length; i++)
			values.add(modules[i][index]);*/
	}

	public void checkUserValue(AttributeValue value, AttributeDesignator attribute) throws ParsingException
	{
		
	}

}
