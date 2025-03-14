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


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.BodyTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.HeaderTerm;
import jakarta.mail.search.NotTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.RecipientStringTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SentDateTerm;
import jakarta.mail.search.SubjectTerm;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * eXist Mail Module Extension GetMessageList
 * 
 * Get a mail store
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @serial 2009-03-12
 * @version 1.3
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class MessageListFunctions extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MessageListFunctions.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "get-message-list", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Returns a message list of all messages in a folder.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "mail-folder-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The mail folder handle retrieved from mail:get-mail-folder()" )
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the message list handle." )
			),
		
		new FunctionSignature(
			new QName( "search-message-list", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Searches messages in a folder. " +
			"Search terms are of the form <searchTerm type=\"xxx\">...</searchTerm>.  Valid types include: not, and, or, from, subject, body, recipient, header, flag, sent, received. " +
			"<searchTerm type=\"not\"> requires a single nested child search term. <searchTerm type=\"and\"> and <searchTerm type=\"or\"> must have one or more nested child search terms. " +
			"<searchTerm type=\"from\" pattern=\"pat\">, <searchTerm type=\"subject\" pattern=\"pat\"> and <searchTerm type=\"body\" pattern=\"pat\">  require a pattern attribute and will search for a substring that matches the pattern. " +
			"<searchTerm type=\"recipient\" pattern=\"pat\" recipientType=\"to|cc|bcc\"> requires pattern and recipientType attributes. " +
			"<searchTerm type=\"header\" pattern=\"pat\" name=\"Content-Type\"> requires pattern and name attributes. " +
			"<searchTerm type=\"flag\" flag=\"answered|deleted|draft|recent|seen\" value=\"true|false\"> requires flag and value attributes. " +
			"<searchTerm type=\"sent\" comparison=\"eq|gt|ge|lt|le|ne\" format=\"format\" date=\"date\"> and <searchTerm type=\"received\" comparison=\"eq|gt|ge|lt|le|ne\" format=\"format\" date=\"date\"> require comparison, format and date attributes. " +
			"The format string should conform to Java SimpleDateFormat specifications and the date string must conform to the specified format string.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "mail-folder-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The mail folder handle retrieved from mail:get-mail-folder()" ),
				new FunctionParameterSequenceType( "search-parameters", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The xml fragment defining the search terms" )
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the message list handle." )
			),
		
		new FunctionSignature(
			new QName( "get-message-list-as-xml", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Returns a message list of all messages in a folder as XML.  If there are no messages in the list, an empty sequence will be returned",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "message-list-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The message list handle retrieved from mail:get-message-list() or mail:search-message-list()" ),
				new FunctionParameterSequenceType( "include-headers", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "A boolean specifying whether to include message headers" )
			},
			new FunctionReturnSequenceType( Type.ELEMENT, Cardinality.ZERO_OR_ONE, "the list of all messages in a folder as XML" )
			),
		
		new FunctionSignature(
			new QName( "close-message-list", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Closes a message list.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "message-list-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The message list handle retrieved from mail:get-message-list() or mail:search-message-list()" )
			},
			new SequenceType( Type.ITEM, Cardinality.EMPTY_SEQUENCE )
			)
	};
	
	private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
	
	private static final String PREFETCH_HEADERS[] = {
		"Return-Path",
		"Delivered-To",
		"Received",
		"Date",
		"From",
		"To",
		"Message-ID",
		"Subject",
		"MIME-Version",
		"Content-Type",
		"Content-Transfer-Encoding",
		"X-Mailer",
		"X-Priority"
	};

	public MessageListFunctions( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	@Override
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		if( isCalledAs( "get-message-list" ) ) {
            return getMessageList( args, contextSequence );
		} else if( isCalledAs( "search-message-list" ) ) {
            return searchMessageList( args, contextSequence );
		} else if( isCalledAs( "get-message-list-as-xml" ) ) {
            return getMessageListAsXML( args, contextSequence );
		} else if( isCalledAs( "close-message-list" ) ) {
            return closeMessageList( args, contextSequence );
		} 
			
		throw( new XPathException(this, "Invalid function name" ) );	
	}
	
	
	//***************************************************************************
	//*
	//*    Function Implementation Methods
	//*
	//***************************************************************************/
	
	private Sequence getMessageList( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Message[] msgList;
		
		// was a folder handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException(this, "Folder handle not specified" ) );
		}

		// get the Folder
		long folderHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Folder folder= MailModule.retrieveFolder( context, folderHandle );
		if( folder == null ) {
			throw( new XPathException(this, "Invalid Folder handle specified" ) );
		}
		
		try {
			msgList = folder.getMessages();
			prefetchMessages( folder, msgList );
		}
		catch( MessagingException me ) {
			throw( new XPathException(this, "Failed to get mail list", me ) );
		}
		
		// save the message list and return the handle of the message list
			
		return( new IntegerValue( this, MailModule.storeMessageList( context, msgList, folderHandle ), Type.LONG ) );
	}

	private Sequence searchMessageList( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Message[] msgList;
		
		// was a folder handle specified?
		if( args[0].isEmpty() || args[1].isEmpty() ) {
			throw( new XPathException(this, "Folder handle or Search Terms not specified" ) );
		}

		// get the Folder
		long folderHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		Folder folder= MailModule.retrieveFolder( context, folderHandle );
		if( folder == null ) {
			throw( new XPathException(this, "Invalid Folder handle specified" ) );
		}
		
		Node searchTermsXML = ( (NodeValue)args[1].itemAt( 0 ) ).getNode();
		
		try {
			msgList = folder.search( parseSearchTerms( searchTermsXML ) );
			
			prefetchMessages( folder, msgList );
		}
		catch( MessagingException me ) {
			throw( new XPathException(this, "Failed to get mail list", me ) );
		}
		
		// save the message list and return the handle of the message list
			
		return( new IntegerValue( this, MailModule.storeMessageList( context, msgList, folderHandle ), Type.LONG ) );
	}

	private void prefetchMessages( Folder folder, Message[] msgList ) throws MessagingException
	{
		// Prefetch all the key information and headers
		
		FetchProfile fp = new FetchProfile();
		fp.add( FetchProfile.Item.ENVELOPE );
		
        for (String PREFETCH_HEADER : PREFETCH_HEADERS) {
            fp.add(PREFETCH_HEADER);
        }
		folder.fetch( msgList, fp );
	}

	private Sequence getMessageListAsXML(Sequence[] args, Sequence contextSequence) throws XPathException {
		Message[] msgList;
		Sequence ret = Sequence.EMPTY_SEQUENCE;

		// was a msgList handle specified?
		if (args[0].isEmpty()) {
			throw (new XPathException(this, "Message List handle not specified"));
		}

		// get the MessageList
		long msgListHandle = ((IntegerValue) args[0].itemAt(0)).getLong();
		msgList = MailModule.retrieveMessageList(context, msgListHandle);
		if (msgList == null) {
			throw (new XPathException(this, "Invalid Message List handle specified"));
		}

		if (msgList.length > 0) {

			boolean includeHeaders = args[1].effectiveBooleanValue();

			context.pushDocumentContext();
			try {
				MemTreeBuilder builder = context.getDocumentBuilder();

				builder.startDocument();
				builder.startElement(new QName("messages", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
				builder.addAttribute(new QName("count", null, null), String.valueOf(msgList.length));

				try {
					for (Message message : msgList) {
						builder.startElement(new QName("message", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);

						builder.addAttribute(new QName("number", null, null), String.valueOf(message.getMessageNumber()));

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
							builder.startElement(new QName("from", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
							builder.characters(message.getFrom()[0].toString());
							builder.endElement();
						}

						// Recipients
						builder.startElement(new QName("recipients", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
						// To Recipients
						Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
						if (toAddresses != null) {
							for (Address to : toAddresses) {
								builder.startElement(new QName("recipient", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
								builder.addAttribute(new QName("type", null, null), "to");
								builder.characters(to.toString());
								builder.endElement();
							}
						}

						// cc Recipients
						Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
						if (ccAddresses != null) {
							for (Address ccAddress : ccAddresses) {
								builder.startElement(new QName("recipient", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
								builder.addAttribute(new QName("type", null, null), "cc");
								builder.characters(ccAddress.toString());
								builder.endElement();
							}
						}

						// bcc Recipients
						Address[] bccAddresses = message.getRecipients(Message.RecipientType.BCC);
						if (bccAddresses != null) {
							for (Address bccAddress : bccAddresses) {
								builder.startElement(new QName("recipient", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
								builder.addAttribute(new QName("type", null, null), "bcc");
								builder.characters(bccAddress.toString());
								builder.endElement();
							}
						}
						builder.endElement();

						// Flags

						Flags flags = message.getFlags();
						Flags.Flag[] systemFlags = flags.getSystemFlags();
						String[] userFlags = flags.getUserFlags();

						if (systemFlags.length > 0 || userFlags.length > 0) {
							builder.startElement(new QName("flags", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);

							for (Flags.Flag systemFlag : systemFlags) {
								if (systemFlag == Flags.Flag.ANSWERED) {
									builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("type", null, null), "answered");
									builder.endElement();
								} else if (systemFlag == Flags.Flag.DELETED) {
									builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("type", null, null), "deleted");
									builder.endElement();
								} else if (systemFlag == Flags.Flag.DRAFT) {
									builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("type", null, null), "draft");
									builder.endElement();
								} else if (systemFlag == Flags.Flag.FLAGGED) {
									builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("type", null, null), "flagged");
									builder.endElement();
								} else if (systemFlag == Flags.Flag.RECENT) {
									builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("type", null, null), "recent");
									builder.endElement();
								} else if (systemFlag == Flags.Flag.SEEN) {
									builder.startElement(new QName("flag", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("type", null, null), "seen");
									builder.endElement();
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

						// Headers

						if (includeHeaders) {
							Enumeration headers = message.getAllHeaders();

							if (headers.hasMoreElements()) {
								builder.startElement(new QName("headers", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);

								while (headers.hasMoreElements()) {
									Header header = (Header) headers.nextElement();

									builder.startElement(new QName("header", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
									builder.addAttribute(new QName("name", null, null), header.getName());
									builder.addAttribute(new QName("value", null, null), header.getValue());
									builder.endElement();
								}

								builder.endElement();
							}
						}

						// Subject
						builder.startElement(new QName("subject", MailModule.NAMESPACE_URI, MailModule.PREFIX), null);
						builder.characters(message.getSubject());
						builder.endElement();

						builder.endElement();
					}
				} catch (MessagingException me) {
					throw (new XPathException(this, "Failed to retrieve messages from list", me));
				}

				builder.endElement();

				ret = (NodeValue) builder.getDocument().getDocumentElement();
			} finally {
				context.popDocumentContext();

			}
		}

		return (ret);
	}

	private String formatDate( Date date ) 
	{
		String formatted = "";
		
		SimpleDateFormat sdf = new SimpleDateFormat( DATE_FORMAT );
		
		String temp = sdf.format( date );
		
		formatted = temp.substring( 0, temp.length() - 2 ) + ":" + temp.substring( temp.length() - 2 );
		
		return( formatted );
	}

	private Sequence closeMessageList( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		// was a msgList handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException(this, "Message List handle not specified" ) );
		}
		
		// get the msgList
		long msgListHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
	
		MailModule.removeMessageList( context, msgListHandle );
			
		return( Sequence.EMPTY_SEQUENCE );
	}
	
	
	//***************************************************************************
	//*
	//*    Search Term Methods
	//*
	//***************************************************************************/
	
	private SearchTerm parseSearchTerms( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		if( terms.getNodeType() == Node.ELEMENT_NODE && "searchTerm".equalsIgnoreCase(terms.getLocalName()) ) {
			String type  = ((Element)terms).getAttribute( "type" );
			
			if( type != null ) {
				if( "not".equalsIgnoreCase(type) ) {
					st = new NotTerm( parseChildSearchTerm( terms ) );
				} else if( "and".equalsIgnoreCase(type) ) {
					st = new AndTerm( parseChildSearchTerms( terms ) );
				} else if( "or".equalsIgnoreCase(type) ) {
					st = new OrTerm( parseChildSearchTerms( terms ) );
				} else if( "from".equalsIgnoreCase(type) ) {
					st = parseFromTerm( terms );
				} else if( "subject".equalsIgnoreCase(type) ) {
					st = parseSubjectTerm( terms );
				} else if( "body".equalsIgnoreCase(type) ) {
					st = parseBodyTerm( terms );
				} else if( "to".equalsIgnoreCase(type) || "recipient".equalsIgnoreCase(type) ) {
					st = parseRecipientTerm( terms );
				} else if( "header".equalsIgnoreCase(type) ) {
					st = parseHeaderTerm( terms );
				} else if( "flag".equalsIgnoreCase(type) ) {
					st = parseFlagTerm( terms );
				} else if( "sent".equalsIgnoreCase(type) ) {
					st = parseSentDateTerm( terms );
				} else if( "received".equalsIgnoreCase(type) ) {
					st = parseReceivedDateTerm( terms );
				} else {
					throw( new XPathException(this, "Invalid Search Term type specified: " + type ) );
				}
			} else {
				throw( new XPathException(this, "Invalid Search Term type specified: null" ) );
			}
		} 
		
		if( st == null ) {
			throw( new XPathException(this, "Invalid Search Terms specified" ) );
		}
		
		return( st );
	}

	private SearchTerm parseChildSearchTerm( Node terms ) throws XPathException
	{
		// Parent only allows a single child search term
		
		SearchTerm	st = null;
		
		NodeList children = terms.getChildNodes();
		
		if( children.getLength() == 1 ) {
			Node child = children.item( 0 );
			
			st = parseSearchTerms( child );
		} else {
			throw( new XPathException(this, "Only one child term is allowed for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm[] parseChildSearchTerms( Node terms ) throws XPathException
	{
		// Parent allows multiple child search terms
		
		ArrayList<SearchTerm> st = new ArrayList<>();
		
		NodeList children = terms.getChildNodes();
		
		if( children.getLength() > 0 ) {
			for( int i = 0; i < children.getLength(); i++ ) {
				Node child = children.item( i );
				
				st.add( parseSearchTerms( child ) );
			}
		} else {
			throw( new XPathException(this, "At least one child term is required for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return st.toArray(new SearchTerm[0]);
	}

	private SearchTerm parseFromTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String pattern  = ((Element)terms).getAttribute( "pattern" );
		
		if( pattern != null && !pattern.isEmpty()) {
			st = new FromStringTerm( pattern );
		} else {
			throw( new XPathException(this, "Pattern attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseSubjectTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String pattern  = ((Element)terms).getAttribute( "pattern" );
		
		if( pattern != null && !pattern.isEmpty()) {
			st = new SubjectTerm( pattern );
		} else {
			throw( new XPathException(this, "Pattern attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseBodyTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String pattern  = ((Element)terms).getAttribute( "pattern" );
		
		if( pattern != null && !pattern.isEmpty()) {
			st = new BodyTerm( pattern );
		} else {
			throw( new XPathException(this, "Pattern attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseRecipientTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String pattern  = ((Element)terms).getAttribute( "pattern" );
		String type     = ((Element)terms).getAttribute( "recipientType" );
		
		if( StringUtils.isEmpty(type) ) {
			throw( new XPathException(this, "recipientType not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		if( pattern != null && !pattern.isEmpty()) {
			Message.RecipientType rtype = null;
			
			if( "to".equalsIgnoreCase(type) ) {
				rtype = Message.RecipientType.TO;
			} else if( "cc".equalsIgnoreCase(type) ) {
				rtype = Message.RecipientType.CC;
			} else if( "bcc".equalsIgnoreCase(type) ) {
				rtype = Message.RecipientType.BCC;
			} else {
				throw( new XPathException(this, "Invalid recipientType: " + type + ", for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
			}
			
			st = new RecipientStringTerm( rtype, pattern );
		} else {
			throw( new XPathException(this, "Pattern attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseHeaderTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String pattern  = ((Element)terms).getAttribute( "pattern" );
		String name     = ((Element)terms).getAttribute( "name" );
		
		if( StringUtils.isEmpty(name) ) {
			throw( new XPathException(this, "name not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		if( pattern != null && !pattern.isEmpty()) {
			st = new HeaderTerm( name, pattern );
		} else {
			throw( new XPathException(this, "pattern attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseFlagTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String flag  = ((Element)terms).getAttribute( "flag" );
		String value = ((Element)terms).getAttribute( "value" );
		
		if( StringUtils.isEmpty(value) ) {
			throw( new XPathException(this, "value not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		if( flag != null && !flag.isEmpty()) {
			Flags flags = null;
			
			if( "answered".equalsIgnoreCase(flag) ) {
				flags = new Flags( Flags.Flag.ANSWERED );
			} else if( "deleted".equalsIgnoreCase(flag) ) {
				flags = new Flags( Flags.Flag.DELETED );
			} else if( "draft".equalsIgnoreCase(flag) ) {
				flags = new Flags( Flags.Flag.DRAFT );
			} else if( "recent".equalsIgnoreCase(flag) ) {
				flags = new Flags( Flags.Flag.RECENT );
			} else if( "seen".equalsIgnoreCase(flag) ) {
				flags = new Flags( Flags.Flag.SEEN );
			} else {
				throw( new XPathException(this, "Invalid flag: " + flag + ", for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
			}
				
			st = new FlagTerm( flags, "true".equalsIgnoreCase(value) );
		} else {
			throw( new XPathException(this, "flag attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseSentDateTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String value = ((Element)terms).getAttribute( "date" );
		String format = ((Element)terms).getAttribute( "format" );
		
		if( StringUtils.isEmpty(value) ) {
			throw( new XPathException(this, "value not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		if( StringUtils.isEmpty(format) ) {
			throw( new XPathException(this, "format not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		int  cp = parseComparisonAttribute( terms );
		
		try {
			SimpleDateFormat sdf = new SimpleDateFormat( format );
			
			Date date = sdf.parse( value );
			
			st = new SentDateTerm( cp, date );
		}
		catch( ParseException pe ) {
			throw( new XPathException(this, "Cannot parse date value: " + value + ", using format: " + format + ", for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private SearchTerm parseReceivedDateTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String value = ((Element)terms).getAttribute( "date" );
		String format = ((Element)terms).getAttribute( "format" );
		
		if( StringUtils.isEmpty(value) ) {
			throw( new XPathException(this, "value not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		if( StringUtils.isEmpty(format) ) {
			throw( new XPathException(this, "format not specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		int  cp = parseComparisonAttribute( terms );
		
		try {
			SimpleDateFormat sdf = new SimpleDateFormat( format );
			
			Date date = sdf.parse( value );
			
			st = new ReceivedDateTerm( cp, date );
		}
		catch( ParseException pe ) {
			throw( new XPathException(this, "Cannot parse date value: " + value + ", using format: " + format + ", for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( st );
	}

	private int parseComparisonAttribute( Node terms ) throws XPathException
	{
		int  cp = ComparisonTerm.EQ;
		
		String comp  = ((Element)terms).getAttribute( "comparison" );
		
		if( comp != null && !comp.isEmpty()) {
			if( "eq".equalsIgnoreCase(comp) ) {
				cp = ComparisonTerm.EQ;
			} else if( "ge".equalsIgnoreCase(comp) ) {
				cp = ComparisonTerm.GE;
			} else if( "gt".equalsIgnoreCase(comp) ) {
				cp = ComparisonTerm.GT;
			} else if( "le".equalsIgnoreCase(comp) ) {
				cp = ComparisonTerm.LE;
			} else if( "lt".equalsIgnoreCase(comp) ) {
				cp = ComparisonTerm.LT;
			} else if( "ne".equalsIgnoreCase(comp) ) {
				cp = ComparisonTerm.NE;
			} else {
				throw( new XPathException(this, "Invalid comparison: " + comp + ", for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
			}
		} else {
			throw( new XPathException(this, "comparison attribute must be specified for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
		}
		
		return( cp );
	}
}
