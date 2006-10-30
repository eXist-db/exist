/**
 * 
 */
package org.exist.xqj;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
public class XQException extends javax.xml.xquery.XQException {

	/**
	 * @param message
	 */
	public XQException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 * @param cause
	 * @param vendorCode
	 * @param nextException
	 */
	public XQException(String message, Throwable cause, String vendorCode,
			javax.xml.xquery.XQException nextException) {
		super(message, cause, vendorCode, nextException);
		// TODO Auto-generated constructor stub
	}

}
