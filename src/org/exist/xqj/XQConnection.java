/**
 * 
 */
package org.exist.xqj;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQCommonHandler;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQExpression;
import javax.xml.xquery.XQItem;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQMetaData;
import javax.xml.xquery.XQPreparedExpression;
import javax.xml.xquery.XQSequence;
import javax.xml.xquery.XQSequenceType;
import javax.xml.xquery.XQWarning;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * @author adam
 *
 */
public class XQConnection implements javax.xml.xquery.XQConnection {

	/**
	 * 
	 */
	
	private DBBroker broker;
	private XQCommonHandler handler;
	
	public XQConnection()
	{
		broker = null;
		handler = null;
	}
	
	public XQConnection(DBBroker broker, XQCommonHandler handler)
	{
		this.broker = broker;
		this.handler = handler;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#clearWarnings()
	 */
	public void clearWarnings() throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#close()
	 */
	public void close() throws XQException
	{
		try
		{
			BrokerPool pool = BrokerPool.getInstance();
		
			pool.release(broker);
			broker = null;
		}
		catch(EXistException ee)
		{
			throw new XQException("Unable to return broker to pool");
		}
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#commit()
	 */
	public void commit() throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#createExpression()
	 */
	public XQExpression createExpression() throws XQException {

		XQExpression expr = new org.exist.xqj.XQExpression(broker);
		
		return expr;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getHoldability()
	 */
	public int getHoldability() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getMetaData()
	 */
	public XQMetaData getMetaData() throws XQException
	{
		return new org.exist.xqj.XQMetaData(this);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getMetaDataProperty(java.lang.String)
	 */
	public String getMetaDataProperty(String key) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getQueryLanguageTypeAndVersion()
	 */
	public int getQueryLanguageTypeAndVersion() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getScrollability()
	 */
	public int getScrollability() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getSupportedMetaDataPropertyNames()
	 */
	public String[] getSupportedMetaDataPropertyNames() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getUpdatability()
	 */
	public int getUpdatability() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#getWarnings()
	 */
	public XQWarning getWarnings() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#isClosed()
	 */
	public boolean isClosed()
	{ 
		return (broker == null);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#prepareExpression(java.io.InputStream)
	 */
	public XQPreparedExpression prepareExpression(InputStream xquery)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#prepareExpression(java.io.InputStream, javax.xml.xquery.XQItemType)
	 */
	public XQPreparedExpression prepareExpression(InputStream xquery,
			XQItemType contextItemType) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#prepareExpression(java.io.Reader)
	 */
	public XQPreparedExpression prepareExpression(Reader xquery)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#prepareExpression(java.io.Reader, javax.xml.xquery.XQItemType)
	 */
	public XQPreparedExpression prepareExpression(Reader xquery,
			XQItemType contextItemType) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#prepareExpression(java.lang.String)
	 */
	public XQPreparedExpression prepareExpression(String xquery)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#prepareExpression(java.lang.String, javax.xml.xquery.XQItemType)
	 */
	public XQPreparedExpression prepareExpression(String xquery,
			XQItemType contextItemType) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#rollback()
	 */
	public void rollback() throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#setCommonHandler(javax.xml.xquery.XQCommonHandler)
	 */
	public void setCommonHandler(XQCommonHandler handler) throws XQException
	{
		this.handler = handler;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#setHoldability(int)
	 */
	public void setHoldability(int holdability) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#setQueryLanguageTypeAndVersion(int)
	 */
	public void setQueryLanguageTypeAndVersion(int langtype) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#setScrollability(int)
	 */
	public void setScrollability(int scrollability) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQConnection#setUpdatability(int)
	 */
	public void setUpdatability(int updatability) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createAtomicItemType(int)
	 */
	public XQItemType createAtomicItemType(int baseType) throws XQException {
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
	public XQItem createItemFromAtomicValue(String value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromBoolean(boolean, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromBoolean(boolean value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromByte(byte, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromByte(byte value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromDocument(org.xml.sax.InputSource)
	 */
	public XQItem createItemFromDocument(InputSource value) throws XQException,
			IOException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromDouble(double, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromDouble(double value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromFloat(float, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromFloat(float value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromInt(int, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromInt(int value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDataFactory#createItemFromLong(long, javax.xml.xquery.XQItemType)
	 */
	public XQItem createItemFromLong(long value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
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
	public XQItem createItemFromShort(short value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
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

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getBaseURI()
	 */
	public String getBaseURI() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getBoundarySpacePolicy()
	 */
	public int getBoundarySpacePolicy() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getConstructionMode()
	 */
	public int getConstructionMode() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getCopyNamespacesModeInherit()
	 */
	public int getCopyNamespacesModeInherit() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getCopyNamespacesModePreserve()
	 */
	public int getCopyNamespacesModePreserve() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getDefaultCollation()
	 */
	public String getDefaultCollation() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getDefaultElementTypeNamespace()
	 */
	public String getDefaultElementTypeNamespace() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getDefaultFunctionNamespace()
	 */
	public String getDefaultFunctionNamespace() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getDefaultOrderForEmptySequences()
	 */
	public int getDefaultOrderForEmptySequences() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getInScopeNamespacePrefixes()
	 */
	public String[] getInScopeNamespacePrefixes() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getNamespaceURI(java.lang.String)
	 */
	public String getNamespaceURI(String prefix) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getOrderingMode()
	 */
	public int getOrderingMode() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getStaticInScopeVariableNames()
	 */
	public QName[] getStaticInScopeVariableNames() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQStaticContext#getStaticInScopeVariableType(javax.xml.namespace.QName)
	 */
	public XQSequenceType getStaticInScopeVariableType(QName varname)
			throws XQException {
		// TODO Auto-generated method stub
		return null;
	}
	
	protected DBBroker getBroker()
	{
		return broker;
	}
	
}
