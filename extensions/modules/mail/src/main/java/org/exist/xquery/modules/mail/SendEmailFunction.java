/*
 *  eXist Mail Module Extension SendEmailFunction
 *  Copyright (C) 2006-09 Adam Retter <adam@exist-db.org>
 *  www.exist-db.org
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
 *  $Id$
 */

package org.exist.xquery.modules.mail;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.Base64Encoder;
import org.exist.util.MimeTable;
import org.exist.xquery.*;
import org.exist.xquery.functions.system.GetVersion;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

//send-email specific imports

/**
 * eXist Mail Module Extension SendEmailFunction
 * 
 * The email sending functionality of the eXist Mail Module Extension that
 * allows email to be sent from XQuery using either SMTP or Sendmail.  
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:robert.walpole@devon.gov.uk">Robert Walpole</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:josemariafg@gmail.com">José María Fernández</a>
 * @serial 2011-08-02
 * @version 1.6
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */

/* TODO according to RFC 821, SMTP commands must end with <CR><LF>,
Java uses platform native end-of-line characters for .println(...) functions and so
this function may have issues on non-Windows platforms
*/

public class SendEmailFunction extends BasicFunction
{
    
    protected static final Logger logger = LogManager.getLogger(SendEmailFunction.class);

    private final int MIME_BASE64_MAX_LINE_LENGTH = 76; //RFC 2045, page 24
    
    private String charset;
	
    public final static FunctionSignature deprecated = new FunctionSignature(
        new QName("send-email", MailModule.NAMESPACE_URI, MailModule.PREFIX),
        "Sends an email through the SMTP Server.",
        new SequenceType[]
        {
            new FunctionParameterSequenceType("email", Type.ELEMENT, Cardinality.ONE_OR_MORE, "The email message in the following format: <mail> <from/> <reply-to/> <to/> <cc/> <bcc/> <subject/> <message> <text/> <xhtml/> </message> <attachment filename=\"\" mimetype=\"\">xs:base64Binary</attachment> </mail>."),
            new FunctionParameterSequenceType("server", Type.STRING, Cardinality.ZERO_OR_ONE, "The SMTP server.  If empty, then it tries to use the local sendmail program."),
            new FunctionParameterSequenceType("charset", Type.STRING, Cardinality.ZERO_OR_ONE, "The charset value used in the \"Content-Type\" message header (Defaults to UTF-8)")
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE_OR_MORE, "true if the email message was successfully sent")
    );

