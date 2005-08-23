/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.modules.mail;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.functions.util.ExistVersion;


//send-email specific imports
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
import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import org.w3c.dom.Node;


/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class SendEmail extends BasicFunction
{
	//TODO: Feature - Add an option to execute the function Asynchronously as Socket operations for SMTP can be slow (Sendmail seems fast enough). Will require placing the SMTP code in a thread.
	//TODO: Feature - Add a facility for the user to add their own message headers.
	//TODO: Feature - Add attachment support, will need base64 encoding etc...
	//TODO: Read the location of sendmail from the configuration file. Can vary from system to system
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("send-email", MailModule.NAMESPACE_URI, MailModule.PREFIX),
			"Sends an email $a through the SMTP Server $b, or if $b is () tries to use the local sendmail program. $a is the email in the following format <mail><from/><to/><cc/><bcc/><subject/><message><text/><xhtml/></message></mail>. $c defines the charset value used in the \"Content-Type\" message header (Defaults to UTF-8)",
			new SequenceType[]
			{
				new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public SendEmail(XQueryContext context)
	{
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		try
		{	
			//Parse the XML <mail> into a mail Object
			mail theMail = ParseMailXML( ((NodeValue)args[0].itemAt(0)).getNode() );
			
			//Send email with Sendmail or SMTP?
			if(args[1].getLength() > 0)
			{
				//SMTP
				if(SendSMTP(theMail, args[1].getStringValue(), args[2].getStringValue()))
				{
					return(BooleanValue.TRUE);
				}
			}
			else
			{
				//Sendmail
				if(SendSendmail(theMail, args[2].getStringValue()))
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
	
	
	//Sends an email using the system's sendmail binary
	private boolean SendSendmail(mail aMail, String ContentType_Charset)
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
			PrintWriter out = new PrintWriter(p.getOutputStream());
			
			//Send the Message
			WriteMessage(out, aMail, ContentType_Charset);
			
			//Close the stdOut
			out.close();
			
		}
		catch(IOException e)
		{
			return(false);
		}
		
		//Message Sent Succesfully
		LOG.info("send-email() message sent using Sendmail " + new Date());
		return(true);
	}
	
	//Sends an email using an SMTP Server
	private boolean SendSMTP(mail aMail, String SMTPServer, String ContentType_Charset)
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
			PrintWriter out = new PrintWriter(new OutputStreamWriter(smtpSock.getOutputStream()));
			
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
			WriteMessage(out, aMail, ContentType_Charset);
			
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
			return(false);
		}
		
		//Message Sent Succesfully
		LOG.info("send-email() message sent using SMTP " + new Date());
		return(true);
	}
	
	
	//Writes an email payload (Headers + Body) from a mail object
	private void WriteMessage(PrintWriter out, mail aMail, String ContentType_Charset) throws IOException
	{
			String Version = eXistVersion();									//Version of eXist
			String MultipartBoundary = "eXist.multipart." + Version;			//Multipart Boundary
			
			if(ContentType_Charset == "")										//set default charset if requied
				ContentType_Charset = "UTF-8";								
				
			//write the message headers
			out.println("From: " + encode(aMail.getFrom(), ContentType_Charset));
			for(int x = 0; x < aMail.countTo(); x++)
			{	
				out.println("To: " + encode(aMail.getTo(x), ContentType_Charset));
			}
			for(int x = 0; x < aMail.countCC(); x++)
			{	
				out.println("CC: " + encode(aMail.getCC(x), ContentType_Charset));
			}
			for(int x = 0; x < aMail.countBCC(); x++)
			{	
				out.println("BCC: " + encode(aMail.getBCC(x), ContentType_Charset));
			}
			out.println("Date: " + getDateRFC822());
			out.println("Subject: " + encode(aMail.getSubject(), ContentType_Charset));
			out.println("X-Mailer: eXist " + Version + " util:send-email()");
			out.println("MIME-Version: 1.0");
			
			//Is this a multipart message i.e. text and html?
			if((!aMail.getText().toString().equals("")) && (!aMail.getXHTML().toString().equals("")))
			{
				//Yes, start multipart message
				out.println("Content-Type: multipart/alternative; boundary=\"" + MultipartBoundary + "\";");
				
				//Mime warning
				out.println(encode("Error your mail client is not MIME Compatible", ContentType_Charset));
				
				//send the text part first 
				out.println("--" + MultipartBoundary);
				out.println("Content-Type: text/plain; charset=" + ContentType_Charset);
				out.println("Content-Transfer-Encoding: quoted-printable");
				out.println(aMail.getText());
				
				//send the html part next
				out.println("--" + MultipartBoundary);
				out.println("Content-Type: text/html; charset=" + ContentType_Charset);
				out.println("Content-Transfer-Encoding: quoted-printable");
				out.println(encode("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">", ContentType_Charset));
				out.println(encode(aMail.getXHTML(), ContentType_Charset));
				
				//Emd multipart message
				out.println("--" + MultipartBoundary + "--");
				
			}
			else
			{
				//No, is it a text email
				if(!aMail.getText().toString().equals(""))
				{
					//Yes, text email
					out.println("Content-Type: text/plain; charset=" + ContentType_Charset);
					out.println("Content-Transfer-Encoding: quoted-printable");
					
					//now send the trxt message
					out.println();
					out.println(encode(aMail.getText(), ContentType_Charset));
				}
				else
				{
					//No, its a HTML email
					out.println("Content-Type: text/html; charset=" + ContentType_Charset);
					out.println("Content-Transfer-Encoding: quoted-printable");
					
					//now send the html message
					out.println();
					out.println(encode("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">", ContentType_Charset));
					out.println(encode(aMail.getXHTML(), ContentType_Charset));
				}
			}
			
			//end the message, <cr><lf>.<cr><lf>
			out.println();
			out.println(".");
			out.println();
			out.flush();
	}
	
	
	//Gets the eXist Version Number
	private String eXistVersion() throws IOException
	{
		Properties sysProperties = new Properties();
		sysProperties.load(ExistVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		return((String)sysProperties.getProperty("product-version", "unknown version"));
	}
	
	//Constructs a mail object from an XML representation
	private mail ParseMailXML(Node message) throws TransformerException
	{
		//Expects message to be in the format -
		/*
		 * <mail>
		 * 	<from></from>
		 * 	<to></to>
		 * 	<cc></cc>
		 * 	<bcc></bcc>
		 *	<subject></subject>
		 *	<message>
		 *		<text></text>
		 *		<xhtml></xhtml>
		 *	</message>
		 * </mail>
		 * 
		 */
		
		
		//New mail Object
		mail theMail = new mail(); 
		
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
				}
				
				//next node
				child = child.getNextSibling();
				
			}
		}
		
		//Return the mail object
		return(theMail);
	}
	
	//Returns the current date and time in an RFC822 format, suitable for an email Date Header
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
		dateString += rightNow.get(Calendar.HOUR_OF_DAY) + ":" + rightNow.get(Calendar.MINUTE) + ":" + rightNow.get(Calendar.SECOND);
		dateString += " ";
		
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
	
	//encodes a string to the charset
	private String encode (String str, String ContentType_Charset)
	{
		try
		{
			return new String(str.getBytes(), ContentType_Charset);
		}
		catch(java.io.UnsupportedEncodingException e)
		{
			return str;
		}
	}
	
	//Class that Represents an email
	private class mail
	{
		private String from = "";			//Who is the mail from
		private Vector to = new Vector();	//Who is the mail going to
		private Vector cc = new Vector();	//Carbon Copy to
		private Vector bcc = new Vector();	//Blind Carbon Copy to
		private String subject = "";		//Subject of the mail
		private String text = "";			//Body text of the mail
		private String xhtml = "";			//Body XHTML of the mail
		
		//From
		public void setFrom(String from)
		{
			this.from = from;
		}
		
		public String getFrom()
		{
			return(this.from);
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
	}
	
	
}
