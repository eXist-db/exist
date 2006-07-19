/**
 * 
 */
package org.exist.xqj;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.Properties;

import javax.xml.xquery.XQCommonHandler;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemType;

import org.exist.xquery.value.Item;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
public class XQItem implements javax.xml.xquery.XQItem {

	Item item;
	
	/**
	 * 
	 */
	public XQItem()
	{
		item = null;
	}

	public XQItem(Item item)
	{
		this.item = item;
	}
	
	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItem#close()
	 */
	public void close() throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItem#isClosed()
	 */
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getAtomicValue()
	 */
	public String getAtomicValue() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getBoolean()
	 */
	public boolean getBoolean() throws XQException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getByte()
	 */
	public byte getByte() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getDouble()
	 */
	public double getDouble() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getFloat()
	 */
	public float getFloat() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getInt()
	 */
	public int getInt() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getItemAsString()
	 */
	public String getItemAsString() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getItemType()
	 */
	public XQItemType getItemType() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getLong()
	 */
	public long getLong() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getNode()
	 */
	public Node getNode() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getNodeUri()
	 */
	public URI getNodeUri() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getObject()
	 */
	public Object getObject() throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getObject(javax.xml.xquery.XQCommonHandler)
	 */
	public Object getObject(XQCommonHandler handler) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getShort()
	 */
	public short getShort() throws XQException {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#instanceOf(javax.xml.xquery.XQItemType)
	 */
	public boolean instanceOf(XQItemType type) throws XQException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#writeItem(java.io.OutputStream, java.util.Properties)
	 */
	public void writeItem(OutputStream os, Properties props) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#writeItem(java.io.Writer, java.util.Properties)
	 */
	public void writeItem(Writer ow, Properties props) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#writeItemToSAX(org.xml.sax.ContentHandler)
	 */
	public void writeItemToSAX(ContentHandler saxHandler) throws XQException {
		// TODO Auto-generated method stub

	}

}
