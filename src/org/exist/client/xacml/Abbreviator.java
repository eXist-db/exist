package org.exist.client.xacml;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.security.xacml.XACMLConstants;

import com.sun.xacml.cond.FunctionBase;
import com.sun.xacml.cond.FunctionFactory;
import com.sun.xacml.cond.MatchFunction;

/**
 * Abbreviates the constants used in XACML to something
 * shorter and hopefully more user-friendly.  The getFullXXX
 * methods should generally only be called with an argument
 * obtained as the result of the corresponding getAbbreviatedXXX
 * method.
 */
public class Abbreviator
{
	private static final Logger LOG = Logger.getLogger(Abbreviator.class);
	private static final String[][] comparisonMap = { {"equal", "="},
			{"less-than-or-equal", "<="}, {"less-than", "<"},
			{"greater-than-or-equal", ">="}, {"greater-than", ">"} };
	
	private Map attributeIdMap = new HashMap(8);
	private Map typeMap = new HashMap(8);
	private Map functionMap = new HashMap(8);
	
	public Abbreviator() {} 
	
	public String getAbbreviatedId(URI uri)
	{
		if(uri == null)
			return null;
		String toString = uri.toString();
		if(toString.startsWith(XACMLConstants.EXIST_XACML_NS))
		{
			toString = toString.substring(XACMLConstants.EXIST_XACML_NS.length());
			int i = toString.lastIndexOf('#');
			if(i == -1)
			{
				i = toString.lastIndexOf('/');
				if(i != -1)
					toString = toString.substring(i+1);
			}
			else
				toString = toString.substring(i+1);
			
		}
		else if(toString.startsWith(XACMLConstants.VERSION_1_0_BASE))
		{
			toString = toString.substring(XACMLConstants.VERSION_1_0_BASE.length());
			int i = toString.lastIndexOf(':');
			if(i != -1)
				toString = toString.substring(i+1);
		}
		else
			return toString;
		
		attributeIdMap.put(toString, uri);
		return toString;
	}
	public URI getFullIdURI(String abbrev)
	{
		return get(attributeIdMap, abbrev); 
	}
	
	public String getAbbreviatedType(URI type)
	{
		if(type == null)
			return null;
		String toString = type.toString();
		if(toString.startsWith(XACMLConstants.XACML_DATATYPE_BASE))
			toString = toString.substring(XACMLConstants.XACML_DATATYPE_BASE.length());
		else if(toString.startsWith(Namespaces.SCHEMA_NS))
			toString = toString.substring(Namespaces.SCHEMA_NS.length()+1);
		else if(toString.startsWith(XACMLConstants.XQUERY_OPERATORS_NS))
			toString = toString.substring(XACMLConstants.XQUERY_OPERATORS_NS.length()+1);
		else
			return toString;
		
		typeMap.put(toString, type);
		return toString;
	}
	public URI getFullTypeURI(String abbrev)
	{
		return get(typeMap, abbrev);
	}
	
	public String getAbbreviatedCombiningID(URI uri)
	{
		if(uri == null)
			return null;
		String toString = uri.toString();
		if(toString.startsWith(XACMLConstants.RULE_COMBINING_BASE))
			toString = toString.substring(XACMLConstants.RULE_COMBINING_BASE.length());
		else if(toString.startsWith(XACMLConstants.POLICY_COMBINING_BASE))
			toString = toString.substring(XACMLConstants.POLICY_COMBINING_BASE.length());
		else
			return toString;
		
		return toString;
	}
	public URI getFullCombiningURI(String abbrev, boolean isRuleAlg)
	{
		if(abbrev == null)
			return null;
		String prefix = isRuleAlg ? XACMLConstants.RULE_COMBINING_BASE : XACMLConstants.POLICY_COMBINING_BASE;
		return URI.create(prefix + abbrev);
	}
	
	public String getAbbreviatedFunctionId(URI functionId)
	{
		if(functionId == null)
			return null;
	
		String toString = functionId.toString();
		if(toString.startsWith(FunctionBase.FUNCTION_NS))
			toString = toString.substring(FunctionBase.FUNCTION_NS.length());
		else
		{
			functionMap.put(functionId, toString);
			return toString;
		}
		
		if(toString.equals("regexp-string-match"))
			toString = "string-match";
		
		for(int i = 0; i < comparisonMap.length; ++i)
		{
			if(toString.endsWith(comparisonMap[i][0]))
			{
				toString = comparisonMap[i][1];
				return toString;
			}
		}
		functionMap.put(functionId, toString);
		return toString;
	}
	public URI getFullFunctionId(String abbrev, URI dataType)
	{
		if(abbrev == null || dataType == null)
			return null;
		URI uri = (URI)functionMap.get(abbrev);
		if(uri != null)
			return uri;
		
		String abbrevType = getAbbreviatedType(dataType);
		
		if(abbrev.equals("match") && abbrevType.equals("string"))
			return URI.create(MatchFunction.NAME_REGEXP_STRING_MATCH);
		
		for(int i = 0; i < comparisonMap.length; ++i)
		{
			if(abbrev.equals(comparisonMap[i][1]))
			{
				abbrev = abbrevType + "-" + comparisonMap[i][0];
				break;
			}
		}
		return URI.create(FunctionBase.FUNCTION_NS + abbrev);
	}

	//TODO: not valid for date-related conversions
	public Set getAbbreviatedTargetFunctions(URI dataType)
	{
		//note that sunxacml includes logical functions in the target
		//instance
		//this is almost certainly wrong
		//abbrevTargetFunctionId filters these out because they do
		//not include the data type in their name (not, and, or, n-or)
		FunctionFactory factory = FunctionFactory.getTargetInstance();
		Set functionIds = factory.getSupportedFunctions();
		
		Set ret = new HashSet();
		String abbrevType = getAbbreviatedType(dataType);
		String functionId;
		for(Iterator it = functionIds.iterator(); it.hasNext();)
		{
			functionId = (String)it.next();
			functionId = abbrevTargetFunctionId(functionId, abbrevType);
			if(functionId != null)
				ret.add(functionId);
		}
		return ret;
	}
	public String getAbbreviatedTargetFunctionId(URI functionId, URI dataType)
	{
		if(functionId == null || dataType == null)
			return null;
		return abbrevTargetFunctionId(functionId.toString(), getAbbreviatedType(dataType));
	}
	private String abbrevTargetFunctionId(String functionId, String abbrevType)
	{
		if(functionId.startsWith(FunctionBase.FUNCTION_NS))
			functionId = functionId.substring(FunctionBase.FUNCTION_NS.length());
		else
			return null;
		
		if(functionId.equals("regexp-string-match"))
			functionId = "string-match";
		if(functionId.startsWith(abbrevType))
		{
			functionId = functionId.substring(abbrevType.length() + 1);
			for(int i = 0; i < comparisonMap.length; ++i)
			{
				if(functionId.equals(comparisonMap[i][0]))
				{
					functionId = comparisonMap[i][1];
					return functionId;
				}
			}
			return functionId;
		}
		return null;
	}
	
	private static URI get(Map map, String abbrev)
	{
		URI ret = (URI)map.get(abbrev);
		return (ret == null) ? parse(abbrev) : ret;
	}
	private static URI parse(String abbrev)
	{
		try
		{
			return new URI(abbrev);
		}
		catch (URISyntaxException e)
		{
			LOG.warn("Invalid URI '" + abbrev + "'", e);
			return null;
		}
	}
}
