/**
 * 
 */
package org.exist.xqj;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.sql.Connection;
import java.util.Iterator;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQCommonHandler;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItem;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQSequence;
import javax.xml.xquery.XQSequenceType;

import org.exist.EXistException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
public class XQDataSource implements javax.xml.xquery.XQDataSource
{
	
	private final static Properties defaultProperties = new Properties();
	static
	{
		defaultProperties.setProperty("javax.xml.xquery.property.UserName", "guest");
		defaultProperties.setProperty("javax.xml.xquery.property.Password", "guest");
		defaultProperties.setProperty("javax.xml.xquery.property.MaxConnections", "0");
	}
	
	private final static String[] strPropertyNames = {
		"javax.xml.xquery.property.UserName",
		"javax.xml.xquery.property.Password",
		"javax.xml.xquery.property.MaxConnections"
	};
	private Properties properties;
	
	
	private int iLoginTimeout = -1;
	private PrintWriter pwLogWriter;
	private XQCommonHandler handler;
	
	/**
	 * 
	 */
	public XQDataSource()
	{
		//setup initial property values
		properties = new Properties(defaultProperties);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getConnection()
	 */
	public XQConnection getConnection() throws XQException
	{
		return getConnection(properties.getProperty(strPropertyNames[0]), properties.getProperty(strPropertyNames[1]));
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getConnection(java.sql.Connection)
	 */
	public XQConnection getConnection(Connection con) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getConnection(java.lang.String, java.lang.String)
	 */
	public XQConnection getConnection(String username, String password) throws XQException
	{
		try
		{
			//get the broker pool instance
			BrokerPool pool = BrokerPool.getInstance();
			
			//get the user
			User user = pool.getSecurityManager().getUser(username);
			
			if (user == null)
			{
	        	throw new XQException("User '" + username + "' does not exist");
	        }
	        if (!user.validate(password) )
	        {
	        	throw new XQException("Invalid password for user '" + username + "'");
	        }
			
	        //get a broker for the user
	        DBBroker broker = pool.get(user);
	        
	        //return the connection object
	        return new org.exist.xqj.XQConnection(broker, handler);
			
		}
		catch(EXistException ee)
		{
			throw new XQException("Can not access local database instance: " + ee.getMessage());
		}
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getLoginTimeout()
	 */
	public int getLoginTimeout()
	{
		return iLoginTimeout;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getLogWriter()
	 */
	public PrintWriter getLogWriter()
	{
		return pwLogWriter;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getProperty(java.lang.String)
	 */
	public String getProperty(String name) throws XQException
	{
		//check for a valid property name
		if(validPropertyName(name))
		{
				return properties.getProperty(name);
		}
		
		throw new XQException("Invalid Property Name");
		
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#getSupportedPropertyNames()
	 */
	public String[] getSupportedPropertyNames()
	{
		return strPropertyNames;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#setCommonHandler(javax.xml.xquery.XQCommonHandler)
	 */
	public void setCommonHandler(XQCommonHandler handler) throws XQException
	{
		this.handler = handler;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#setLoginTimeout(int)
	 */
	public void setLoginTimeout(int seconds) throws XQException
	{
		iLoginTimeout = seconds;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#setLogWriter(java.io.PrintWriter)
	 */
	public void setLogWriter(PrintWriter out) throws XQException
	{
		pwLogWriter = out;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#setProperties(java.util.Properties)
	 */
	public void setProperties(Properties props) throws XQException
	{
		//copy the valid properties accross
		for(int i = 0; i < strPropertyNames.length; i ++)
		{
			properties.setProperty(strPropertyNames[i], props.getProperty(strPropertyNames[i]));
		}
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataSource#setProperty(java.lang.String, java.lang.String)
	 */
	public void setProperty(String name, String value) throws XQException
	{
		//check for a valid property name
		if(validPropertyName(name))
		{
				//set the property
				properties.setProperty(name, value);
		}
		else
		{
			throw new XQException("Invalid Property Name");
		}
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createAtomicItemType(int)
	 */
	public XQItemType createAtomicItemType(int baseType) throws XQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItem(javax.xml.xquery.XQItem)
	 */
	public XQItem createItem(XQItem item) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromAtomicValue(java.lang.String, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromAtomicValue(String value, XQItemType type) throws XQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromBoolean(boolean, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromBoolean(boolean value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromByte(byte, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromByte(byte value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromDocument(org.xml.sax.InputSource)
	 */
	public XQItem createItemFromDocument(InputSource value) throws XQException, IOException
	{
	
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromDouble(double, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromDouble(double value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromFloat(float, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromFloat(float value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromInt(int, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromInt(int value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromLong(long, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromLong(long value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromNode(org.w3c.dom.Node, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromNode(Node value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromObject(java.lang.Object, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromObject(Object value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromShort(short, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromShort(short value, XQItemType type) throws XQException
	{
		return new org.exist.xqj.XQItem(value, type);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemType(int, int, javax.xml.namespace.QName)
	 */
	public XQItemType createItemType(int itemkind, int basetype, QName nodename)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemType(int, int, javax.xml.namespace.QName, javax.xml.namespace.QName, java.net.URI, boolean)
	 */
	public XQItemType createItemType(int itemkind, int basetype,
			QName nodename, QName typename, URI schemaURI, boolean nillable)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createSequence(java.util.Iterator)
	 */
	public XQSequence createSequence(Iterator i) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createSequence(javax.xml.xquery.XQSequence)
	 */
	public XQSequence createSequence(XQSequence s) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createSequenceType(javax.xml.xquery.XQItemType, int)
	 */
	public XQSequenceType createSequenceType(XQItemType item, int occurrence)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	
	/**
	 * Determines if the property name is valid in strPropertyNames
	 * 
	 * @param name	The property name
	 * @return True or False indicating the validitity of the supplied name 
	 */
	private boolean validPropertyName(String name)
	{
		//iterate through strPropertyNames
		for(int i = 0; i < strPropertyNames.length; i++)
		{
			if(strPropertyNames[i].equals(name))
			{
				//found the property name
				return true;
			}
		}
		
		return false;
	}
}
