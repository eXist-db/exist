package org.exist.xqj;

import javax.xml.xquery.XQException;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */

public class XQMetaData implements javax.xml.xquery.XQMetaData {

	private final static boolean EXIST_COLLECTION_NESTING_SUPPORTED = true;
	private final static boolean EXIST_FULLAXIS_SUPPORTED = true;
	private final static boolean EXIST_XQUERY_MODULE_SUPPORTED = true;
	private final static boolean EXIST_SCHEMA_IMPORT_SUPPORTED = true;
	private final static boolean EXIST_SCHEMA_VALIDATION_SUPPORTED = true;
	
	XQConnection connection;
	
	
	public XQMetaData()
	{
		connection = null;
	}
	
	public XQMetaData(XQConnection connection)
	{
		this.connection = connection;
	}

	public int getMaxExpressionLength() throws XQException
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public int getMaxUserNameLength() throws XQException
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public int getProductMajorVersion() throws XQException
	{

		return 0;
	}

	public int getProductMinorVersion() throws XQException
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public String getProductName() throws XQException
	{
		throwIfClosed();
		
		return "eXist";
	}

	public String getProductVersion() throws XQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public String getUserName() throws XQException
	{
		throwIfClosed();
		
		return connection.getBroker().getUser().getName();
	}

	public int getXQJMajorVersion() throws XQException
	{
		throwIfClosed();
		
		return 0;
	}

	public int getXQJMinorVersion() throws XQException
	{
		throwIfClosed();
		
		return 2;
	}

	public String getXQJVersion() throws XQException
	{
		throwIfClosed();
		
		return "XQuery API for Java Version 0.5.0 (EDR)";
	}

	public boolean isCollectionNestingSupported() throws XQException
	{
		throwIfClosed();
		
		return EXIST_COLLECTION_NESTING_SUPPORTED;		
	}

	public boolean isFullAxisFeatureSupported() throws XQException
	{
		throwIfClosed();
		
		return EXIST_FULLAXIS_SUPPORTED;
	}

	public boolean isModuleFeatureSupported() throws XQException
	{
		throwIfClosed();
		
		return EXIST_XQUERY_MODULE_SUPPORTED;
	}

	public boolean isReadOnly() throws XQException
	{
		throwIfClosed();
		
		return false;
	}

	public boolean isSchemaImportFeatureSupported() throws XQException
	{
		throwIfClosed();
		
		return EXIST_SCHEMA_IMPORT_SUPPORTED;
	}

	public boolean isSchemaValidationFeatureSupported() throws XQException
	{
		throwIfClosed();
		
		return EXIST_SCHEMA_VALIDATION_SUPPORTED;
	}

	public boolean isSerializationFeatureSupported() throws XQException {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isStaticTypingExtensionsSupported() throws XQException {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isStaticTypingFeatureSupported() throws XQException {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isTransactionSupported() throws XQException {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isXQueryXSupported() throws XQException
	{
		return true;
	}

	public boolean wasCreatedFromJDBCConnection() throws XQException {
		// TODO Auto-generated method stub
		return false;
	}
	
	private void throwIfClosed() throws XQException
	{
		if(connection.isClosed())
			throw new XQException("Connection is closed");
	}

}
