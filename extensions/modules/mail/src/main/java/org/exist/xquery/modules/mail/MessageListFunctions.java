/*
 *  eXist Mail Module Extension MessageListFunctions
 *  Copyright (C) 2006-09 Adam Retter <adam.retter@devon.gov.uk>
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
 *  $Id$
 */

package org.exist.xquery.modules.mail;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.search.AndTerm;
import javax.mail.search.BodyTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.HeaderTerm;
import javax.mail.search.NotTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SentDateTerm;
import javax.mail.search.SubjectTerm;
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
				new FunctionParameterSequenceType( "mail-folder-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The mail folder handle retrieved from mail:get-mail-folder()" )
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
				new FunctionParameterSequenceType( "mail-folder-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The mail folder handle retrieved from mail:get-mail-folder()" ),
				new FunctionParameterSequenceType( "search-parameters", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The xml fragment defining the search terms" )
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the message list handle." )
			),
		
		new FunctionSignature(
			new QName( "get-message-list-as-xml", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Returns a message list of all messages in a folder as XML.  If there are no messages in the list, an empty sequence will be returned",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "message-list-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The message list handle retrieved from mail:get-message-list() or mail:search-message-list()" ),
				new FunctionParameterSequenceType( "include-headers", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "A boolean specifying whether to include message headers" )
			},
			new FunctionReturnSequenceType( Type.ELEMENT, Cardinality.ZERO_OR_ONE, "the list of all messages in a folder as XML" )
			),
		
		new FunctionSignature(
			new QName( "close-message-list", MailModule.NAMESPACE_URI, MailModule.PREFIX ),
			"Closes a message list.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "message-list-handle", Type.INTEGER, Cardinality.EXACTLY_ONE, "The message list handle retrieved from mail:get-message-list() or mail:search-message-list()" )
			},
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
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
			
		return( new IntegerValue( MailModule.storeMessageList( context, msgList, folderHandle ) ) );
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
			
		return( new IntegerValue( MailModule.storeMessageList( context, msgList, folderHandle ) ) );
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

	private Sequence getMessageListAsXML( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		Message[] 		 msgList;
		Sequence 		 ret		= Sequence.EMPTY_SEQUENCE;
		
		// was a msgList handle specified?
		if( args[0].isEmpty() ) {
			throw( new XPathException(this, "Message List handle not specified" ) );
		}

		// get the MessageList
		long msgListHandle = ((IntegerValue)args[0].itemAt(0)).getLong();
		msgList = MailModule.retrieveMessageList( context, msgListHandle );
		if( msgList == null ) {
			throw( new XPathException(this, "Invalid Message List handle specified" ) );
		}
		
		if( msgList.length > 0 ) {
			
			boolean includeHeaders = args[1].effectiveBooleanValue();
			
			MemTreeBuilder builder = context.getDocumentBuilder();
        
	        builder.startDocument();
	        builder.startElement( new QName( "messages", MailModule.NAMESPACE_URI, MailModule.PREFIX ), null );
	        builder.addAttribute( new QName( "count", null, null ), String.valueOf( msgList.length ) );
			
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
			}
			catch( MessagingException me ) {
				throw( new XPathException(this, "Failed to retrieve messages from list", me ) );
			}
			
			builder.endElement();
        
        	ret = (NodeValue)builder.getDocument().getDocumentElement();
		}
		
		return( ret );
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
		
		if( terms.getNodeType() == Node.ELEMENT_NODE && terms.getLocalName().equalsIgnoreCase( "searchTerm" ) ) {
			String type  = ((Element)terms).getAttribute( "type" );
			
			if( type != null ) {
				if( type.equalsIgnoreCase( "not" ) ) {
					st = new NotTerm( parseChildSearchTerm( terms ) );
				} else if( type.equalsIgnoreCase( "and" ) ) {
					st = new AndTerm( parseChildSearchTerms( terms ) );
				} else if( type.equalsIgnoreCase( "or" ) ) {
					st = new OrTerm( parseChildSearchTerms( terms ) );
				} else if( type.equalsIgnoreCase( "from" ) ) {
					st = parseFromTerm( terms );
				} else if( type.equalsIgnoreCase( "subject" ) ) {
					st = parseSubjectTerm( terms );
				} else if( type.equalsIgnoreCase( "body" ) ) {
					st = parseBodyTerm( terms );
				} else if( type.equalsIgnoreCase( "to" ) || type.equalsIgnoreCase( "recipient" ) ) {
					st = parseRecipientTerm( terms );
				} else if( type.equalsIgnoreCase( "header" ) ) {
					st = parseHeaderTerm( terms );
				} else if( type.equalsIgnoreCase( "flag" ) ) {
					st = parseFlagTerm( terms );
				} else if( type.equalsIgnoreCase( "sent" ) ) {
					st = parseSentDateTerm( terms );
				} else if( type.equalsIgnoreCase( "received" ) ) {
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
		
		return st.toArray(new SearchTerm[st.size()]);
	}

	private SearchTerm parseFromTerm( Node terms ) throws XPathException
	{
		SearchTerm	st = null;
		
		String pattern  = ((Element)terms).getAttribute( "pattern" );
		
		if( pattern != null && pattern.length() > 0 ) {
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
		
		if( pattern != null && pattern.length() > 0 ) {
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
		
		if( pattern != null && pattern.length() > 0 ) {
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
		
		if( pattern != null && pattern.length() > 0 ) {
			Message.RecipientType rtype = null;
			
			if( type.equalsIgnoreCase( "to" ) ) {
				rtype = Message.RecipientType.TO;
			} else if( type.equalsIgnoreCase( "cc" ) ) {
				rtype = Message.RecipientType.CC;
			} else if( type.equalsIgnoreCase( "bcc" ) ) {
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
		
		if( pattern != null && pattern.length() > 0 ) {
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
		
		if( flag != null && flag.length() > 0 ) {
			Flags flags = null;
			
			if( flag.equalsIgnoreCase( "answered" ) ) {
				flags = new Flags( Flags.Flag.ANSWERED );
			} else if( flag.equalsIgnoreCase( "deleted" ) ) {
				flags = new Flags( Flags.Flag.DELETED );
			} else if( flag.equalsIgnoreCase( "draft" ) ) {
				flags = new Flags( Flags.Flag.DRAFT );
			} else if( flag.equalsIgnoreCase( "recent" ) ) {
				flags = new Flags( Flags.Flag.RECENT );
			} else if( flag.equalsIgnoreCase( "seen" ) ) {
				flags = new Flags( Flags.Flag.SEEN );
			} else {
				throw( new XPathException(this, "Invalid flag: " + flag + ", for term with type: " + ((Element)terms).getAttribute( "type" ) ) );
			}
				
			st = new FlagTerm( flags, value.equalsIgnoreCase( "true" ) );
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
		
		if( comp != null && comp.length() > 0 ) {
			if( comp.equalsIgnoreCase( "eq" ) ) {
				cp = ComparisonTerm.EQ;
			} else if( comp.equalsIgnoreCase( "ge" ) ) {
				cp = ComparisonTerm.GE;
			} else if( comp.equalsIgnoreCase( "gt" ) ) {
				cp = ComparisonTerm.GT;
			} else if( comp.equalsIgnoreCase( "le" ) ) {
				cp = ComparisonTerm.LE;
			} else if( comp.equalsIgnoreCase( "lt" ) ) {
				cp = ComparisonTerm.LT;
			} else if( comp.equalsIgnoreCase( "ne" ) ) {
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
