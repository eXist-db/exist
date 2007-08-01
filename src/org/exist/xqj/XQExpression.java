/**
 * 
 */
package org.exist.xqj;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.TimeZone;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItem;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQResultSequence;
import javax.xml.xquery.XQSequence;
import javax.xml.xquery.XQWarning;

import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
public class XQExpression implements javax.xml.xquery.XQExpression {

	private DBBroker broker;
	
	/**
	 * 
	 */
	public XQExpression()
	{
		broker = null;
	}

	public XQExpression(DBBroker broker)
	{
		this.broker = broker;

	}
	
	
	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#cancel()
	 */
	public void cancel() throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#clearWarnings()
	 */
	public void clearWarnings() throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#close()
	 */
	public void close()
	{
		broker = null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#executeCommand(java.io.Reader)
	 */
	public void executeCommand(Reader command) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#executeCommand(java.lang.String)
	 */
	public void executeCommand(String command) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#executeQuery(java.io.InputStream)
	 */
	public XQResultSequence executeQuery(InputStream query) throws XQException
	{
		try
		{
			byte[] bytQuery = new byte[query.available()];
			
			return executeQuery(new String(bytQuery));
		}
		catch(IOException ioe)
		{
			throw new XQException("Could not read input stream for query");
		}
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#executeQuery(java.io.Reader)
	 */
	public XQResultSequence executeQuery(Reader query) throws XQException
	{
		try
		{
			StringBuffer bufQuery = new StringBuffer();
			
			while(query.ready())
			{	
				bufQuery.append((char)query.read());
			}
			
			return executeQuery(bufQuery.toString());
		}
		catch(IOException ioe)
		{
			throw new XQException("Could not read reader for query");
		}
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#executeQuery(java.lang.String)
	 */
	public XQResultSequence executeQuery(String query) throws XQException
	{
		//prepare the source of the query
		Source source = new StringSource(query);
		
		//get an xquery and the xquery bool
		XQuery xquery = broker.getXQueryService();
		XQueryPool xqPool = xquery.getXQueryPool();
		
		//try and get a pre-compiled query from the pool
		CompiledXQuery compiled = xqPool.borrowCompiledXQuery(broker, source);
        
		//setup the context
		XQueryContext context;
        if (compiled == null)
        {
            context = xquery.newContext(AccessContext.XQJ);
        }
        else
        {
            context = compiled.getContext();
        }
        //context.setStaticallyKnownDocuments(new XmldbURI[] { pathUri });
        
        try
        {
        	//if there was no pre-compiled query then compile it
        	if (compiled == null)
        	{
        		compiled = xquery.compile(context, source);
        	}
        	
        	//execute the query
        	Sequence resultSequence = xquery.execute(compiled, null);
        	
        	//return the result sequence
        	return new org.exist.xqj.XQResultSequence(resultSequence);
        }
        catch(IOException ioe)
        {
        
        }
        catch(XPathException xpe)
    	{
    	}
        finally
        {
        	//store the compiled query in the pool for re-use later
        	xqPool.returnCompiledXQuery(source ,compiled);
        }
        
        return null;
	}
	

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#getQueryLanguageTypeAndVersion()
	 */
	public int getQueryLanguageTypeAndVersion() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#getQueryTimeout()
	 */
	public int getQueryTimeout() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#getWarnings()
	 */
	public XQWarning getWarnings() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#isClosed()
	 */
	public boolean isClosed()
	{
		return broker == null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQExpression#setQueryTimeout(int)
	 */
	public void setQueryTimeout(int seconds) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindAtomicValue(javax.xml.namespace.QName, java.lang.String, javax.xml.xquery.XQItemType)
	 */
	public void bindAtomicValue(QName varname, String value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindBoolean(javax.xml.namespace.QName, boolean, javax.xml.xquery.XQItemType)
	 */
	public void bindBoolean(QName varname, boolean value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindByte(javax.xml.namespace.QName, byte, javax.xml.xquery.XQItemType)
	 */
	public void bindByte(QName varName, byte value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindContextItem(javax.xml.xquery.XQItem)
	 */
	public void bindContextItem(XQItem contextitem) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindDocument(javax.xml.namespace.QName, org.xml.sax.InputSource)
	 */
	public void bindDocument(QName varname, InputSource source)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindDouble(javax.xml.namespace.QName, double, javax.xml.xquery.XQItemType)
	 */
	public void bindDouble(QName varName, double value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindFloat(javax.xml.namespace.QName, float, javax.xml.xquery.XQItemType)
	 */
	public void bindFloat(QName varName, float value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindInt(javax.xml.namespace.QName, int, javax.xml.xquery.XQItemType)
	 */
	public void bindInt(QName varName, int value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindItem(javax.xml.namespace.QName, javax.xml.xquery.XQItem)
	 */
	public void bindItem(QName varName, XQItem value) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindLong(javax.xml.namespace.QName, long, javax.xml.xquery.XQItemType)
	 */
	public void bindLong(QName varName, long value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindNode(javax.xml.namespace.QName, org.w3c.dom.Node, javax.xml.xquery.XQItemType)
	 */
	public void bindNode(QName varName, Node value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindObject(javax.xml.namespace.QName, java.lang.Object, javax.xml.xquery.XQItemType)
	 */
	public void bindObject(QName varName, Object value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindSequence(javax.xml.namespace.QName, javax.xml.xquery.XQSequence)
	 */
	public void bindSequence(QName varName, XQSequence value)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#bindShort(javax.xml.namespace.QName, short, javax.xml.xquery.XQItemType)
	 */
	public void bindShort(QName varName, short value, XQItemType type)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#getImplicitTimeZone()
	 */
	public TimeZone getImplicitTimeZone() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQDynamicContext#setImplicitTimeZone(java.util.TimeZone)
	 */
	public void setImplicitTimeZone(TimeZone implicitTimeZone)
			throws XQException {
		// TODO Auto-generated method stub

	}

}