    public final static FunctionSignature signatures[] = {
    	new FunctionSignature(
            new QName("send-email", MailModule.NAMESPACE_URI, MailModule.PREFIX),
            "Sends an email using javax.mail messaging libraries.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "mail-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The JavaMail session handle retrieved from mail:get-mail-session()" ),
                new FunctionParameterSequenceType( "email", Type.ELEMENT, Cardinality.ONE_OR_MORE, "The email message in the following format: <mail> <from/> <reply-to/> <to/> <cc/> <bcc/> <subject/> <message> <text/> <xhtml/> </message> <attachment filename=\"\" mimetype=\"\">xs:base64Binary</attachment> </mail>.")
            },
            new SequenceType( Type.ITEM, Cardinality.EMPTY )
        )
    };

    public SendEmailFunction(XQueryContext context, FunctionSignature signature)
    {
            super( context, signature );
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
    	if(args.length==3) {
    		return deprecatedSendEmail(args, contextSequence);
    	} else {
    		return sendEmail(args, contextSequence);
    	}
    }
    
	public Sequence sendEmail(Sequence[] args, Sequence contextSequence)
		throws XPathException
	{
		// was a session handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException( this, "Session handle not specified" ) );
		}

		// get the Session
		long sessionHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Session session = MailModule.retrieveSession( context, sessionHandle );
		if( session == null ) {
			throw( new XPathException( this, "Invalid Session handle specified" ) );
		}
		
		try {
            List<Message> messages = parseInputEmails(session,args[1]);
	    		String proto = session.getProperty("mail.transport.protocol");
			if(proto==null)
				proto = "smtp";
			Transport t = session.getTransport(proto);
			try {
				if(session.getProperty("mail."+proto+".auth")!=null)
					t.connect(session.getProperty("mail."+proto+".user"),session.getProperty("mail."+proto+".password"));
				for(Message msg: messages) {
					t.sendMessage(msg,msg.getAllRecipients());
				}
			} finally {
				t.close();
			}
			
			return( Sequence.EMPTY_SEQUENCE );
		} catch(TransformerException te) {
			throw new XPathException(this, "Could not Transform XHTML Message Body: " + te.getMessage(), te);
        } catch(MessagingException smtpe) {
        	throw new XPathException(this, "Could not send message(s): " + smtpe.getMessage(), smtpe);
        } catch(IOException ioe) {
        	throw new XPathException(this, "Attachment in some message could not be prepared: " + ioe.getMessage(), ioe);
        } catch(Throwable t) {
		throw new XPathException(this, "Unexpected error from JavaMail layer (Is your message well structured?): " + t.getMessage(), t);
	}
	}
    
    public Sequence deprecatedSendEmail(Sequence[] args, Sequence contextSequence) throws XPathException
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
        	List<Element> mailElements = new ArrayList<>();
            if(args[0].getItemCount() > 1 && args[0] instanceof ValueSequence) {
            	for(int i = 0; i < args[0].getItemCount(); i++) {
                	mailElements.add((Element)args[0].itemAt(i));
                }
            } else {
            	mailElements.add((Element)args[0].itemAt(0));
            }
            
            List<Mail> mails =  parseMailElement(mailElements);

            ValueSequence results = new ValueSequence();

            //Send email with Sendmail or SMTP?
            if(!args[1].isEmpty())
            {
                //SMTP
                List<Boolean> mailResults = sendBySMTP(mails, args[1].getStringValue());
                
                for(Boolean mailResult : mailResults)
                {
                    results.add(BooleanValue.valueOf(mailResult));
                }
            }
            else
            {
                for(Mail mail : mails)
                {
                   boolean result = sendBySendmail(mail);

                   results.add(BooleanValue.valueOf(result));
                }
            }
            
            return results;
        }
        catch(TransformerException te)
        {
            throw new XPathException(this, "Could not Transform XHTML Message Body: " + te.getMessage(), te);
        }
        catch(SMTPException smtpe)
        {
            throw new XPathException(this, "Could not send message(s)" + smtpe.getMessage(), smtpe);
        }
    }
	
    private List<Message> parseInputEmails(Session session, Sequence arg)
    	throws IOException, MessagingException, TransformerException
    {
    	//Parse the XML <mail> into a mail Object
    	List<Element> mailElements = new ArrayList<>();
        if(arg.getItemCount() > 1 && arg instanceof ValueSequence) {
        	for(int i = 0; i < arg.getItemCount(); i++) {
            	mailElements.add((Element)arg.itemAt(i));
            }
        } else {
        	mailElements.add((Element)arg.itemAt(0));
        }
        
        return parseMessageElement(session, mailElements);
    }
    
    /**
     * Sends an email using the Operating Systems sendmail application
     *
     * @param mail representation of the email to send
     * @return boolean value of true of false indicating success or failure to send email
     */
    private boolean sendBySendmail(Mail mail)
    {
        PrintWriter out = null;

        try
        {
            //Create a list of all Recipients, should include to, cc and bcc recipient
            List<String> allrecipients = new ArrayList<>();

            allrecipients.addAll(mail.getTo());
            allrecipients.addAll(mail.getCC());
            allrecipients.addAll(mail.getBCC());

            //Get a string of all recipients email addresses
            final StringBuilder recipients = new StringBuilder();

            for (String recipient : allrecipients) {
                recipients.append(" ");

                //Check format of to address does it include a name as well as the email address?
                if (recipient.contains("<")) {
                    //yes, just add the email address
                    recipients.append(recipient.substring(recipient.indexOf("<") + 1, recipient.indexOf(">")));
                } else {
                    //add the email address
                    recipients.append(recipient);
                }
            }

            //Create a sendmail Process
            Process p = Runtime.getRuntime().exec("/usr/sbin/sendmail" + recipients.toString());

            //Get a Buffered Print Writer to the Processes stdOut
            out = new PrintWriter(new OutputStreamWriter(p.getOutputStream(),charset));

            //Send the Message
            writeMessage(out, mail);
        }
        catch(IOException e)
        {
            LOG.error(e.getMessage(), e);
            
            return false;
        }
        finally
        {
            //Close the stdOut
            if(out != null)
                out.close();
        }

        //Message Sent Succesfully
        LOG.info("send-email() message sent using Sendmail " + new Date());

        return true;
    }

    private static class SMTPException extends Exception
    {
		private static final long serialVersionUID = 4859093648476395159L;

		public SMTPException(String message)
        {
            super(message);
        }

        public SMTPException(Throwable cause)
        {
            super(cause);
        }
    }

    /**
     * Sends an email using SMTP
     *
     * @param mails A list of mail object representing the email to send
     * @param SMTPServer The SMTP Server to send the email through
     * @return boolean value of true of false indicating success or failure to send email
     *
     * @throws SMTPException if an I/O error occurs
     */
    private List<Boolean> sendBySMTP(List<Mail> mails, String SMTPServer) throws SMTPException
    {
        final int TCP_PROTOCOL_SMTP = 25;   //SMTP Protocol
        String smtpResult = "";             //Holds the server Result code when an SMTP Command is executed

        List<Boolean> sendMailResults = new ArrayList<>();



        try (//Create a Socket and connect to the SMTP Server
             Socket smtpSock = new Socket(SMTPServer, TCP_PROTOCOL_SMTP);

             //Create a Buffered Reader for the Socket
             InputStream smtpSockInputStream = smtpSock.getInputStream();
             InputStreamReader inputStreamReader = new InputStreamReader(smtpSockInputStream);
             BufferedReader smtpIn = new BufferedReader(inputStreamReader);

             //Create an Output Writer for the Socket
             OutputStream smtpSockOutputStream = smtpSock.getOutputStream();
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(smtpSockOutputStream, charset);
             PrintWriter smtpOut = new PrintWriter(outputStreamWriter)) {

            //First line sent to us from the SMTP server should be "220 blah blah", 220 indicates okay
            smtpResult = smtpIn.readLine();
            if(!smtpResult.substring(0, 3).equals("220"))
            {
                String errMsg = "Error - SMTP Server not ready: '" + smtpResult + "'";
                LOG.error(errMsg);
                throw new SMTPException(errMsg);
            }

            //Say "HELO"
            smtpOut.println("HELO " + InetAddress.getLocalHost().getHostName());
            smtpOut.flush();

            //get "HELLO" response, should be "250 blah blah"
            smtpResult = smtpIn.readLine();
            if(smtpResult == null)
            {
                String errMsg = "Error - Unexpected null response to SMTP HELO";
                LOG.error(errMsg);
                throw new SMTPException(errMsg);
            }

            if(!smtpResult.substring(0, 3).equals("250"))
            {
                String errMsg = "Error - SMTP HELO Failed: '" + smtpResult + "'";
                LOG.error(errMsg);
                throw new SMTPException(errMsg);
            }

            //write SMTP message(s)
            for(Mail mail : mails)
            {
                boolean mailResult = writeSMTPMessage(mail, smtpOut, smtpIn);

                sendMailResults.add(mailResult);
            }

        } catch(IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            throw new SMTPException(ioe);
        }

        //Message(s) Sent Succesfully
        LOG.info("send-email() message(s) sent using SMTP " + new Date());

        return sendMailResults;
    }

    private boolean writeSMTPMessage(Mail mail, PrintWriter smtpOut, BufferedReader smtpIn)
    {
        try
        {
            String smtpResult = "";

            //Send "MAIL FROM:"
            //Check format of from address does it include a name as well as the email address?
            if(mail.getFrom().contains("<"))
            {
                //yes, just send the email address
                smtpOut.println("MAIL FROM:<" + mail.getFrom().substring(mail.getFrom().indexOf("<") + 1, mail.getFrom().indexOf(">")) + ">");
            }
            else
            {
                //no, doesnt include a name so send the email address
                smtpOut.println("MAIL FROM:<" + mail.getFrom() + ">");
            }
            smtpOut.flush();

            //Get "MAIL FROM:" response
            smtpResult = smtpIn.readLine();
            if(smtpResult == null)
            {
                LOG.error("Error - Unexpected null response to SMTP MAIL FROM");
                return false;
            }
            if(!smtpResult.substring(0, 3).equals("250"))
            {
                LOG.error("Error - SMTP MAIL FROM failed: " + smtpResult);
                return false;
            }

            //RCPT TO should be issued for each to, cc and bcc recipient
            List<String> allrecipients = new ArrayList<>();
            allrecipients.addAll(mail.getTo());
            allrecipients.addAll(mail.getCC());
            allrecipients.addAll(mail.getBCC());

            for (String recipient : allrecipients) {
                //Send "RCPT TO:"
                //Check format of to address does it include a name as well as the email address?
                if (recipient.contains("<")) {
                    //yes, just send the email address
                    smtpOut.println("RCPT TO:<" + recipient.substring(recipient.indexOf("<") + 1, recipient.indexOf(">")) + ">");
                } else {
                    smtpOut.println("RCPT TO:<" + recipient + ">");
                }
                smtpOut.flush();
                //Get "RCPT TO:" response
                smtpResult = smtpIn.readLine();
                if(!smtpResult.substring(0, 3).equals("250"))
                {
                    LOG.error("Error - SMTP RCPT TO failed: " + smtpResult);
                }
            }


            //SEND "DATA"
            smtpOut.println("DATA");
            smtpOut.flush();

            //Get "DATA" response, should be "354 blah blah"
            smtpResult = smtpIn.readLine();
            if(!smtpResult.substring(0, 3).equals("354"))
            {
                LOG.error("Error - SMTP DATA failed: " + smtpResult);
                return false;
            }

            //Send the Message
            writeMessage(smtpOut, mail);

            //Get end message response, should be "250 blah blah"
            smtpResult = smtpIn.readLine();
            if(!smtpResult.substring(0, 3).equals("250"))
            {
                LOG.error("Error - Message not accepted: " + smtpResult);
                return false;
            }
        }
        catch(IOException ioe)
        {
            LOG.error(ioe.getMessage(), ioe);
            return false;
        }

        return true;
    }
	
    /**
     * Writes an email payload (Headers + Body) from a mail object
     *
     * @param out A PrintWriter to receive the email
     * @param aMail A mail object representing the email to write out
     *
     * @throws IOException if an I/O error occurs
     */
    private void writeMessage(PrintWriter out, Mail aMail) throws IOException
    {
        String Version = eXistVersion();				//Version of eXist
        String MultipartBoundary = "eXist.multipart." + Version;	//Multipart Boundary

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
        out.println("X-Mailer: eXist " + Version + " mail:send-email()");
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
        if(!aMail.getText().equals("") && !aMail.getXHTML().equals("") && aMail.attachmentIterator().hasNext())
        {
            out.println("Content-Type: multipart/alternative; boundary=\"" + MultipartBoundary + "_alt\";");
            out.println("--" + MultipartBoundary + "_alt");
        }

        //text email
        if(!aMail.getText().equals(""))
        {
            out.println("Content-Type: text/plain; charset=" + charset);
            out.println("Content-Transfer-Encoding: 8bit");

            //now send the txt message
            out.println();
            out.println(aMail.getText());

            if(multipartBoundary != null)
            {
                if(!aMail.getXHTML().equals("") || aMail.attachmentIterator().hasNext())
                {
                    if(!aMail.getText().equals("") && !aMail.getXHTML().equals("") && aMail.attachmentIterator().hasNext())
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
                    if(!aMail.getText().equals("") && !aMail.getXHTML().equals("") && aMail.attachmentIterator().hasNext())
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
        if(!aMail.getXHTML().equals(""))
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
                        if(!aMail.getText().equals("") && !aMail.getXHTML().equals("") && aMail.attachmentIterator().hasNext())
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
                        if(!aMail.getText().equals("") && !aMail.getXHTML().equals("") && aMail.attachmentIterator().hasNext())
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
            for(Iterator<MailAttachment> itAttachment = aMail.attachmentIterator(); itAttachment.hasNext(); )
            {
                MailAttachment ma = itAttachment.next();

                out.println("Content-Type: " + ma.getMimeType() + "; name=\"" + ma.getFilename() + "\"");
                out.println("Content-Transfer-Encoding: base64");
                out.println("Content-Description: " + ma.getFilename());
                out.println("Content-Disposition: attachment; filename=\"" + ma.getFilename() + "\"");
                out.println();
                
                
                //write out the attachment encoded data in fixed width lines
                final char buf[] = new char[MIME_BASE64_MAX_LINE_LENGTH];
                int read = -1;
                final Reader attachmentDataReader = new StringReader(ma.getData());
                while((read = attachmentDataReader.read(buf, 0, MIME_BASE64_MAX_LINE_LENGTH)) > -1) {
                    out.println(String.valueOf(buf, 0, read));
                }

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
        out.flush();
    }


    /**
     * Get's the version of eXist we are running
     * The eXist version is used as part of the multipart separator
     *
     * @return The eXist Version
     *
     * @throws IOException if an I/O error occurs
     */
    private String eXistVersion() throws IOException
    {
        Properties sysProperties = new Properties();
        sysProperties.load(GetVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
        return sysProperties.getProperty("product-version", "unknown version");
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
     * @param mailElements	The XML mail Node
     * @return		A mail Object representing the XML mail Node
     *
     * @throws TransformerException if a transformation error occurs
     */
    private List<Mail> parseMailElement(List<Element> mailElements) throws TransformerException
    {
        List<Mail> mails = new ArrayList<>();

        for(Element mailElement : mailElements)
        {
            //Make sure that message has a Mail node
            if(mailElement.getLocalName().equals("mail"))
            {
                //New mail Object
                Mail mail = new Mail();

                //Get the First Child
                Node child = mailElement.getFirstChild();
                while(child != null)
                {
                    //Parse each of the child nodes
                    if(child.getNodeType() == Node.ELEMENT_NODE && child.hasChildNodes())
                    {
                        switch (child.getLocalName()) {
                            case "from":
                                mail.setFrom(child.getFirstChild().getNodeValue());
                                break;
                            case "reply-to":
                                mail.setReplyTo(child.getFirstChild().getNodeValue());
                                break;
                            case "to":
                                mail.addTo(child.getFirstChild().getNodeValue());
                                break;
                            case "cc":
                                mail.addCC(child.getFirstChild().getNodeValue());
                                break;
                            case "bcc":
                                mail.addBCC(child.getFirstChild().getNodeValue());
                                break;
                            case "subject":
                                mail.setSubject(child.getFirstChild().getNodeValue());
                                break;
                            case "message":
                                //If the message node, then parse the child text and xhtml nodes
                                Node bodyPart = child.getFirstChild();
                                while(bodyPart != null)
                                {
                                    if(bodyPart.getLocalName().equals("text"))
                                    {
                                        mail.setText(bodyPart.getFirstChild().getNodeValue());
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
                                        
                                        mail.setXHTML(strWriter.toString());
                                    }
                                    
                                    //next body part
                                    bodyPart = bodyPart.getNextSibling();
                                }   break;
                            case "attachment":
                                Element attachment = (Element)child;
                                MailAttachment ma = new MailAttachment(attachment.getAttribute("filename"), attachment.getAttribute("mimetype"), attachment.getFirstChild().getNodeValue());
                                mail.addAttachment(ma);
                                break;
                        }
                    }

                    //next node
                    child = child.getNextSibling();

                }
                mails.add(mail);
            }
        }

        return mails;
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
     * 		<text charset="" encoding=""></text>
     * 		<xhtml charset="" encoding=""></xhtml>
     * 		<generic charset="" type="" encoding=""></generic>
     * 	</message>
     * 	<attachment mimetype="" filename=""></attachment>
     * </mail>
     *
     * @param mailElements	The XML mail Node
     * @return		A mail Object representing the XML mail Node
     *
     * @throws IOException if an I/O error occurs
     * @throws MessagingException if an email error occurs
     * @throws TransformerException if a transformation error occurs
     */
    private List<Message> parseMessageElement(Session session, List<Element> mailElements)
            throws IOException, MessagingException, TransformerException {
        List<Message> mails = new ArrayList<>();

        for (Element mailElement : mailElements) {
            //Make sure that message has a Mail node
            if (mailElement.getLocalName().equals("mail")) {
                //New message Object
                // create a message
                MimeMessage msg = new MimeMessage(session);

                ArrayList<InternetAddress> replyTo = new ArrayList<>();
                boolean fromWasSet = false;
                MimeBodyPart body = null;
                Multipart multibody = null;
                ArrayList<MimeBodyPart> attachments = new ArrayList<>();
                String firstContent = null;
                String firstContentType = null;
                String firstCharset = null;
                String firstEncoding = null;

                //Get the First Child
                Node child = mailElement.getFirstChild();
                while (child != null) {
                    //Parse each of the child nodes
                    if (child.getNodeType() == Node.ELEMENT_NODE && child.hasChildNodes()) {
                        switch (child.getLocalName()) {
                            case "from":
                                // set the from and to address
                                InternetAddress[] addressFrom = {new InternetAddress(child.getFirstChild().getNodeValue())};
                                msg.addFrom(addressFrom);
                                fromWasSet = true;
                                break;
                            case "reply-to":
                                // As we can only set the reply-to, not add them, let's keep
                                // all of them in a list
                                replyTo.add(new InternetAddress(child.getFirstChild().getNodeValue()));
                                msg.setReplyTo(replyTo.toArray(new InternetAddress[0]));
                                break;
                            case "to":
                                msg.addRecipient(Message.RecipientType.TO, new InternetAddress(child.getFirstChild().getNodeValue()));
                                break;
                            case "cc":
                                msg.addRecipient(Message.RecipientType.CC, new InternetAddress(child.getFirstChild().getNodeValue()));
                                break;
                            case "bcc":
                                msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(child.getFirstChild().getNodeValue()));
                                break;
                            case "subject":
                                msg.setSubject(child.getFirstChild().getNodeValue());
                                break;
                            case "header":
                                // Optional : You can also set your custom headers in the Email if you Want
                                msg.addHeader(((Element) child).getAttribute("name"), child.getFirstChild().getNodeValue());
                                break;
                            case "message":
                                //If the message node, then parse the child text and xhtml nodes
                                Node bodyPart = child.getFirstChild();
                                while (bodyPart != null) {
                                    if (bodyPart.getNodeType() != Node.ELEMENT_NODE)
                                        continue;
                                    
                                    Element elementBodyPart = (Element) bodyPart;
                                    String content = null;
                                    String contentType = null;
                                    
                                    if (bodyPart.getLocalName().equals("text")) {
                                        // Setting the Subject and Content Type
                                        content = bodyPart.getFirstChild().getNodeValue();
                                        contentType = "plain";
                                    } else if (bodyPart.getLocalName().equals("xhtml")) {
                                        //Convert everything inside <xhtml></xhtml> to text
                                        TransformerFactory transFactory = TransformerFactory.newInstance();
                                        Transformer transformer = transFactory.newTransformer();
                                        DOMSource source = new DOMSource(bodyPart.getFirstChild());
                                        StringWriter strWriter = new StringWriter();
                                        StreamResult result = new StreamResult(strWriter);
                                        transformer.transform(source, result);
                                        
                                        content = strWriter.toString();
                                        contentType = "html";
                                    } else if (bodyPart.getLocalName().equals("generic")) {
                                        // Setting the Subject and Content Type
                                        content = elementBodyPart.getFirstChild().getNodeValue();
                                        contentType = elementBodyPart.getAttribute("type");
                                    }
                                    
                                    // Now, time to store it
                                    if (content != null && contentType != null && contentType.length() > 0) {
                                        String charset = elementBodyPart.getAttribute("charset");
                                        String encoding = elementBodyPart.getAttribute("encoding");
                                        
                                        if (body != null && multibody == null) {
                                            multibody = new MimeMultipart("alternative");
                                            multibody.addBodyPart(body);
                                        }
                                        
                                        if (StringUtils.isEmpty(charset)) {
                                            charset = "UTF-8";
                                        }
                                        
                                        if (StringUtils.isEmpty(encoding)) {
                                            encoding = "quoted-printable";
                                        }
                                        
                                        if (body == null) {
                                            firstContent = content;
                                            firstCharset = charset;
                                            firstContentType = contentType;
                                            firstEncoding = encoding;
                                        }
                                        body = new MimeBodyPart();
                                        body.setText(content, charset, contentType);
                                        if (encoding != null) {
                                            body.setHeader("Content-Transfer-Encoding", encoding);
                                        }
                                        if (multibody != null)
                                            multibody.addBodyPart(body);
                                    }
                                    
                                    //next body part
                                    bodyPart = bodyPart.getNextSibling();
                                }   break;
                            case "attachment":
                                Element attachment = (Element) child;
                                MimeBodyPart part;
                                // if mimetype indicates a binary resource, assume the content is base64 encoded
                                if (MimeTable.getInstance().isTextContent(attachment.getAttribute("mimetype"))) {
                                    part = new MimeBodyPart();
                                } else {
                                    part = new PreencodedMimeBodyPart("base64");
                                }   StringBuilder content = new StringBuilder();
                            Node attachChild = attachment.getFirstChild();
                                while (attachChild != null) {
                                    if (attachChild.getNodeType() == Node.ELEMENT_NODE) {
                                        TransformerFactory transFactory = TransformerFactory.newInstance();
                                        Transformer transformer = transFactory.newTransformer();
                                        DOMSource source = new DOMSource(attachChild);
                                        StringWriter strWriter = new StringWriter();
                                        StreamResult result = new StreamResult(strWriter);
                                        transformer.transform(source, result);
                                        
                                        content.append(strWriter.toString());
                                    } else {
                                        content.append(attachChild.getNodeValue());
                                    }
                                    attachChild = attachChild.getNextSibling();
                                }   part.setDataHandler(new DataHandler(new ByteArrayDataSource(content.toString(), attachment.getAttribute("mimetype"))));
                            part.setFileName(attachment.getAttribute("filename"));
//                            part.setHeader("Content-Transfer-Encoding", "base64");
                                attachments.add(part);
                                break;
                        }
                    }

                    //next node
                    child = child.getNextSibling();

                }
                // Lost from
                if (!fromWasSet)
                    msg.setFrom();

                // Preparing content and attachments
                if (attachments.size() > 0) {
                    if (multibody == null) {
                        multibody = new MimeMultipart("mixed");
                        if (body != null) {
                            multibody.addBodyPart(body);
                        }
                    } else {
                        MimeMultipart container = new MimeMultipart("mixed");
                        MimeBodyPart containerBody = new MimeBodyPart();
                        containerBody.setContent(multibody);
                        container.addBodyPart(containerBody);
                        multibody = container;
                    }
                    for (MimeBodyPart part : attachments) {
                        multibody.addBodyPart(part);
                    }
                }

                // And now setting-up content
                if (multibody != null) {
                    msg.setContent(multibody);
                } else if (body != null) {
                    msg.setText(firstContent, firstCharset, firstContentType);
                    if (firstEncoding != null) {
                        msg.setHeader("Content-Transfer-Encoding", firstEncoding);
                    }
                }

                msg.saveChanges();
                mails.add(msg);
            }
        }

        return mails;
    }

    /**
     * Returns the current date and time in an RFC822 format, suitable for an email Date Header
     *
     * @return		RFC822 formated date and time as a String
     */
    private String getDateRFC822() {
        
        String dateString = "";
        final Calendar rightNow = Calendar.getInstance();

        //Day of the week
        switch(rightNow.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                dateString = "Mon";
                break;
            
            case Calendar.TUESDAY:
                dateString = "Tue";
                break;
            
            case Calendar.WEDNESDAY:
                dateString = "Wed";
                break;
            
            case Calendar.THURSDAY:
                dateString = "Thu";
                break;
            
            case Calendar.FRIDAY:
                dateString = "Fri";
                break;

            case Calendar.SATURDAY:
                dateString = "Sat";
                break;
            
            case Calendar.SUNDAY:
                dateString = "Sun";
                break;   
        }
        
        dateString += ", ";

        //Date
        dateString += rightNow.get(Calendar.DAY_OF_MONTH);
        dateString += " ";

        //Month
        switch(rightNow.get(Calendar.MONTH)) {
            case Calendar.JANUARY:
                dateString += "Jan";
                break;
            
            case Calendar.FEBRUARY:
                dateString += "Feb";
                break;
            
            case Calendar.MARCH:
                dateString += "Mar";
                break;

            case Calendar.APRIL:
                dateString += "Apr";
                break;
            
            case Calendar.MAY:
                dateString += "May";
                break;
            
            case Calendar.JUNE:
                dateString += "Jun";
                break;
            
            case Calendar.JULY:
                dateString += "Jul";
                break;
            
            case Calendar.AUGUST:
                dateString += "Aug";
                break;
            
            case Calendar.SEPTEMBER:
                dateString += "Sep";
                break;
            
            case Calendar.OCTOBER:
                dateString += "Oct";
                break;
            
            case Calendar.NOVEMBER:
                dateString += "Nov";
                break;
            
            case Calendar.DECEMBER:
                dateString += "Dec";
                break;
        }
        dateString += " ";

        //Year
        dateString += rightNow.get(Calendar.YEAR);
        dateString += " ";

        //Time
        String tHour = Integer.toString(rightNow.get(Calendar.HOUR_OF_DAY));
        if(tHour.length() == 1) {
                tHour = "0" + tHour;
        }
        
        String tMinute = Integer.toString(rightNow.get(Calendar.MINUTE));
        if(tMinute.length() == 1) {
                tMinute = "0" + tMinute;
        }
        
        String tSecond = Integer.toString(rightNow.get(Calendar.SECOND));
        if(tSecond.length() == 1) {
                tSecond = "0" + tSecond;
        }

        dateString += tHour + ":" + tMinute + ":" + tSecond + " ";

        //TimeZone Correction
        final String tzSign;
        String tzHours = "";
        String tzMinutes = "";

        final TimeZone thisTZ = rightNow.getTimeZone();
        int tzOffset = thisTZ.getOffset(rightNow.getTime().getTime()); //get timezone offset in milliseconds
        tzOffset = (tzOffset / 1000); //convert to seconds
        tzOffset = (tzOffset / 60); //convert to minutes
        
        //Sign
        if(tzOffset > 1) {
            tzSign = "+";
        } else {
            tzSign = "-";
            tzOffset *= -1;
        }

        //Calc Hours and Minutes?
        if(tzOffset >= 60) {
            //Minutes and Hours
            tzHours += (tzOffset / 60); //hours
            
            // do we need to prepend a 0
            if(tzHours.length() == 1) {  
                tzHours = "0" + tzHours;
            }

            tzMinutes += (tzOffset % 60); //minutes
            
            // do we need to prepend a 0
            if(tzMinutes.length() == 1) {
                tzMinutes = "0" + tzMinutes;
            }
        } else {
            //Just Minutes
            tzHours = "00";
            tzMinutes += tzOffset;
            // do we need to prepend a 0
            if(tzMinutes.length() == 1) {
                tzMinutes = "0" + tzMinutes;
            }
        }

        dateString += tzSign + tzHours + tzMinutes;

        return dateString;
    }
	
    /**
     * Base64 Encodes a string (used for message subject)
     *
     * @param str	The String to encode
     * @return		The encoded String
     *
     * @throws java.io.UnsupportedEncodingException if the encocding is unsupported
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
     *
     * @throws java.io.UnsupportedEncodingException if the encocding is unsupported
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
    private static class MailAttachment
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
    private static class Mail
    {
        private String from = "";				//Who is the mail from
        private String replyTo = null;                          //Who should you reply to
        private final List<String> to = new ArrayList<>();      //Who is the mail going to
        private final List<String> cc = new ArrayList<>();	//Carbon Copy to
        private final List<String> bcc = new ArrayList<>();	//Blind Carbon Copy to
        private String subject = "";				//Subject of the mail
        private String text = "";				//Body text of the mail
        private String xhtml = "";                              //Body XHTML of the mail
        private final List<MailAttachment> attachment = new ArrayList<>();	//Any attachments

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
            this.to.add(to);
        }

        public int countTo()
        {
            return(to.size());
        }

        public String getTo(int index)
        {
            return to.get(index);
        }

        public List<String> getTo()
        {
            return(to);
        }

        //CC
        public void addCC(String cc)
        {
            this.cc.add(cc);
        }

        public int countCC()
        {
            return(cc.size());
        }

        public String getCC(int index)
        {
            return cc.get(index);
        }

        public List<String> getCC()
        {
                return(cc);
        }

        //BCC
        public void addBCC(String bcc)
        {
            this.bcc.add(bcc);
        }

        public int countBCC()
        {
            return bcc.size();
        }

        public String getBCC(int index)
        {
            return bcc.get(index);
        }

        public List<String> getBCC()
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
            return subject;
        }

        //text
        public void setText(String text)
        {
            this.text = text;
        }

        public String getText()
        {
            return text;
        }

        //xhtml
        public void setXHTML(String xhtml)
        {
            this.xhtml = xhtml;
        }

        public String getXHTML()
        {
            return xhtml;
        }

        public void addAttachment(MailAttachment ma)
        {
                attachment.add(ma);
        }

        public Iterator<MailAttachment> attachmentIterator()
        {
                return attachment.iterator();
        }
    }
}
