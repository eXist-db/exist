package org.exist.xqj;

import java.net.URI;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQException;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */

public class XQItemType implements javax.xml.xquery.XQItemType
{
	private int type;
	
	public XQItemType()
	{
		type = javax.xml.xquery.XQItemType.XQBASETYPE_ANYTYPE;
	}
	
	public int getBaseType()
	{
			
		// TODO Auto-generated method stub
		return 0;
	}

	public int getItemKind()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public int getItemOccurrence()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public QName getNodeName() throws XQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public URI getSchemaURI()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public String getString() throws XQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public QName getTypeName() throws XQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isAnonymousType() 
	{
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isElementNillable()
	{
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isSchemaElement()
	{
		// TODO Auto-generated method stub
		return false;
	}

	public javax.xml.xquery.XQItemType getItemType() 
	{
		// TODO Auto-generated method stub
		return null;
	}

}
