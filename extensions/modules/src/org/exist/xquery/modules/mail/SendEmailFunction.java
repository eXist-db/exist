/*
 *  eXist Mail Module Extension SendEmailFunction
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: SendEmailFunction.java,v 1.12 2006/03/01 13:52:00 deliriumsky Exp $
 */

package org.exist.xquery.modules.mail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.system.GetVersion;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Type;

//send-email specific imports
import org.exist.util.Base64Encoder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * eXist Mail Module Extension SendEmailFunction
 * 
 * The email sending functionality of the eXist Mail Module Extension that
 * allows email to be sent from XQuery using either SMTP or Sendmail.  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Robert Walpole <robert.walpole@devon.gov.uk>
 * @serial 2007-10-05
 * @version 1.2
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class SendEmailFunction extends BasicFunction
{
	//TODO: Feature - Add an option to execute the function Asynchronously as Socket operations for SMTP can be slow (Sendmail seems fast enough). Will require placing the SMTP code in a thread.
	//TODO: Feature - Add a facility for the user to add their own message headers.
	//TODO: Read the location of sendmail from the configuration file. Can vary from system to system
    
    private String charset;
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("send-email", MailModule.NAMESPACE_URI, MailModule.PREFIX),
			"Sends an email $a through the SMTP Server $b, or if $b is () tries to use the local sendmail program. $a is the email in the following format <mail><from/><reply-to/><to/><cc/><bcc/><subject/><message><text/><xhtml/></message><attachment filename=\"\" mimetype=\"\">xs:base64Binary</attachment></mail>. $c defines the charset value used in the \"Content-Type\" message header (Defaults to UTF-8)",
			new SequenceType[]
			{
				new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	/**
	 * SendEmail Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public SendEmailFunction(XQueryContext context)
	{
		super(context, signature);
    }

	/**
	 * evaluate the call to the xquery send-email function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the send-email() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A sequence representing the result of the send-email() function call
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		try
		{	
			//get the charset parameter, default to UTF-8
            if (!args[2].isEmpty())
            {
                charset =  args[2].getStringValue();
            }
            else
            {
                charset =  "UTF-8";
            }
            
			//Parse the XML <mail> into a mail Object
			Mail theMail = ParseMailXML( ((NodeValue)args[0].itemAt(0)).getNode() );
			
			//Send email with Sendmail or SMTP?
			if(!args[1].isEmpty())
			{
				//SMTP
				if(SendSMTP(theMail, args[1].getStringValue()))
				{
					return(BooleanValue.TRUE);
				}
			}
			else
			{
				//Sendmail
				if(SendSendmail(theMail))
				{
					return(BooleanValue.TRUE);
				}
			}
			
			//Failed to send email
			return(BooleanValue.FALSE);
		}
		catch(TransformerException e)
		{
			throw new XPathException("Could not Transform XHTML Message Body: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Sends an email using the Operating Systems sendmail application
	 * 
	 * @param aMail		A mail object representing the email to send
	 * @return		boolean value of true of false indicating success or failure to send email
	 */
	private boolean SendSendmail(Mail aMail)
	{
		try
		{	
			//Create a vector of all Recipients, should include to, cc and bcc recipient
			Vector allrecipients = new Vector();
			allrecipients.addAll(aMail.getTo());
			allrecipients.addAll(aMail.getCC());
			allrecipients.addAll(aMail.getBCC());
			
			//Get a string of all recipients email addresses
			String recipients = "";
			
			for(int x = 0; x < allrecipients.size(); x++)
			{
				//Check format of to address does it include a name as well as the email address?
				if(((String)allrecipients.elementAt(x)).indexOf("<") != -1)
				{
					//yes, just add the email address
					recipients += " " + ((String)allrecipients.elementAt(x)).substring(((String)allrecipients.elementAt(x)).indexOf("<") + 1, ((String)allrecipients.elementAt(x)).indexOf(">"));
				}
				else
				{
					//add the email address
					recipients += " " + ((String)allrecipients.elementAt(x));
				}
			}
			
			//Create a sendmail Process
			Process p = Runtime.getRuntime().exec("/usr/sbin/sendmail" + recipients);
			
			//Get a Buffered Print Writer to the Processes stdOut
			PrintWriter out = new PrintWriter(new OutputStreamWriter(p.getOutputStream(),charset));
			
			//Send the Message
			WriteMessage(out, aMail);
			
			//Close the stdOut
			out.close();
			
		}
		catch(IOException e)
		{
			LOG.error(e);
			return(false);
		}
		
		//Message Sent Succesfully
		LOG.info("send-email() message sent using Sendmail " + new Date());
		return(true);
	}
	
	/**
	 * Sends an email using SMTP
	 * 
	 * @param aMail		A mail object representing the email to send
	 * @param SMTPServer	The SMTP Server to send the email through
	 * @return		boolean value of true of false indicating success or failure to send email
	 */
	private boolean SendSMTP(Mail aMail, String SMTPServer)
	{
		final int TCP_PROTOCOL_SMTP = 25;									//SMTP Protocol
		String SMTPResult = "";												//Holds the server Result code when an SMTP Command is executed
		
		try
		{
			//Create a Socket and connect to the SMTP Server
			Socket smtpSock = new Socket(SMTPServer, TCP_PROTOCOL_SMTP);
			
			//Create a Buffered Reader for the Socket
			BufferedReader in = new BufferedReader(new InputStreamReader(smtpSock.getInputStream()));
				
			//Create an Output Writer for the Socket
			PrintWriter out = new PrintWriter(new OutputStreamWriter(smtpSock.getOutputStream(),charset));
			
			//First line sent to us from the SMTP server should be "220 blah blah", 220 indicates okay
			SMTPResult = in.readLine();
			if(!SMTPResult.substring(0, 3).toString().equals("220"))
			{
				LOG.error("Error - SMTP Server not ready!");
				return(false);
			}
				
			//Say "HELO"
			out.println("HELO " + InetAddress.getLocalHost().getHostName());
			out.flush();
				
			//get "HELLO" response, should be "250 blah blah"
			SMTPResult = in.readLine();
			if(!SMTPResult.substring(0, 3).toString().equals("250"))
			{
				LOG.error("Error - SMTP HELO Failed: " + SMTPResult);
				return(false);
			}
			
			//Send "MAIL FROM:"
			//Check format of from address does it include a name as well as the email address?
			if(aMail.getFrom().indexOf("<") != -1)
			{
				//yes, just send the email address
				out.println("MAIL FROM: " + aMail.getFrom().substring(aMail.getFrom().indexOf("<") + 1, aMail.getFrom().indexOf(">")));
			}
			else
			{
				//no, doesnt include a name so send the email address
				out.println("MAIL FROM: " + aMail.getFrom());
			}
			out.flush();
				
			//Get "MAIL FROM:" response
			SMTPResult = in.readLine();
			if(!SMTPResult.substring(0, 3).toString().equals("250"))
			{
				LOG.error("Error - SMTP MAIL FROM failed: " + SMTPResult);
				return(false);
			}
			
			//RCPT TO should be issued for each to, cc and bcc recipient
			Vector allrecipients = new Vector();
			allrecipients.addAll(aMail.getTo());
			allrecipients.addAll(aMail.getCC());
			allrecipients.addAll(aMail.getBCC());
				
			for(int x = 0; x < allrecipients.size(); x++)
			{
				//Send "RCPT TO:"
				//Check format of to address does it include a name as well as the email address?
				if(((String)allrecipients.elementAt(x)).indexOf("<") != -1)
				{
					//yes, just send the email address
					out.println("RCPT TO: " + ((String)allrecipients.elementAt(x)).substring(((String)allrecipients.elementAt(x)).indexOf("<") + 1, ((String)allrecipients.elementAt(x)).indexOf(">")));
				}
				else
				{
					out.println("RCPT TO: " + ((String)allrecipients.elementAt(x)));
				}
				out.flush();
				
				//Get "RCPT TO:" response
				SMTPResult = in.readLine();
				if(!SMTPResult.substring(0, 3).toString().equals("250"))
				{
					LOG.error("Error - SMTP RCPT TO failed: " + SMTPResult);
				}
			}
			
			
			//SEND "DATA" 
			out.println("DATA");
			out.flush();
			
			//Get "DATA" response, should be "354 blah blah"
			SMTPResult = in.readLine();
			if(!SMTPResult.substring(0, 3).toString().equals("354"))
			{
				LOG.error("Error - SMTP DATA failed: " + SMTPResult);
				return(false);
			}
			
			//Send the Message
			WriteMessage(out, aMail);
			
			//Get end message response, should be "250 blah blah"
			SMTPResult = in.readLine();
			if(!SMTPResult.substring(0, 3).toString().equals("250"))
			{
				LOG.error("Error - Message not accepted: " + SMTPResult);
				return(false);
			}
		}
		catch(IOException e)
		{
			LOG.error(e);
			return(false);
		}
		
		//Message Sent Succesfully
		LOG.info("send-email() message sent using SMTP " + new Date());
		return(true);
	}
	
	
	/**
	 * Writes an email payload (Headers + Body) from a mail object
	 * 
	 * @param out		A PrintWriter to receive the email
	 * @param aMail		A mail object representing the email to write out		
	 */
	private void WriteMessage(PrintWriter out, Mail aMail) throws IOException
	{
		String Version = eXistVersion();									//Version of eXist
		String MultipartBoundary = "eXist.multipart." + Version;			//Multipart Boundary
		
		//write the message headers
        
		out.println("From: " + encode64Address(aMail.getFrom()));
		if(aMail.getReplyTo() != null)
		{
			out.println("Reply-To: " + encode64Address(aMail.getReplyTo()));
		}
		for(int x = 0; x < aMail.countTo(); x++)
		{	
			out.println("To: " + encode64Address(aMail.getTo(x)));
		}
		for(int x = 0; x < aMail.countCC(); x++)
		{	
			out.println("CC: " + encode64Address(aMail.getCC(x)));
		}
		for(int x = 0; x < aMail.countBCC(); x++)
		{	
			out.println("BCC: " + encode64Address(aMail.getBCC(x)));
		}
		out.println("Date: " + getDateRFC822());
		out.println("Subject: " + encode64(aMail.getSubject()));
		out.println("X-Mailer: eXist " + Version + " util:send-email()");
		out.println("MIME-Version: 1.0");
		

		boolean multipartAlternative = false;
		String multipartBoundary = null;
		
		if(aMail.attachmentIterator().hasNext())
		{
			// we have an attachment as well as text and/or html so we need a multipart/mixed message
			multipartBoundary =  MultipartBoundary;
		}
		else if(!aMail.getText().equals("") && !aMail.getXHTML().equals(""))
		{
			// we have text and html so we need a multipart/alternative message and no attachment
			multipartAlternative = true;
			multipartBoundary = MultipartBoundary + "_alt";
		}
		else
		{
			// we have either text or html and no attachment this message is not multipart
		}
					
		//content type
		if(multipartBoundary != null)
		{
			//multipart message
			
			out.println("Content-Type: " + (multipartAlternative ? "multipart/alternative" : "multipart/mixed") + "; boundary=\"" + multipartBoundary + "\";");
		
			//Mime warning
			out.println();
			out.println("Error your mail client is not MIME Compatible");
			
			out.println("--" + multipartBoundary);
		}
		
		// TODO - need to put out a multipart/mixed boundary here when HTML, text and attachment present
		if(!aMail.getText().toString().equals("") && !aMail.getXHTML().toString().equals("") && aMail.attachmentIterator().hasNext())
		{
			out.println("Content-Type: multipart/alternative; boundary=\"" + MultipartBoundary + "_alt\";");
			out.println("--" + MultipartBoundary + "_alt");
		}
		
		//text email
		if(!aMail.getText().toString().equals(""))
		{
			out.println("Content-Type: text/plain; charset=" + charset);
			out.println("Content-Transfer-Encoding: 8bit");
			
			//now send the txt message
			out.println();
			out.println(aMail.getText());
			
			if(multipartBoundary != null)
			{
				if(!aMail.getXHTML().toString().equals("") || aMail.attachmentIterator().hasNext())
				{
					if(!aMail.getText().toString().equals("") && !aMail.getXHTML().toString().equals("") && aMail.attachmentIterator().hasNext())
					{
						out.println("--" + MultipartBoundary + "_alt");
					}
					else
					{
						out.println("--" + multipartBoundary);
					}						
				}
				else
				{
					if(!aMail.getText().toString().equals("") && !aMail.getXHTML().toString().equals("") && aMail.attachmentIterator().hasNext())
					{
						out.println("--" + MultipartBoundary + "_alt--");
					}
					else
					{
						out.println("--" + multipartBoundary + "--");
					}						
				}
			}
		}

		//HTML email
		if(!aMail.getXHTML().toString().equals(""))
		{
			out.println("Content-Type: text/html; charset=" + charset);
			out.println("Content-Transfer-Encoding: 8bit");
				
			//now send the html message
			out.println();
			out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
			out.println(aMail.getXHTML());
			
			if(multipartBoundary != null)
			{
				if(aMail.attachmentIterator().hasNext())
				{
					if(!aMail.getText().toString().equals("") && !aMail.getXHTML().toString().equals("") && aMail.attachmentIterator().hasNext())
					{
						out.println("--" + MultipartBoundary + "_alt--");
						out.println("--" + multipartBoundary);
					}
					else
					{
						out.println("--" + multipartBoundary);
					}
				}
				else
				{
					if(!aMail.getText().toString().equals("") && !aMail.getXHTML().toString().equals("") && aMail.attachmentIterator().hasNext())
					{
						out.println("--" + MultipartBoundary + "_alt--");
					}
					else
					{
						out.println("--" + multipartBoundary + "--");
					}
				}
			}
		}
		
		//attachments
		if(aMail.attachmentIterator().hasNext())
		{
			for(Iterator itAttachment = aMail.attachmentIterator(); itAttachment.hasNext(); )
			{
				MailAttachment ma = (MailAttachment) itAttachment.next();
				out.println("Content-Type: " + ma.getMimeType() + "; name=\"" + ma.getFilename() + "\"");
				out.println("Content-Transfer-Encoding: base64");
				out.println("Content-Description: " + ma.getFilename());
				out.println("Content-Disposition: attachment; filname=\"" + ma.getFilename() + "\"");
				out.println();
				out.println(ma.getData());
				if(itAttachment.hasNext())
				{
					out.println("--" + multipartBoundary);
				}
			}
			
			//Emd multipart message
			out.println("--" + multipartBoundary + "--");
		}

		//end the message, <cr><lf>.<cr><lf>
		out.println();
		out.println(".");
		out.println();
		out.flush();
	}
	
	
	/**
	 * Get's the version of eXist we are running
	 * The eXist version is used as part of the multipart separator
	 * 
	 * @return		The eXist Version
	 */
	private String eXistVersion() throws IOException
	{
		Properties sysProperties = new Properties();
		sysProperties.load(GetVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		return((String)sysProperties.getProperty("product-version", "unknown version"));
	}
	
	/**
	 * Constructs a mail Object from an XML representation of an email
	 * 
	 * The XML email Representation is expected to look something like this
	 * 
	 * <mail>
	 * 	<from></from>
	 * 	<reply-to></reply-to>
	 * 	<to></to>
	 * 	<cc></cc>
	 * 	<bcc></bcc>
	 * 	<subject></subject>
	 * 	<message>
	 * 		<text></text>
	 * 		<xhtml></xhtml>
	 * 	</message>
	 * </mail>
	 * 
	 * @param message	The XML mail Node
	 * @return		A mail Object representing the XML mail Node
	 */
	private Mail ParseMailXML(Node message) throws TransformerException
	{
		//New mail Object
		Mail theMail = new Mail(); 
		
		//Make sure that message has a Mail node
		if(message.getNodeType() == Node.ELEMENT_NODE && message.getLocalName().equals("mail"))
		{
			//Get the First Child
			Node child = message.getFirstChild();
			while(child != null)
			{
				//Parse each of the child nodes
				if(child.getNodeType() == Node.ELEMENT_NODE && child.hasChildNodes())
				{
					
					if(child.getLocalName().equals("from"))
					{
						theMail.setFrom(child.getFirstChild().getNodeValue()); 
					}
					if(child.getLocalName().equals("reply-to"))
					{
						theMail.setReplyTo(child.getFirstChild().getNodeValue());
					}
					else if(child.getLocalName().equals("to"))
					{
						theMail.addTo(child.getFirstChild().getNodeValue());
					}
					else if(child.getLocalName().equals("cc"))
					{
						theMail.addCC(child.getFirstChild().getNodeValue());
					}
					else if(child.getLocalName().equals("bcc"))
					{
						theMail.addBCC(child.getFirstChild().getNodeValue());
					}
					else if(child.getLocalName().equals("subject"))
					{
						theMail.setSubject(child.getFirstChild().getNodeValue());
					}
					else if(child.getLocalName().equals("message"))
					{
						//If the message node, then parse the child text and xhtml nodes
						Node bodyPart = child.getFirstChild();
						while(bodyPart != null)
						{
							if(bodyPart.getLocalName().equals("text"))
							{
								theMail.setText(bodyPart.getFirstChild().getNodeValue());
							}
							else if(bodyPart.getLocalName().equals("xhtml"))
							{
								//Convert everything inside <xhtml></xhtml> to text
								TransformerFactory transFactory = TransformerFactory.newInstance();
								Transformer transformer = transFactory.newTransformer();
								DOMSource source = new DOMSource(bodyPart.getFirstChild());
								StringWriter strWriter = new StringWriter();
								StreamResult result = new StreamResult(strWriter);
								transformer.transform(source, result);
									
								theMail.setXHTML(strWriter.toString());
							}
						
							//next body part
							bodyPart = bodyPart.getNextSibling();
						}
						
					}
					else if(child.getLocalName().equals("attachment"))
					{
						Element attachment = (Element)child;
						MailAttachment ma = new MailAttachment(attachment.getAttribute("filename"), attachment.getAttribute("mimetype"), attachment.getFirstChild().getNodeValue());
						theMail.addAttachment(ma);
					}
				}
				
				//next node
				child = child.getNextSibling();
				
			}
		}
		
		//Return the mail object
		return(theMail);
	}
	
	/**
	 * Returns the current date and time in an RFC822 format, suitable for an email Date Header
	 * 
	 * @return		RFC822 formated date and time as a String
	 */
	private String getDateRFC822()
	{
		String dateString = new String();
		Calendar rightNow = Calendar.getInstance();
		
		//Day of the week
		switch(rightNow.get(Calendar.DAY_OF_WEEK))
		{
			case Calendar.MONDAY:
			{
				dateString = "Mon";
				break;
			}
			case Calendar.TUESDAY:
			{
				dateString = "Tue";
				break;
			}
			case Calendar.WEDNESDAY:
			{
				dateString = "Wed";
				break;
			}
			case Calendar.THURSDAY:
			{
				dateString = "Thu";
				break;
			}
			case Calendar.FRIDAY:
			{
				dateString = "Fri";
				break;
			}
			case Calendar.SATURDAY:
			{
				dateString = "Sat";
				break;
			}
			case Calendar.SUNDAY:
			{
				dateString = "Sun";
				break;
			}
		}
		dateString += ", ";
		
		//Date 
		dateString += rightNow.get(Calendar.DAY_OF_MONTH);
		dateString += " ";
		
		//Month
		switch(rightNow.get(Calendar.MONTH))
		{
			case Calendar.JANUARY:
			{
				dateString += "Jan";
				break;
			}
			case Calendar.FEBRUARY:
			{
				dateString += "Feb";
				break;
			}
			case Calendar.MARCH:
			{
				dateString += "Mar";
				break;
			}
			case Calendar.APRIL:
			{
				dateString += "Apr";
				break;
			}
			case Calendar.MAY:
			{
				dateString += "May";
				break;
			}
			case Calendar.JUNE:
			{
				dateString += "Jun";
				break;
			}
			case Calendar.JULY:
			{
				dateString += "Jul";
				break;
			}
			case Calendar.AUGUST:
			{
				dateString += "Aug";
				break;
			}
			case Calendar.SEPTEMBER:
			{
				dateString += "Sep";
				break;
			}
			case Calendar.OCTOBER:
			{
				dateString += "Oct";
				break;
			}
			case Calendar.NOVEMBER:
			{
				dateString += "Nov";
				break;
			}
			case Calendar.DECEMBER:
			{
				dateString += "Dec";
				break;
			}
		}
		dateString += " ";
		
		//Year
		dateString += rightNow.get(Calendar.YEAR);
		dateString += " ";
		
		//Time
		String tHour = Integer.toString(rightNow.get(Calendar.HOUR_OF_DAY));
		if(tHour.length() == 1)
		{
			tHour = "0" + tHour;
		}
		String tMinute = Integer.toString(rightNow.get(Calendar.MINUTE));
		if(tMinute.length() == 1)
		{
			tMinute = "0" + tMinute;
		}
		String tSecond = Integer.toString(rightNow.get(Calendar.SECOND));
		if(tSecond.length() == 1)
		{
			tSecond = "0" + tSecond;
		}
		
		dateString += tHour + ":" + tMinute + ":" + tSecond + " ";
		
		//TimeZone Correction
		String tzSign = new String();
		String tzHours = new String();
		String tzMinutes = new String();
		
		TimeZone thisTZ = TimeZone.getDefault();
		int TZOffset = thisTZ.getOffset(rightNow.get(Calendar.DATE)); //get timezone offset in milliseconds
	    TZOffset = (TZOffset / 1000); //convert to seconds
	    TZOffset = (TZOffset / 60); //convert to minutes
	    
	    //Sign
	    if(TZOffset > 1)
	    {
	    	tzSign = "+";
	    }
	    else
	    {
	    	tzSign = "-";
	    }
	    
	    //Calc Hours and Minutes?
	    if(TZOffset >= 60 || TZOffset <= -60)
	    {
	    	//Minutes and Hours
	    	tzHours += (TZOffset / 60); //hours
	    	if(tzHours.length() == 1)  // do we need to prepend a 0
	    	{
	    		tzHours = "0" + tzHours;
	    	}
	    	
	    	tzMinutes += (TZOffset % 60); //minutes
	    	if(tzMinutes.length() == 1)  // do we need to prepend a 0
	    	{
	    		tzMinutes = "0" + tzMinutes;
	    	}
	    }
	    else
	    {
	    	//Just Minutes
	    	tzHours = "00";
	    	tzMinutes += TZOffset;
	    	if(tzMinutes.length() == 1)  // do we need to prepend a 0
	    	{
	    		tzMinutes = "0" + tzMinutes;
	    	}
	    }
	    
		dateString += tzSign + tzHours + tzMinutes;
		
		return(dateString);
	}
	
	/**
	 * Base64 Encodes a string (used for message subject)
	 * 
	 * @param str	The String to encode
	 * @return		The encoded String
	 */
	private String encode64 (String str) throws java.io.UnsupportedEncodingException
	{
		Base64Encoder enc = new Base64Encoder();
		enc.translate(str.getBytes(charset));
		String result = new String(enc.getCharArray());	
		
        result = result.replaceAll("\n","?=\n =?" + charset + "?B?");
        result = "=?" + charset + "?B?" + result + "?=";
        return(result);
	}

	/**
	 * Base64 Encodes an email address
	 * 
	 * @param str	The email address as a String to encode
	 * @return		The encoded email address String
	 */
	private String encode64Address (String str) throws java.io.UnsupportedEncodingException
	{
        String result;
        int idx = str.indexOf("<");
        
        if(idx != -1)
        {
            result = encode64(str.substring(0,idx)) + " " + str.substring(idx);
        }
        else
        {
            result = str;
        }
        return(result);
	}

	/**
	 * A simple data class to represent an email
	 * attachment. Just has private
	 * members and some get methods.
	 * 
	 * @version 1.2
	 */
	private class MailAttachment
	{
		private String filename;
		private String mimeType;
		private String data;
		
		public MailAttachment(String filename, String mimeType, String data)
		{
			this.filename = filename;
			this.mimeType = mimeType;
			this.data = data;
		}

		public String getData() {
			return data;
		}

		public String getFilename() {
			return filename;
		}

		public String getMimeType() {
			return mimeType;
		}
	}
	
	/**
	 * A simple data class to represent an email
	 * doesnt do anything fancy just has private
	 * members and get and set methods
	 * 
	 * @version 1.2
	 */
	private class Mail
	{
		private String from = "";					//Who is the mail from
		private String replyTo = null;				//Who should you reply to 
		private Vector to = new Vector();			//Who is the mail going to
		private Vector cc = new Vector();			//Carbon Copy to
		private Vector bcc = new Vector();			//Blind Carbon Copy to
		private String subject = "";				//Subject of the mail
		private String text = "";					//Body text of the mail
		private String xhtml = "";					//Body XHTML of the mail
		private Vector attachment = new Vector();	//Any attachments
		
		//From
		public void setFrom(String from)
		{
			this.from = from;
		}
		
		public String getFrom()
		{
			return(this.from);
		}
		
		//reply-to
		public void setReplyTo(String replyTo)
		{
			this.replyTo = replyTo;
		}
		
		public String getReplyTo()
		{
			return replyTo;
		}
		
		//To
		public void addTo(String to)
		{
			this.to.addElement(to);
		}
		
		public int countTo()
		{
			return(to.size());
		}
		
		public String getTo(int index)
		{
			return((String)to.elementAt(index));
		}
		
		public Collection getTo()
		{
			return(to);
		}
		
		//CC
		public void addCC(String cc)
		{
			this.cc.addElement(cc);
		}
		
		public int countCC()
		{
			return(cc.size());
		}
		
		public String getCC(int index)
		{
			return((String)cc.elementAt(index));
		}
		
		public Collection getCC()
		{
			return(cc);
		}
		
		//BCC
		public void addBCC(String bcc)
		{
			this.bcc.addElement(bcc);
		}
		
		public int countBCC()
		{
			return(bcc.size());
		}
		
		public String getBCC(int index)
		{
			return((String)bcc.elementAt(index));
		}
		
		public Collection getBCC()
		{
			return(bcc);
		}
		
		//Subject
		public void setSubject(String subject)
		{
			this.subject = subject;
		}
		
		public String getSubject()
		{
			return(subject);
		}
		
		//text
		public void setText(String text)
		{
			this.text = text;
		}
		
		public String getText()
		{
			return(text);
		}		
		
		//xhtml
		public void setXHTML(String xhtml)
		{
			this.xhtml = xhtml;
		}
		
		public String getXHTML()
		{
			return(xhtml);
		}
		
		public void addAttachment(MailAttachment ma)
		{
			attachment.add(ma);
		}
		
		public Iterator attachmentIterator()
		{
			return attachment.iterator();
		}
	}
}
