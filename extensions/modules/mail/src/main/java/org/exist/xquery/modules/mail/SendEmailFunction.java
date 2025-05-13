/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.mail;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Version;
import org.exist.dom.QName;
import org.exist.util.MimeTable;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;

import javax.annotation.Nullable;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.regex.Pattern;

/**
 * eXist-db Mail Module Extension SendEmailFunction.
 *
 * The email sending functionality of the eXist-db Mail Module Extension that
 * allows email to be sent from XQuery using either SMTP or Sendmail.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:robert.walpole@devon.gov.uk">Robert Walpole</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:josemariafg@gmail.com">José María Fernández</a>
 */
public class SendEmailFunction extends BasicFunction {

    private static final Logger LOGGER = LogManager.getLogger(SendEmailFunction.class);
    private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();

    private final static int MIME_BASE64_MAX_LINE_LENGTH = 76; //RFC 2045, page 24

    /**
     * Regular expression for checking for an RFC 2045 non-token.
     */
    private static final Pattern NON_TOKEN_PATTERN = Pattern.compile("^.*[\\s\\p{Cntrl}()<>@,;:\\\"/\\[\\]?=].*$");

    static final String ERROR_MSG_NON_MIME_CLIENT = "Error your mail client is not MIME Compatible";

    private static final Random RANDOM = new Random();

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

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("send-email", MailModule.NAMESPACE_URI, MailModule.PREFIX),
                    "Sends an email using javax.mail messaging libraries.",
                    new SequenceType[]
                            {
                                    new FunctionParameterSequenceType("mail-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The JavaMail session handle retrieved from mail:get-mail-session()"),
                                    new FunctionParameterSequenceType("email", Type.ELEMENT, Cardinality.ONE_OR_MORE, "The email message in the following format: <mail> <from/> <reply-to/> <to/> <cc/> <bcc/> <subject/> <message> <text/> <xhtml/> </message> <attachment filename=\"\" mimetype=\"\">xs:base64Binary</attachment> </mail>.")
                            },
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            )
    };

    public SendEmailFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args.length == 3) {
            return deprecatedSendEmail(args, contextSequence);
        } else {
            return sendEmail(args, contextSequence);
        }
    }

    public Sequence sendEmail(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        // was a session handle specified?
        if (args[0].isEmpty()) {
            throw new XPathException(this, "Session handle not specified");
        }

        // get the Session
        final long sessionHandle = ((IntegerValue) args[0].itemAt(0)).getLong();
        final Session session = MailModule.retrieveSession(context, sessionHandle);
        if (session == null) {
            throw new XPathException(this, "Invalid Session handle specified");
        }

        try {
            final Message[] messages = parseInputEmails(session, args[1]);
            String proto = session.getProperty("mail.transport.protocol");
            if (proto == null) {
                proto = "smtp";
            }
            try (final Transport t = session.getTransport(proto)) {
                if (session.getProperty("mail." + proto + ".auth") != null) {
                    t.connect(session.getProperty("mail." + proto + ".user"), session.getProperty("mail." + proto + ".password"));
                } else {
                    t.connect();
                }
                for (final Message msg : messages) {
                    t.sendMessage(msg, msg.getAllRecipients());
                }
            }

            return Sequence.EMPTY_SEQUENCE;
        } catch (final TransformerException e) {
            throw new XPathException(this, "Could not Transform XHTML Message Body: " + e.getMessage(), e);
        } catch (final MessagingException e) {
            throw new XPathException(this, "Could not send message(s): " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException(this, "Attachment in some message could not be prepared: " + e.getMessage(), e);
        } catch (final Throwable t) {
            throw new XPathException(this, "Unexpected error from JavaMail layer (Is your message well structured?): " + t.getMessage(), t);
        }
    }

    public Sequence deprecatedSendEmail(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        try {
            //get the charset parameter, default to UTF-8
            final String charset;
            if (!args[2].isEmpty()) {
                charset = args[2].getStringValue();
            } else {
                charset = "UTF-8";
            }

            // Parse the XML <mail> Elements into mail Objects
            final int len = args[0].getItemCount();
            final Element[] mailElements = new Element[len];
            for (int i = 0; i < len; i++) {
                mailElements[i] = (Element) args[0].itemAt(i);
            }
            final Mail[] mails = parseMailElement(mailElements);

            final ValueSequence results = new ValueSequence();

            //Send email with Sendmail or SMTP?
            if (!args[1].isEmpty()) {
                //SMTP
                final boolean[] mailResults = sendBySMTP(mails, args[1].getStringValue(), charset);

                for (final boolean mailResult : mailResults) {
                    results.add(BooleanValue.valueOf(mailResult));
                }
            } else {
                for (final Mail mail : mails) {
                    final boolean result = sendBySendmail(mail, charset);
                    results.add(BooleanValue.valueOf(result));
                }
            }

            return results;
        } catch (final TransformerException | IOException e) {
            throw new XPathException(this, "Could not Transform XHTML Message Body: " + e.getMessage(), e);
        } catch (final SMTPException e) {
            throw new XPathException(this, "Could not send message(s)" + e.getMessage(), e);
        }
    }

    private Message[] parseInputEmails(final Session session, final Sequence arg) throws IOException, MessagingException, TransformerException {
        // Parse the XML <mail> Elements into mail Objects
        final int len = arg.getItemCount();
        final Element[] mailElements = new Element[len];
        for (int i = 0; i < len; i++) {
            mailElements[i] = (Element) arg.itemAt(i);
        }
        return parseMessageElement(session, mailElements);
    }

    /**
     * Sends an email using the Operating Systems sendmail application
     *
     * @param mail representation of the email to send
     * @param charset the character set
     * @return boolean value of true of false indicating success or failure to send email
     */
    private boolean sendBySendmail(final Mail mail, final String charset) {

        //Create a list of all Recipients, should include to, cc and bcc recipient
        final List<String> allrecipients = new ArrayList<>();
        allrecipients.addAll(mail.getTo());
        allrecipients.addAll(mail.getCC());
        allrecipients.addAll(mail.getBCC());

        //Get a string of all recipients email addresses
        final StringBuilder recipients = new StringBuilder();

        for (final String recipient : allrecipients) {
            recipients.append(" ");

            //Check format of to address does it include a name as well as the email address?
            if (recipient.contains("<")) {
                //yes, just add the email address
                recipients.append(recipient, recipient.indexOf("<") + 1, recipient.indexOf(">"));
            } else {
                //add the email address
                recipients.append(recipient);
            }
        }

        try {
            //Create a sendmail Process
            final Process p = Runtime.getRuntime().exec("/usr/sbin/sendmail" + recipients);

            //Get a Buffered Print Writer to the Processes stdOut
            try (final PrintWriter out = new PrintWriter(new OutputStreamWriter(p.getOutputStream(), charset))) {
                //Send the Message
                writeMessage(out, mail, false, charset);
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }

        // Message Sent Succesfully
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("send-email() message sent using Sendmail {}", new Date());
        }

        return true;
    }

    private static class SMTPException extends Exception {
        private static final long serialVersionUID = 4859093648476395159L;

        public SMTPException(final String message) {
            super(message);
        }

        public SMTPException(final Throwable cause) {
            super(cause);
        }
    }

    /**
     * Sends an email using SMTP
     *
     * @param mails         A list of mail object representing the email to send
     * @param smtpServerArg The SMTP Server to send the email through
     * @param charset the character set
     * @return boolean value of true of false indicating success or failure to send email
     * @throws SMTPException if an I/O error occurs
     */
    private boolean[] sendBySMTP(final Mail[] mails, final String smtpServerArg, final String charset) throws SMTPException {
        String smtpHost = "localhost";
        int smtpPort = 25;

        if (smtpServerArg != null && !smtpServerArg.isEmpty()) {
            final int idx = smtpServerArg.indexOf(':');
            if (idx > -1) {
                smtpHost = smtpServerArg.substring(0, idx);
                smtpPort = Integer.parseInt(smtpServerArg.substring(idx + 1));
            } else {
                smtpHost = smtpServerArg;
            }
        }

        String smtpResult;             //Holds the server Result code when an SMTP Command is executed

        final boolean[] sendMailResults = new boolean[mails.length];

        try (
                //Create a Socket and connect to the SMTP Server
                final Socket smtpSock = new Socket(smtpHost, smtpPort);

                //Create a Buffered Reader for the Socket
                final BufferedReader smtpIn = new BufferedReader(new InputStreamReader(smtpSock.getInputStream()));

                //Create an Output Writer for the Socket
                final PrintWriter smtpOut = new PrintWriter(new OutputStreamWriter(smtpSock.getOutputStream(), charset))) {

            //First line sent to us from the SMTP server should be "220 blah blah", 220 indicates okay
            smtpResult = smtpIn.readLine();
            if (!smtpResult.startsWith("220")) {
                final String errMsg = "Error - SMTP Server not ready: '" + smtpResult + "'";
                LOGGER.error(errMsg);
                throw new SMTPException(errMsg);
            }

            //Say "HELO"
            smtpOut.print("HELO " + InetAddress.getLocalHost().getHostName() + "\r\n");
            smtpOut.flush();

            //get "HELLO" response, should be "250 blah blah"
            smtpResult = smtpIn.readLine();
            if (smtpResult == null) {
                final String errMsg = "Error - Unexpected null response to SMTP HELO";
                LOGGER.error(errMsg);
                throw new SMTPException(errMsg);
            }

            if (!smtpResult.startsWith("250")) {
                final String errMsg = "Error - SMTP HELO Failed: '" + smtpResult + "'";
                LOGGER.error(errMsg);
                throw new SMTPException(errMsg);
            }

            //write SMTP message(s)
            for (int i = 0; i < mails.length; i++) {
                final boolean mailResult = writeSMTPMessage(mails[i], smtpOut, smtpIn, charset);
                sendMailResults[i] = mailResult;
            }

            //all done, time to "QUIT"
            smtpOut.print("QUIT\r\n");
            smtpOut.flush();

        } catch (final IOException ioe) {
            LOGGER.error(ioe.getMessage(), ioe);
            throw new SMTPException(ioe);
        }

        //Message(s) Sent Succesfully
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("send-email() message(s) sent using SMTP {}", new Date());
        }

        return sendMailResults;
    }

    private boolean writeSMTPMessage(final Mail mail, final PrintWriter smtpOut, final BufferedReader smtpIn, final String charset) {
        try {
            String smtpResult;

            //Send "MAIL FROM:"
            //Check format of from address does it include a name as well as the email address?
            if (mail.getFrom().contains("<")) {
                //yes, just send the email address
                smtpOut.print("MAIL FROM:<" + mail.getFrom().substring(mail.getFrom().indexOf("<") + 1, mail.getFrom().indexOf(">")) + ">\r\n");
            } else {
                //no, doesnt include a name so send the email address
                smtpOut.print("MAIL FROM:<" + mail.getFrom() + ">\r\n");
            }
            smtpOut.flush();

            //Get "MAIL FROM:" response
            smtpResult = smtpIn.readLine();
            if (smtpResult == null) {
                LOGGER.error("Error - Unexpected null response to SMTP MAIL FROM");
                return false;
            }
            if (!smtpResult.startsWith("250")) {
                LOGGER.error("Error - SMTP MAIL FROM failed: {}", smtpResult);
                return false;
            }

            //RCPT TO should be issued for each to, cc and bcc recipient
            final List<String> allrecipients = new ArrayList<>();
            allrecipients.addAll(mail.getTo());
            allrecipients.addAll(mail.getCC());
            allrecipients.addAll(mail.getBCC());

            for (final String recipient : allrecipients) {
                //Send "RCPT TO:"
                //Check format of to address does it include a name as well as the email address?
                if (recipient.contains("<")) {
                    //yes, just send the email address
                    smtpOut.print("RCPT TO:<" + recipient.substring(recipient.indexOf("<") + 1, recipient.indexOf(">")) + ">\r\n");
                } else {
                    smtpOut.print("RCPT TO:<" + recipient + ">\r\n");
                }
                smtpOut.flush();
                //Get "RCPT TO:" response
                smtpResult = smtpIn.readLine();
                if (!smtpResult.startsWith("250")) {
                    LOGGER.error("Error - SMTP RCPT TO failed: {}", smtpResult);
                }
            }

            //SEND "DATA"
            smtpOut.print("DATA\r\n");
            smtpOut.flush();

            //Get "DATA" response, should be "354 blah blah" (optionally preceded by "250 OK")
            smtpResult = smtpIn.readLine();
            if (smtpResult.startsWith("250")) {
                // should then be followed by "354 blah blah"
                smtpResult = smtpIn.readLine();
            }

            if (!smtpResult.startsWith("354")) {
                LOGGER.error("Error - SMTP DATA failed: {}", smtpResult);
                return false;
            }

            //Send the Message
            writeMessage(smtpOut, mail, true, charset);

            //Get end message response, should be "250 blah blah"
            smtpResult = smtpIn.readLine();
            if (!smtpResult.startsWith("250")) {
                LOGGER.error("Error - Message not accepted: {}", smtpResult);
                return false;
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }

        return true;
    }

    /**
     * Writes an email payload (Headers + Body) from a mail object.
     *
     * Access is package-private for unit testing purposes.
     *
     * @param out   A PrintWriter to receive the email
     * @param aMail A mail object representing the email to write out
     * @param useCrLf true to use CRLF for line ending, false to use LF
     * @param charset the character set
     * @throws IOException if an I/O error occurs
     */
    static void writeMessage(final PrintWriter out, final Mail aMail, final boolean useCrLf, final String charset) throws IOException {
        final String eol = useCrLf ? "\r\n" : "\n";

        //write the message headers

        out.print("From: " + encode64Address(aMail.getFrom(), charset) + eol);

        if (aMail.getReplyTo() != null) {
            out.print("Reply-To: " + encode64Address(aMail.getReplyTo(), charset) + eol);
        }

        for (int x = 0; x < aMail.countTo(); x++) {
            out.print("To: " + encode64Address(aMail.getTo(x), charset) + eol);
        }

        for (int x = 0; x < aMail.countCC(); x++) {
            out.print("CC: " + encode64Address(aMail.getCC(x), charset) + eol);
        }

        for (int x = 0; x < aMail.countBCC(); x++) {
            out.print("BCC: " + encode64Address(aMail.getBCC(x), charset) + eol);
        }

        out.print("Date: " + getDateRFC822() + eol);
        String subject = aMail.getSubject();
        if (subject == null) {
            subject = "";
        }
        out.print("Subject: " + encode64(subject, charset) + eol);
        out.print("X-Mailer: eXist-db " + Version.getVersion() + " mail:send-email()" + eol);
        out.print("MIME-Version: 1.0" + eol);


        boolean multipartAlternative = false;
        int multipartInstanceCount = 0;
        final Deque<String> multipartBoundary = new ArrayDeque<>();

        if (aMail.attachmentIterator().hasNext()) {
            // we have an attachment as well as text and/or html, so we need a multipart/mixed message
            multipartBoundary.addFirst(multipartBoundary(++multipartInstanceCount));
        } else if (nonEmpty(aMail.getText()) && nonEmpty(aMail.getXHTML())) {
            // we have text and html, so we need a multipart/alternative message and no attachment
            multipartAlternative = true;
            multipartBoundary.addFirst(multipartBoundary(++multipartInstanceCount));
        }
//        else {
//            // we have either text or html and no attachment this message is not multipart
//        }

        //content type
        if (!multipartBoundary.isEmpty()) {
            //multipart message

            out.print("Content-Type: " + (multipartAlternative ? "multipart/alternative" : "multipart/mixed") + "; boundary=" + parameterValue(multipartBoundary.peekFirst()) + eol);

            //Mime warning
            out.print(eol);
            out.print(ERROR_MSG_NON_MIME_CLIENT + eol);
            out.print(eol);

            out.print("--" + multipartBoundary.peekFirst() + eol);
        }

        if (nonEmpty(aMail.getText()) && nonEmpty(aMail.getXHTML()) && aMail.attachmentIterator().hasNext()) {
            // we are a multipart inside a multipart
            multipartBoundary.addFirst(multipartBoundary(++multipartInstanceCount));

            out.print("Content-Type: multipart/alternative; boundary=" + parameterValue(multipartBoundary.peekFirst()) + eol);
            out.print(eol);
            out.print("--" + multipartBoundary.peekFirst() + eol);
        }

        //text email
        if (nonEmpty(aMail.getText())) {
            out.print("Content-Type: text/plain; charset=" + charset + eol);
            out.print("Content-Transfer-Encoding: 8bit" + eol);

            //now send the txt message
            out.print(eol);
            out.print(aMail.getText() + eol);

            if (!multipartBoundary.isEmpty()) {
                if (nonEmpty(aMail.getXHTML()) || aMail.attachmentIterator().hasNext()) {
                    out.print("--" + multipartBoundary.peekFirst() + eol);
                } else {
                    // End multipart message
                    out.print("--" + multipartBoundary.peekFirst() + "--" + eol);
                    multipartBoundary.removeFirst();
                }
            }
        }

        //HTML email
        if (nonEmpty(aMail.getXHTML())) {
            out.print("Content-Type: text/html; charset=" + charset + eol);
            out.print("Content-Transfer-Encoding: 8bit" + eol);

            //now send the html message
            out.print(eol);
            out.print("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">" + eol);
            out.print(aMail.getXHTML() + eol);

            if (!multipartBoundary.isEmpty()) {
                if (aMail.attachmentIterator().hasNext()) {
                    if (nonEmpty(aMail.getText()) && nonEmpty(aMail.getXHTML()) && aMail.attachmentIterator().hasNext()) {
                        // End multipart message
                        out.print("--" + multipartBoundary.peekFirst() + "--" + eol);
                        multipartBoundary.removeFirst();
                    }

                    out.print("--" + multipartBoundary.peekFirst() + eol);

                } else {
                    // End multipart message
                    out.print("--" + multipartBoundary.peekFirst() + "--" + eol);
                    multipartBoundary.removeFirst();
                }
            }
        }

        //attachments
        if (aMail.attachmentIterator().hasNext()) {
            for (final Iterator<MailAttachment> itAttachment = aMail.attachmentIterator(); itAttachment.hasNext(); ) {
                final MailAttachment ma = itAttachment.next();

                out.print("Content-Type: " + ma.mimeType() + "; name=" + parameterValue(ma.filename()) + eol);
                out.print("Content-Transfer-Encoding: base64" + eol);
                out.print("Content-Description: " + ma.filename() + eol);
                out.print("Content-Disposition: attachment; filename=" + parameterValue(ma.filename()) + eol);
                out.print(eol);


                //write out the attachment encoded data in fixed width lines
                final char[] buf = new char[MIME_BASE64_MAX_LINE_LENGTH];
                int read = -1;
                final Reader attachmentDataReader = new StringReader(ma.data());
                while ((read = attachmentDataReader.read(buf, 0, MIME_BASE64_MAX_LINE_LENGTH)) > -1) {
                    out.print(String.valueOf(buf, 0, read) + eol);
                }

                if (itAttachment.hasNext()) {
                    out.print("--" + multipartBoundary.peekFirst() + eol);
                }
            }

            // End multipart message
            out.print("--" + multipartBoundary.peekFirst() + "--" + eol);
            multipartBoundary.removeFirst();
        }

        //end the message, <cr><lf>.<cr><lf>
        out.print(eol);
        out.print("." + eol);
        out.flush();
    }

    /**
     * Constructs a mail Object from an XML representation of an email
     * <p>
     * The XML email Representation is expected to look something like this
     *
     * <mail>
     * <from></from>
     * <reply-to></reply-to>
     * <to></to>
     * <cc></cc>
     * <bcc></bcc>
     * <subject></subject>
     * <message>
     * <text></text>
     * <xhtml></xhtml>
     * </message>
     * </mail>
     *
     * @param mailElements The XML mail Node
     * @throws TransformerException if a transformation error occurs
     * @return A mail Object representing the XML mail Node
     */
    private Mail[] parseMailElement(final Element[] mailElements) throws TransformerException, IOException {
        Mail[] mails = new Mail[mailElements.length];

        int i = 0;
        for (final Element mailElement : mailElements) {

            //Make sure that message has a Mail node
            if ("mail".equals(mailElement.getLocalName())) {
                final Mail mail = new Mail();

                //Get the First Child
                Node child = mailElement.getFirstChild();
                while (child != null) {
                    //Parse each of the child nodes
                    if (Node.ELEMENT_NODE == child.getNodeType() && child.hasChildNodes()) {
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
                                while (bodyPart != null) {
                                    if ("text".equals(bodyPart.getLocalName())) {
                                        mail.setText(bodyPart.getFirstChild().getNodeValue());
                                    } else if ("xhtml".equals(bodyPart.getLocalName())) {
                                        //Convert everything inside <xhtml></xhtml> to text
                                        final Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
                                        final DOMSource source = new DOMSource(bodyPart.getFirstChild());
                                        try (final StringWriter strWriter = new StringWriter()) {
                                            final StreamResult result = new StreamResult(strWriter);
                                            transformer.transform(source, result);
                                            mail.setXHTML(strWriter.toString());
                                        }
                                    }

                                    //next body part
                                    bodyPart = bodyPart.getNextSibling();
                                }
                                break;
                            case "attachment":
                                final Element attachment = (Element) child;
                                final MailAttachment ma = new MailAttachment(attachment.getAttribute("filename"), attachment.getAttribute("mimetype"), attachment.getFirstChild().getNodeValue());
                                mail.addAttachment(ma);
                                break;
                        }
                    }

                    //next node
                    child = child.getNextSibling();

                }
                mails[i++] = mail;
            }
        }

        if (i != mailElements.length) {
            mails = Arrays.copyOf(mails, i);
        }

        return mails;
    }

    /**
     * Constructs a mail Object from an XML representation of an email
     * <p>
     * The XML email Representation is expected to look something like this
     *
     * <mail>
     * <from></from>
     * <reply-to></reply-to>
     * <to></to>
     * <cc></cc>
     * <bcc></bcc>
     * <subject></subject>
     * <message>
     * <text charset="" encoding=""></text>
     * <xhtml charset="" encoding=""></xhtml>
     * <generic charset="" type="" encoding=""></generic>
     * </message>
     * <attachment mimetype="" filename=""></attachment>
     * </mail>
     *
     * @param mailElements The XML mail Node
     * @throws IOException          if an I/O error occurs
     * @throws MessagingException   if an email error occurs
     * @throws TransformerException if a transformation error occurs
     * @return A mail Object representing the XML mail Node
     */
    private Message[] parseMessageElement(final Session session, final Element[] mailElements) throws IOException, MessagingException, TransformerException {

        Message[] mails = new Message[mailElements.length];

        int i = 0;
        for (final Element mailElement : mailElements) {
            //Make sure that message has a Mail node
            if ("mail".equals(mailElement.getLocalName())) {
                //New message Object
                // create a message
                final MimeMessage msg = new MimeMessage(session);

                boolean fromWasSet = false;
                final List<InternetAddress> replyTo = new ArrayList<>();
                MimeBodyPart body = null;
                Multipart multibody = null;
                final List<MimeBodyPart> attachments = new ArrayList<>();
                String firstContent = null;
                String firstContentType = null;
                String firstCharset = null;
                String firstEncoding = null;

                //Get the First Child
                Node child = mailElement.getFirstChild();
                while (child != null) {
                    //Parse each of the child nodes
                    if (Node.ELEMENT_NODE == child.getNodeType() && child.hasChildNodes()) {
                        switch (child.getLocalName()) {
                            case "from":
                                // set the from and to address
                                final InternetAddress[] addressFrom = {
                                        new InternetAddress(child.getFirstChild().getNodeValue())
                                };
                                msg.addFrom(addressFrom);
                                fromWasSet = true;
                                break;
                            case "reply-to":
                                // As we can only set the reply-to, not add them, let's keep
                                // all of them in a list
                                replyTo.add(new InternetAddress(child.getFirstChild().getNodeValue()));
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
                                    if (Node.ELEMENT_NODE != bodyPart.getNodeType()) {
                                        continue;
                                    }

                                    final Element elementBodyPart = (Element) bodyPart;
                                    String content = null;
                                    String contentType = null;

                                    switch (bodyPart.getLocalName()) {
                                        case "text":
                                            // Setting the Subject and Content Type
                                            content = bodyPart.getFirstChild().getNodeValue();
                                            contentType = "plain";
                                            break;
                                        case "xhtml":
                                            //Convert everything inside <xhtml></xhtml> to text
                                            final Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
                                            final DOMSource source = new DOMSource(bodyPart.getFirstChild());
                                            try (final StringWriter strWriter = new StringWriter()) {
                                                final StreamResult result = new StreamResult(strWriter);
                                                transformer.transform(source, result);
                                                content = strWriter.toString();
                                            }
                                            contentType = "html";
                                            break;
                                        case "generic":
                                            // Setting the Subject and Content Type
                                            content = elementBodyPart.getFirstChild().getNodeValue();
                                            contentType = elementBodyPart.getAttribute("type");
                                            break;
                                    }

                                    // Now, time to store it
                                    if (content != null && contentType != null && !contentType.isEmpty()) {
                                        String charset = elementBodyPart.getAttribute("charset");
                                        if (charset.isEmpty()) {
                                            charset = "UTF-8";
                                        }

                                        String encoding = elementBodyPart.getAttribute("encoding");
                                        if (encoding.isEmpty()) {
                                            encoding = "quoted-printable";
                                        }

                                        if (body != null && multibody == null) {
                                            multibody = new MimeMultipart("alternative");
                                            multibody.addBodyPart(body);
                                        }

                                        if (body == null) {
                                            firstContent = content;
                                            firstCharset = charset;
                                            firstContentType = contentType;
                                            firstEncoding = encoding;
                                        }
                                        body = new MimeBodyPart();
                                        body.setText(content, charset, contentType);
                                        body.setHeader("Content-Transfer-Encoding", encoding);
                                        if (multibody != null) {
                                            multibody.addBodyPart(body);
                                        }
                                    }

                                    //next body part
                                    bodyPart = bodyPart.getNextSibling();
                                }
                                break;
                            case "attachment":
                                final Element attachment = (Element) child;
                                final MimeBodyPart part;
                                // if mimetype indicates a binary resource, assume the content is base64 encoded
                                if (MimeTable.getInstance().isTextContent(attachment.getAttribute("mimetype"))) {
                                    part = new MimeBodyPart();
                                } else {
                                    part = new PreencodedMimeBodyPart("base64");
                                }
                                final StringBuilder content = new StringBuilder();
                                Node attachChild = attachment.getFirstChild();
                                while (attachChild != null) {
                                    if (Node.ELEMENT_NODE == attachChild.getNodeType()) {
                                        final Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
                                        final DOMSource source = new DOMSource(attachChild);
                                        try (final StringWriter strWriter = new StringWriter()) {
                                            final StreamResult result = new StreamResult(strWriter);
                                            transformer.transform(source, result);
                                            content.append(strWriter);
                                        }
                                    } else {
                                        content.append(attachChild.getNodeValue());
                                    }
                                    attachChild = attachChild.getNextSibling();
                                }
                                part.setDataHandler(new DataHandler(new ByteArrayDataSource(content.toString(), attachment.getAttribute("mimetype"))));
                                part.setFileName(attachment.getAttribute("filename"));
                                // part.setHeader("Content-Transfer-Encoding", "base64");
                                attachments.add(part);
                                break;
                        }
                    }

                    //next node
                    child = child.getNextSibling();
                }

                // Lost from
                if (!fromWasSet) {
                    msg.setFrom();
                }

                msg.setReplyTo(replyTo.toArray(new InternetAddress[0]));

                // Preparing content and attachments
                if (!attachments.isEmpty()) {
                    if (multibody == null) {
                        multibody = new MimeMultipart("mixed");
                        if (body != null) {
                            multibody.addBodyPart(body);
                        }
                    } else {
                        final MimeMultipart container = new MimeMultipart("mixed");
                        final MimeBodyPart containerBody = new MimeBodyPart();
                        containerBody.setContent(multibody);
                        container.addBodyPart(containerBody);
                        multibody = container;
                    }
                    for (final MimeBodyPart part : attachments) {
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
                mails[i++] = msg;
            }
        }

        if (i != mailElements.length) {
            mails = Arrays.copyOf(mails, i);
        }

        return mails;
    }

    /**
     * Returns the current date and time in an RFC822 format, suitable for an email Date Header
     *
     * @return RFC822 formated date and time as a String
     */
    private static String getDateRFC822() {
        String dateString = "";
        final Calendar rightNow = Calendar.getInstance();

        //Day of the week
        dateString = switch (rightNow.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY -> "Mon";
            case Calendar.TUESDAY -> "Tue";
            case Calendar.WEDNESDAY -> "Wed";
            case Calendar.THURSDAY -> "Thu";
            case Calendar.FRIDAY -> "Fri";
            case Calendar.SATURDAY -> "Sat";
            case Calendar.SUNDAY -> "Sun";
            default -> dateString;
        };

        dateString += ", ";

        //Date
        dateString += rightNow.get(Calendar.DAY_OF_MONTH);
        dateString += " ";

        //Month
        switch (rightNow.get(Calendar.MONTH)) {
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
        if (tHour.length() == 1) {
            tHour = "0" + tHour;
        }

        String tMinute = Integer.toString(rightNow.get(Calendar.MINUTE));
        if (tMinute.length() == 1) {
            tMinute = "0" + tMinute;
        }

        String tSecond = Integer.toString(rightNow.get(Calendar.SECOND));
        if (tSecond.length() == 1) {
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
        if (tzOffset > 1) {
            tzSign = "+";
        } else {
            tzSign = "-";
            tzOffset *= -1;
        }

        //Calc Hours and Minutes?
        if (tzOffset >= 60) {
            //Minutes and Hours
            tzHours += (tzOffset / 60); //hours

            // do we need to prepend a 0
            if (tzHours.length() == 1) {
                tzHours = "0" + tzHours;
            }

            tzMinutes += (tzOffset % 60); //minutes

            // do we need to prepend a 0
            if (tzMinutes.length() == 1) {
                tzMinutes = "0" + tzMinutes;
            }
        } else {
            //Just Minutes
            tzHours = "00";
            tzMinutes += tzOffset;
            // do we need to prepend a 0
            if (tzMinutes.length() == 1) {
                tzMinutes = "0" + tzMinutes;
            }
        }

        dateString += tzSign + tzHours + tzMinutes;

        return dateString;
    }

    /**
     * Base64 Encodes a string (used for message subject).
     *
     * Access is package-private for unit testing purposes.
     *
     * @param str The String to encode
     * @throws java.io.UnsupportedEncodingException if the encocding is unsupported
     * @return The encoded String
     */
    static String encode64(final String str, final String charset) throws java.io.UnsupportedEncodingException {
        String result = Base64.encodeBase64String(str.getBytes(charset));
        result = result.replaceAll("\n", "?=\n =?" + charset + "?B?");
        result = "=?" + charset + "?B?" + result + "?=";
        return result;
    }

    /**
     * Base64 Encodes an email address
     *
     * @param str The email address as a String to encode
     * @param charset the character set
     * @throws java.io.UnsupportedEncodingException if the encocding is unsupported
     * @return The encoded email address String
     */
    private static String encode64Address(final String str, final String charset) throws java.io.UnsupportedEncodingException {
        final int idx = str.indexOf("<");

        final String result;
        if (idx != -1) {
            result = encode64(str.substring(0, idx), charset) + " " + str.substring(idx);
        } else {
            result = str;
        }

        return result;
    }

    /**
     * A simple data class to represent an email attachment.
     * <p>
     * It doesn't do anything fancy, it just has private
     * members and get and set methods.
     * <p>
     * Access is package-private for unit testing purposes.
     */
        record MailAttachment(String filename, String mimeType, String data) {
    }

    /**
     * A simple data class to represent an email.
     * It doesn't do anything fancy, it just has private
     * members and get and set methods.
     *
     * Access is package-private for unit testing purposes.
     */
    static class Mail {
        private String from;                                                //Who is the mail from
        private String replyTo;                                             //Who should you reply to
        private final List<String> to = new ArrayList<>(1);    //Who is the mail going to
        private List<String> cc;                                            //Carbon Copy to
        private List<String> bcc;                                           //Blind Carbon Copy to
        private String subject;                                             //Subject of the mail
        private String text;                                                //Body text of the mail
        private String xhtml;                                               //Body XHTML of the mail
        private List<MailAttachment> attachments;                            //Any attachments

        //From
        public void setFrom(final String from) {
            this.from = from;
        }

        public String getFrom() {
            return this.from;
        }

        //reply-to
        public void setReplyTo(final String replyTo) {
            this.replyTo = replyTo;
        }

        public String getReplyTo() {
            return replyTo;
        }

        //To
        public void addTo(final String to) {
            this.to.add(to);
        }

        public int countTo() {
            return to.size();
        }

        public String getTo(final int index) {
            return to.get(index);
        }

        public List<String> getTo() {
            return to;
        }

        //CC
        public void addCC(final String cc) {
            if (this.cc == null) {
                this.cc = new ArrayList<>();
            }
            this.cc.add(cc);
        }

        public int countCC() {
            if (this.cc == null) {
                return 0;
            }
            return cc.size();
        }

        public String getCC(final int index) {
            if (this.cc == null) {
                throw new IndexOutOfBoundsException();
            }
            return cc.get(index);
        }

        public List<String> getCC() {
            if (this.cc == null) {
                return Collections.EMPTY_LIST;
            }
            return cc;
        }

        //BCC
        public void addBCC(final String bcc) {
            if (this.bcc == null) {
                this.bcc = new ArrayList<>();
            }
            this.bcc.add(bcc);
        }

        public int countBCC() {
            if (this.bcc == null) {
                return 0;
            }
            return bcc.size();
        }

        public String getBCC(final int index) {
            if (this.bcc == null) {
                throw new IndexOutOfBoundsException();
            }
            return bcc.get(index);
        }

        public List<String> getBCC() {
            return Objects.requireNonNullElseGet(this.bcc, Collections::emptyList);
        }

        //Subject
        public void setSubject(final String subject) {
            this.subject = subject;
        }

        public String getSubject() {
            return subject;
        }

        //text
        public void setText(final String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        //xhtml
        public void setXHTML(final String xhtml) {
            this.xhtml = xhtml;
        }

        public String getXHTML() {
            return xhtml;
        }

        public void addAttachment(final MailAttachment ma) {
            if (this.attachments == null) {
                this.attachments = new ArrayList<>();
            }
            attachments.add(ma);
        }

        public Iterator<MailAttachment> attachmentIterator() {
            return (Iterator<MailAttachment>) Objects.requireNonNullElseGet(this.attachments, Collections::emptyList).iterator();
        }
    }

    private static boolean nonEmpty(@Nullable final String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * Creates a "quoted-string" of the parameter value
     * if it contains a non-token value (See {@link #isNonToken(String)}),
     * otherwise it returns the parameter value as is.
     *
     * Access is package-private for unit testing purposes.
     *
     * @param value parameter value.
     *
     * @return the quoted string parameter value, or the parameter value as is.
     */
    static String parameterValue(final String value) {
        if (isNonToken(value)) {
            return "\"" + value + "\"";
        } else {
            return value;
        }
    }

    /**
     * Determines if the string contains SPACE, CTLs, or `tspecial` (special token)
     * according to <a href="https://www.rfc-editor.org/rfc/rfc2045#section-5">RFC 2045 - Section 5</a>.
     *
     * @param str the string to test
     *
     * @return true if the string contains a non-token, false otherwise.
     */
    private static boolean isNonToken(final String str) {
        return NON_TOKEN_PATTERN.matcher(str).matches();
    }

    /**
     * Produce a multi-part boundary string.
     *
     * Access is package-private for unit testing purposes.
     *
     * @param multipartInstance the number of this multipart instance.
     *
     * @return the multi-part boundary string.
     */
    private static String multipartBoundary(final int multipartInstance) {
        return multipartBoundaryPrefix(multipartInstance) + "_" + nextRandomPositiveInteger() + "." + System.currentTimeMillis();
    }

    /**
     * Produce the prefix of a multi-part boundary string.
     *
     * Access is package-private for unit testing purposes.
     *
     * @param multipartInstance the number of this multipart instance.
     *
     * @return the multi-part boundary string prefix.
     */
    static String multipartBoundaryPrefix(final int multipartInstance) {
        return "----=_mail.mime.boundary_" + multipartInstance;
    }

    /**
     * Generates a positive random integer.
     *
     * @return the integer
     */
    private static int nextRandomPositiveInteger() {
        return RANDOM.nextInt() & Integer.MAX_VALUE;
    }
}
