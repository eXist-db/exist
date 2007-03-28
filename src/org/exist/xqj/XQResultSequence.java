/**
 * 
 */
package org.exist.xqj;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.Properties;

import javax.xml.xquery.XQCommonHandler;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItem;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQWarning;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
public class XQResultSequence implements javax.xml.xquery.XQResultSequence {

	private Sequence resultSequence;

	//NB First Item is 1, Java Arrays start at 0!!! (before first is 0)
	private int iLength = 0; 
	private int iCurrent = 0;
	
	public XQResultSequence()
	{
		resultSequence = null;
		
	}

	public XQResultSequence(Sequence resultSequence)
	{
		this.resultSequence = resultSequence;
		iLength = resultSequence.getItemCount(); //do this once here as getLength() is expensive
	}
	
	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQResultSequence#clearWarnings()
	 */
	public void clearWarnings() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQResultSequence#getConnection()
	 */
	public XQConnection getConnection() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQResultSequence#getWarnings()
	 */
	public XQWarning getWarnings() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQItemAccessor#getAtomicValue()
	 */
	public String getAtomicValue() throws XQException
	{
		try
		{
			Item item = resultSequence.itemAt(iCurrent);
		
			return item.atomize().toString();
		}
		catch(XPathException xpe)
		{
			throw new XQException(xpe.getMessage());
		}
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

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#absolute(int)
	 */
	public boolean absolute(int itempos) throws XQException
	{
		if(itempos > 0 && itempos <= iLength)
		{
			iCurrent = itempos;
			return true;
		}
		
		return false;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#afterLast()
	 */
	public void afterLast() throws XQException
	{
		if(resultSequence != null)
			iCurrent = iLength + 1;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#beforeFirst()
	 */
	public void beforeFirst() throws XQException
	{	
		if(resultSequence != null)
			iCurrent = 0;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#close()
	 */
	public void close() throws XQException
	{
		iLength = 0;
		iCurrent = 0;
		resultSequence = null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#count()
	 */
	public int count() throws XQException
	{
		return iLength;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#first()
	 */
	public boolean first() throws XQException {

		iCurrent = 1;
		
		return (resultSequence != null);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#getItem()
	 */
	public XQItem getItem() throws XQException
	{
		Item item = resultSequence.itemAt(iCurrent - 1);
		
		return new org.exist.xqj.XQItem(item);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#getPosition()
	 */
	public int getPosition() throws XQException
	{
		return iCurrent;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#getSequenceAsString(java.util.Properties)
	 */
	public String getSequenceAsString(Properties props) throws XQException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isAfterLast()
	 */
	public boolean isAfterLast() throws XQException
	{
		return (iCurrent > iLength);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isBeforeFirst()
	 */
	public boolean isBeforeFirst() throws XQException
	{
		return (iCurrent == 0);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isClosed()
	 */
	public boolean isClosed()
	{
		return (resultSequence == null);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isFirst()
	 */
	public boolean isFirst() throws XQException
	{
		return(iCurrent == 1); 
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isLast()
	 */
	public boolean isLast() throws XQException
	{
		return (iCurrent == iLength);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isOnItem()
	 */
	public boolean isOnItem() throws XQException
	{
		return(iCurrent > 0 && iCurrent <= iLength);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#isScrollable()
	 */
	public boolean isScrollable() throws XQException
	{
		return (resultSequence != null && iLength > 0);
		
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#last()
	 */
	public boolean last() throws XQException
	{
		iCurrent = iLength;
		
		return (resultSequence != null);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#next()
	 */
	public boolean next() throws XQException
	{
		if(resultSequence == null)
			return false;
		
		iCurrent++;
		
		return (iCurrent <= iLength);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#previous()
	 */
	public boolean previous() throws XQException
	{
		if(resultSequence == null)
			return false;
		
		if(iCurrent > 0)	//dont ever go lower than 0 (0 is before first)
			iCurrent--;
		
		return (iCurrent > 0);
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#relative(int)
	 */
	public boolean relative(int itempos) throws XQException
	{
		if(resultSequence == null)
			return false;

		//positive number
		if(itempos > 0)
		{
			//call next() itempos number of times
			for(int i = 0; i < itempos; i++)
			{
				if(!next())
				{
					return false;
				}
			}
		}
		//negative number
		else if(itempos < 0)
		{
			//call previous() itempos number of times
			for(int i = 0; i < itempos; i++)
			{
				if(!previous())
				{
					return false;
				}
			}
		}
		
		return true;
	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#writeSequence(java.io.OutputStream, java.util.Properties)
	 */
	public void writeSequence(OutputStream os, Properties props)
			throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#writeSequence(java.io.Writer, java.util.Properties)
	 */
	public void writeSequence(Writer ow, Properties props) throws XQException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see javax.xml.xquery.XQSequence#writeSequenceToSAX(org.xml.sax.ContentHandler)
	 */
	public void writeSequenceToSAX(ContentHandler saxhdlr) throws XQException {
		// TODO Auto-generated method stub

	}

}
