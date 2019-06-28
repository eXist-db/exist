package org.exist.xquery.modules.mail;

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Pattern;
import javax.activation.MimeType;
import javax.activation.MimeTypeParameterList;
import javax.activation.MimeTypeParseException;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

/**
 * Modified by alisterpillow on 19/08/2014.
 * Get a mail message
 *
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @serial 2009-03-12
 * @version 1.3
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class MessageFunctions extends BasicFunction {
    protected static final Logger logger = LogManager.getLogger(MessageListFunctions.class);
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final Pattern CONTENT_TYPE_RE = Pattern.compile(";\\s*boundary(.*)$"); // Remove unnecessary boundary= from content-type

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("get-messages", MailModule.NAMESPACE_URI, MailModule.PREFIX),
                    "Returns a sequence of emails as XML.  If there are no messages-numbers in the list, an empty sequence will be returned. Please see get_messages_example.xql.",
                    new SequenceType[]
                            {

                                    new FunctionParameterSequenceType("message-list-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The message list handle retrieved from mail:get-message-list() or mail:search-message-list()"),
                                    new FunctionParameterSequenceType("message-numbers", Type.INTEGER, Cardinality.ZERO_OR_MORE, "The messages to retrieve using the numbers from the message-list '//mail:message/@number' ")

                            },
                    new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_ONE, "the chosen messages as XML mail:messages/mail:message")
            )
    };

    public MessageFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * evaluate the call to the xquery get-message function,
     * it is really the main entry point of this class
     *
     * @param args            arguments from the get-message-list() function call
     * @param contextSequence the Context Sequence to operate on (not used here internally!)
     * @return A sequence representing the result of the get-message-list() function call
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (isCalledAs("get-messages")) {
            return getMessages(args, contextSequence);
        }
        throw (new XPathException(this, "Invalid function name"));
    }


    private Sequence getMessages(Sequence[] args, Sequence contextSequence) throws XPathException
    {

        Sequence ret = Sequence.EMPTY_SEQUENCE;

        // was a folder handle specified?
        if (args[0].isEmpty()) {
            throw (new XPathException(this, "Mail folder handle not specified"));
        }
        // get the Folder
        long folderHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
        Folder folder= MailModule.retrieveFolder( context, folderHandle );

        if( folder == null ) {
            throw( new XPathException(this, "Invalid Folder handle specified" ) );
        }

        if (args[1].isEmpty()) { // expecting a sequence of message numbers
            return ret; // no messages requested
        }

        MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement(new QName("messages", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);

        try {
            int counter = args[1].getItemCount();
            for (int i = 0; i < counter; i++) {
                Message message = null;
                int msgNum = ((IntegerValue)args[1].itemAt(i)).getInt();
                try {
                    message = folder.getMessage(msgNum); // get the requested message number
                } catch (IndexOutOfBoundsException iex) {
                    logger.info("There is no message number " + msgNum);
                    continue;
                }


                builder.startElement(new QName("message", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
                builder.addAttribute(new QName("number", null, null), String.valueOf(message.getMessageNumber()));

                String contentType = message.getContentType();
                mimeParamsToAttributes(builder, contentType);


                // Subject
                builder.startElement( new QName( "subject", MailModule.NAMESPACE_URI, MailModule.PREFIX ), null );
                builder.characters( message.getSubject() );
                builder.endElement();

                // Sent Date
                if (message.getSentDate() != null) {
                    builder.startElement(new QName("sent", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
                    builder.characters(formatDate(message.getSentDate()));
                    builder.endElement();
                }

                // Received Date
                if (message.getReceivedDate() != null) {
                    builder.startElement(new QName("received", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
                    builder.characters(formatDate(message.getReceivedDate()));
                    builder.endElement();
                }

                // From
                if (message.getFrom() != null) {
                    addAddress(builder,"from", null, message.getFrom()[0]);
                }

                // Recipients
                builder.startElement(new QName("recipients", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
                // To Recipients
                Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
                if (toAddresses != null) {
                    for (Address to : toAddresses) {
                        addAddress(builder, "recipient","to",to);
                    }
                }

                // cc Recipients
                Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
                if (ccAddresses != null) {
                    for (Address ccAddress : ccAddresses) {
                        addAddress(builder,"recipient", "cc", ccAddress);
                    }
                }

                // bcc Recipients
                Address[] bccAddresses = message.getRecipients(Message.RecipientType.BCC);
                if (bccAddresses != null) {
                    for (Address bccAddress : bccAddresses) {
                        addAddress(builder,"recipient", "bcc", bccAddress);
                    }
                }
                builder.endElement(); // recipients

                // Handle the content
                Object content = message.getContent();
                if (content instanceof Multipart) {
                    handleMultipart((Multipart)content, builder);
                } else {
                    handlePart(message, builder);
                }

                // Flags
                Flags flags = message.getFlags();
                Flags.Flag[] systemFlags = flags.getSystemFlags();
                String[] userFlags = flags.getUserFlags();

                if (systemFlags.length > 0 || userFlags.length > 0) {
                    builder.startElement(new QName("flags", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);

                    for (Flags.Flag systemFlag : systemFlags) {
                        if (systemFlag == Flags.Flag.ANSWERED) {
                            addFlag(builder,"answered");
                        } else if (systemFlag == Flags.Flag.DELETED) {
                            addFlag(builder,"deleted");
                        } else if (systemFlag == Flags.Flag.DRAFT) {
                            addFlag(builder, "draft");
                        } else if (systemFlag == Flags.Flag.FLAGGED) {
                            addFlag(builder,"flagged");
                        } else if (systemFlag == Flags.Flag.RECENT) {
                            addFlag(builder,"recent");
                        } else if (systemFlag == Flags.Flag.SEEN) {
                            addFlag(builder,"seen");
                        }
                    }

                    for (String userFlag : userFlags) {
                        builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
                        builder.addAttribute(new QName("type", null, null), "user");
                        builder.addAttribute(new QName("value", null, null), userFlag);
                        builder.endElement();
                    }

                    builder.endElement();
                }
                builder.endElement();
            }

        } catch (MessagingException me) {
            throw (new XPathException(this, "Failed to retrieve messages from list", me));
        } catch (IOException | SAXException e) {
            e.printStackTrace();
        }

        builder.endElement();
        builder.endDocument();
        ret = (NodeValue) builder.getDocument().getDocumentElement();
        return (ret);
    }

    private void addFlag(MemTreeBuilder builder, String flag) {
        builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
        builder.addAttribute(new QName("type", null, null), flag);
        builder.endElement();
    }

    private void addAddress(MemTreeBuilder builder, String element, String attrVal, Address addr ) {

        builder.startElement(new QName(element, MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
        if (attrVal != null) builder.addAttribute(new QName("type", null, null), attrVal);
        InternetAddress ia = (InternetAddress)addr;
        if (ia.getPersonal() != null) {
            builder.addAttribute(new QName("personal", null, null),ia.getPersonal());
            builder.characters(ia.getAddress());
        } else {
            builder.characters(ia.getAddress());
        }
        builder.endElement();
    }

    private String formatDate(Date date) {
        String formatted = "";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        String temp = sdf.format(date);
        formatted = temp.substring(0, temp.length() - 2) + ":" + temp.substring(temp.length() - 2);
        return (formatted);
    }


    private void handleMultipart(Multipart multipart, MemTreeBuilder builder)
            throws MessagingException, IOException, XPathException, SAXException {
        for (int i=0, n=multipart.getCount(); i<n; i++) {
            handlePart(multipart.getBodyPart(i), builder);
        }
    }

    private void mimeParamsToAttributes(MemTreeBuilder builder, String contentType) {
        try {
            MimeType m = new MimeType(contentType);
            builder.addAttribute(new QName("mime-type",null,null), m.getBaseType());

            MimeTypeParameterList mtpl =  m.getParameters();
            Enumeration e = mtpl.getNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                builder.addAttribute(new QName(key, null,null), mtpl.get(key));
            }
        } catch (MimeTypeParseException e) {
            e.printStackTrace();
        }
    }

    private void handlePart(Part part, MemTreeBuilder builder)
            throws MessagingException, IOException, XPathException, SAXException {
        Object content = part.getContent();
        if (content instanceof Multipart) {
            handleMultipart((Multipart)content, builder);
            return;
        }
        String disposition = part.getDisposition();
        String contentType = part.getContentType();

            // Check if plain
        if (contentType.contains("text/plain")) {
            builder.startElement(new QName("text", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
            mimeParamsToAttributes(builder, part.getContentType());
            builder.characters(part.getContent().toString());
            builder.endElement();

        } else if (contentType.contains("text/html")) {
            builder.startElement(new QName("xhtml", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
            mimeParamsToAttributes(builder, part.getContentType());
            // extract and clean up the html
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            /* There's a bug here caused (possibly) by Apple Mail forwarding Outlook Mail
               In the hideous Outlook html, o:p tags are included as paragraph markers. They either contain nothing,
               or else an NBSP entity. The namespace prefix is correctly declared.
               Apple mail appears to strip or reduce these elements to <o:p/>
               Additionally, the namespace binding is dropped - so this throws an error when the content is parsed.
             */

            try (InputStream inputStream = part.getInputStream()) {
                DocumentImpl html = ModuleUtils.htmlToXHtml(context, new StreamSource(inputStream), null, null);
                ElementImpl rootElem = (ElementImpl) html.getDocumentElement();
                html.copyTo(rootElem,receiver);
                builder.endElement();
            }


        } else if (disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
            builder.startElement(new QName("attachment", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
            builder.addAttribute(new QName("filename", null, null), part.getFileName());
            mimeParamsToAttributes(builder, part.getContentType());
            handleBinaryContent(part, builder);
            builder.endElement();

        } else if (disposition.equalsIgnoreCase(Part.INLINE)) {
            builder.startElement(new QName("inline", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
            MimeBodyPart mbp = (MimeBodyPart)part;
            builder.addAttribute(new QName("filename", null, null), mbp.getFileName());
            // fix content id so that it matches the cid: format within the html
            if (mbp.getContentID()!= null) {
                builder.addAttribute(new QName("content-id", null, null), "cid:" + mbp.getContentID().replaceAll("^<|>$", ""));
            }
            mimeParamsToAttributes(builder, part.getContentType());
            handleBinaryContent(part, builder);
            builder.endElement();

        } else {  // Should never happen
            builder.startElement(new QName("other", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
            mimeParamsToAttributes(builder, part.getContentType());
            builder.addAttribute(new QName("disposition",null,null), part.getDisposition());
            builder.characters(part.getContent().toString());
            builder.endElement();
        }
    }

    private void handleBinaryContent(Part part, MemTreeBuilder builder) throws IOException, MessagingException, XPathException {

        builder.addAttribute(new QName("type", null, null), "binary");
        builder.addAttribute( new QName( "encoding", null, null ), "Base64Encoded" );

        BinaryValue binary = null;
        try {
            binary = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), part.getInputStream());
            builder.characters(binary.getStringValue());
        } finally {
            // free resources
            if (binary != null) {
                binary.destroy(context, null);
            }
        }
    }

}

