/*
 *  Parser.java - eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
 *  $Id:
 * 
 */
package org.exist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.Observable;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Category;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.exist.dom.AttrImpl;
import org.exist.dom.Collection;
import org.exist.dom.CommentImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentTypeImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DOMStreamer;
import org.exist.util.FastStringBuffer;
import org.exist.util.ProgressIndicator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Parser parses a given input document via SAX and stores it to
 * the database. It automatically handles index-creation.
 * 
 * @author wolf
 *
 */
public class Parser
	extends Observable
	implements ContentHandler, LexicalHandler, ErrorHandler, EntityResolver {

	private final static Category LOG = Category.getInstance(Parser.class.getName());

	public final static int MAX_STR_LEN = 225;
	public final static int SPARSE_IDENTIFIERS = 10;

	private final static int VALIDATION_ENABLED = 0;
	private final static int VALIDATION_AUTO = 1;
	private final static int VALIDATION_DISABLED = 2;

	private int validation = VALIDATION_AUTO;

	public Collection collection = null;
	protected DBBroker broker = null;
	protected FastStringBuffer charBuf = new FastStringBuffer(6, 15, 5);
	protected int currentLine = 0, maxLine;
	protected StringBuffer currentPath = new StringBuffer();
	protected DocumentImpl document = null;
	protected String fileName;
	protected boolean insideDTD = false;
	protected boolean validate = false;
	protected int level = 0;
	protected Locator locator = null;
	protected int normalize = FastStringBuffer.SUPPRESS_BOTH;
	protected XMLReader parser;
	protected Stack prefixes = new Stack();
	protected ProgressIndicator progress;
	protected boolean replace = false;
	protected CatalogResolver resolver;
	protected Element rootNode;
	protected Stack stack = new Stack();
	protected User user;
	protected boolean privileged = false;

	// reusable fields
	private TextImpl text = new TextImpl();
	private Stack usedElements = new Stack();
	private FastStringBuffer temp = new FastStringBuffer();

	/**
	 *  Create a new parser using the given database broker and
	 * user to store the document.
	 *
	 *@param  broker              
	 *@param  user                user identity
	 *@param  replace             replace existing documents?
	 *@exception  EXistException  
	 */
	public Parser(DBBroker broker, User user, boolean replace) throws EXistException {
		this(broker, user, replace, false);
	}

	/**
	 *  Create a new parser using the given database broker and
	 * user to store the document.
	 *
	 *@param  broker              
	 *@param  user                user identity
	 *@param  replace             replace existing documents?
	 *@param  privileged		  used by the security manager to
	 *							  indicate that it needs privileged
	 *                            access to the db.
	 *@exception  EXistException  
	 */
	public Parser(DBBroker broker, User user, boolean replace, boolean priv)
		throws EXistException {
		this.broker = broker;
		this.user = user;
		this.privileged = priv;
		Configuration config = broker.getConfiguration();
		// get validation settings
		String option = (String) config.getProperty("indexer.validation");
		if (option != null) {
			if (option.equals("true"))
				validation = VALIDATION_ENABLED;
			else if (option.equals("auto"))
				validation = VALIDATION_AUTO;
			else
				validation = VALIDATION_DISABLED;
		}
		resolver = (CatalogResolver) config.getProperty("resolver");
		// check whitespace suppression
		String suppressWS = (String) config.getProperty("indexer.suppress-whitespace");
		if (suppressWS != null) {
			if (suppressWS.equals("leading"))
				normalize = FastStringBuffer.SUPPRESS_LEADING_WS;
			else if (suppressWS.equals("trailing"))
				normalize = FastStringBuffer.SUPPRESS_TRAILING_WS;
			else if (suppressWS.equals("none"))
				normalize = 0;

		}
		// create a SAX parser
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		if (validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED)
			saxFactory.setValidating(true);
		else
			saxFactory.setValidating(false);
		saxFactory.setNamespaceAware(true);
		try {
			setFeature(saxFactory, "http://xml.org/sax/features/namespace-prefixes", true);
			setFeature(
				saxFactory,
				"http://apache.org/xml/features/validation/dynamic",
				validation == VALIDATION_AUTO);
			setFeature(
				saxFactory,
				"http://apache.org/xml/features/validation/schema",
				validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED);
			SAXParser sax = saxFactory.newSAXParser();
			parser = sax.getXMLReader();
			//parser.setEntityResolver(resolver);
			parser.setEntityResolver(this);
			parser.setErrorHandler(this);
			sax.setProperty("http://xml.org/sax/properties/lexical-handler", this);
		} catch (ParserConfigurationException e) {
			LOG.warn(e);
			throw new EXistException(e);
		} catch (SAXException saxe) {
			LOG.warn(saxe);
			throw new EXistException(saxe);
		}
	}

	public void setBroker(DBBroker broker) {
		this.broker = broker;
	}

	public void setOverwrite(boolean overwrite) {
		this.replace = overwrite;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public void characters(char[] ch, int start, int length) {
		if (length <= 0)
			return;
		if (charBuf != null && charBuf.length() + length < MAX_STR_LEN) {
			charBuf.append(ch, start, length);
			return;
		}

		final ElementImpl last = (ElementImpl) stack.peek();
		if (charBuf != null && charBuf.length() > 0) {
			final String normalized = charBuf.getNormalizedString(normalize);

			if (normalized.length() > 0) {
				//TextImpl text =
				//    new TextImpl( normalized );
				text.setData(normalized);
				text.setOwnerDocument(document);
				charBuf.setLength(0);
				//charBuf = new FastStringBuffer( 6, 6, 3 );
				last.appendChildInternal(text);
				if (!validate)
					broker.store(text, currentPath.toString());
			}
		}
		// if length > MAX_STR_LEN split the string into
		// smaller parts:
		if (length > MAX_STR_LEN) {
			int len = MAX_STR_LEN;
			//TextImpl text;
			while (length > 0) {
				//text = new TextImpl( ch, start, len );
				text.setData(ch, start, len);
				text.setOwnerDocument(document);
				last.appendChildInternal(text);
				if (!validate)
					broker.store(text, currentPath.toString());
				text.clear();
				start = start + len;
				length = length - len;
				if (length < MAX_STR_LEN)
					len = length;
			}
		} else {
			charBuf.setLength(0);
			//charBuf = new FastStringBuffer( 6, 6, 3 );
			charBuf.append(ch, start, length);
		}
	}

	public void comment(char[] ch, int start, int length) {
		if (insideDTD)
			return;
		CommentImpl comment = new CommentImpl(ch, start, length);
		comment.setOwnerDocument(document);
		if (stack.empty())
			document.appendChild(comment, validate);
		else {
			ElementImpl last = (ElementImpl) stack.peek();
			if (charBuf != null && charBuf.length() > 0) {
				String normalized = charBuf.getNormalizedString(normalize);
				//String normalized = charBuf.getString().toString();
				if (normalized.length() > 0) {
					//TextImpl text =
					//    new TextImpl( normalized );
					text.setData(normalized);
					text.setOwnerDocument(document);
					last.appendChildInternal(text);
					charBuf.setLength(0);
					//charBuf = new FastStringBuffer();
					if (!validate)
						broker.store(text, currentPath.toString());
				}
			}
			last.appendChildInternal(comment);
		}
		if (!validate)
			broker.store(comment, currentPath.toString());

	}

	public void endCDATA() {
	}

	public void endDTD() {
		insideDTD = false;
	}

	public void endDocument() {
	}

	public void endElement(String namespace, String name, String qname) {
		//		if(namespace != null && namespace.length() > 0 &&
		//			qname.indexOf(':') < 0)
		//			qname = '#' + namespace + ':' + qname;
		final ElementImpl last = (ElementImpl) stack.peek();
		if (last.getNodeName().equals(qname)) {
			if (charBuf != null && charBuf.length() > 0) {
				final String normalized = charBuf.getNormalizedString(normalize);
				if (normalized.length() > 0) {
					//TextImpl text =
					//    new TextImpl( normalized );
					text.setData(normalized);
					text.setOwnerDocument(document);
					charBuf.setLength(0);
					//charBuf = new FastStringBuffer( 6, 6, 3 );
					last.appendChildInternal(text);
					if (!validate)
						broker.store(text, currentPath.toString());
					text.clear();
				}
			}
			stack.pop();
			currentPath.delete(currentPath.lastIndexOf("/"), currentPath.length());
			//				currentPath.substring(0, currentPath.lastIndexOf('/'));
			if (validate) {
				if (document.getTreeLevelOrder(level) < last.getChildCount())
					document.setTreeLevelOrder(level, last.getChildCount() + SPARSE_IDENTIFIERS);
			} else {
				document.setOwnerDocument(document);
				if (broker.getDatabaseType() == DBBroker.DBM
					|| broker.getDatabaseType() == DBBroker.NATIVE) {
					if (last.getChildCount() > 0)
						broker.update(last);
				} else
					broker.store(last, currentPath.toString());
			}
			level--;
			if (last != rootNode) {
				last.clear();
				usedElements.push(last);
			}
		}
	}

	public void endEntity(String name) {
	}

	public void endPrefixMapping(String prefix) {
		prefix = (String) prefixes.pop();
	}

	public void error(SAXParseException e) throws SAXException {
		LOG.debug("error at line " + e.getLineNumber(), e);
		throw new SAXException("error at line " + e.getLineNumber() + ": " + e.getMessage(), e);
	}

	public void fatalError(SAXParseException e) throws SAXException {
		LOG.debug("fatal error at line " + e.getLineNumber(), e);
		throw new SAXException(
			"fatal error at line " + e.getLineNumber() + ": " + e.getMessage(),
			e);
	}

	public void ignorableWhitespace(char[] ch, int start, int length) {
	}

	/**
	 * Parse and store a document using the given input source.
	 * 
	 * @param src
	 * @return DocumentImpl
	 * @throws SAXException
	 * @throws IOException
	 * @throws PermissionDeniedException
	 */
	public DocumentImpl parse(InputSource src)
		throws SAXException, IOException, PermissionDeniedException {
		return parse(null, src, null);
	}

	/**
	 * Parse and store a document using the given input source and collection.
	 * 
	 * @param coll
	 * @param is
	 * @param fileName
	 * @return DocumentImpl
	 * @throws SAXException
	 * @throws IOException
	 * @throws PermissionDeniedException
	 */
	public DocumentImpl parse(Collection coll, InputSource is, String fileName)
		throws SAXException, IOException, PermissionDeniedException {
		this.collection = coll;
		final Object lock = broker.acquireWriteLock();
		try {
			scan(is, fileName);

			return store(is);
		} finally {
			broker.releaseWriteLock(lock);
		}
	}

	/**
	 * Parse and store a document using the given file.
	 * 
	 * @param file
	 * @param xmlFileName
	 * @return DocumentImpl
	 * @throws SAXException
	 * @throws IOException
	 * @throws PermissionDeniedException
	 */
	public DocumentImpl parse(File file, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		return parse(null, file, xmlFileName);
	}

	/**
	 * Parse and store a document, using the given file and collection.
	 * 
	 * @param collection
	 * @param file
	 * @param xmlFileName
	 * @return DocumentImpl
	 * @throws SAXException
	 * @throws IOException
	 * @throws PermissionDeniedException
	 */
	public DocumentImpl parse(Collection collection, File file, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		this.collection = collection;
		final Object lock = broker.acquireWriteLock();
		try {
			final InputSource in = new InputSource(file.getAbsolutePath());
			scan(in, xmlFileName);
			return store(in);
		} finally {
			broker.releaseWriteLock(lock);
		}
	}

	/**
	 * Parse and store a document from the given string.
	 * 
	 * @param str
	 * @param xmlFileName
	 * @return DocumentImpl
	 * @throws SAXException
	 * @throws IOException
	 * @throws PermissionDeniedException
	 */
	public DocumentImpl parse(String str, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		return parse(collection, str, xmlFileName);
	}

	/**
	 * Parse and store a document from the given string and collection.
	 * 
	 * @param coll
	 * @param str
	 * @param xmlFileName
	 * @return DocumentImpl
	 * @throws SAXException
	 * @throws IOException
	 * @throws PermissionDeniedException
	 */
	public DocumentImpl parse(Collection coll, String str, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		collection = coll;
		final Object lock = broker.acquireWriteLock();
		try {
			scan(new InputSource(new StringReader(str)), xmlFileName);
			return store(new InputSource(new StringReader(str)));
		} finally {
			broker.releaseWriteLock(lock);
		}
	}

	public DocumentImpl parse(byte[] data, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		return parse(collection, data, xmlFileName);
	}

	public DocumentImpl parse(Collection coll, byte[] data, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		collection = coll;
		final Object lock = broker.acquireWriteLock();
		try {
			ByteArrayInputStream bos = new ByteArrayInputStream(data);
			InputSource is = new InputSource(bos);
			is.setEncoding("UTF-8");
			scan(is, xmlFileName);
			bos.reset();
			is = new InputSource(bos);
			is.setEncoding("UTF-8");
			return store(is);
		} finally {
			broker.releaseWriteLock(lock);
		}
	}

	public DocumentImpl parse(Collection coll, Node node, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		collection = coll;
		final Object lock = broker.acquireWriteLock();
		try {
			scan(node, xmlFileName);
			return store(node);
		} finally {
			broker.releaseWriteLock(lock);
		}
	}

	public void processingInstruction(String target, String data) {
		ProcessingInstructionImpl pi = new ProcessingInstructionImpl(0, target, data);
		pi.setOwnerDocument(document);
		if (stack.isEmpty())
			document.appendChild(pi, validate);
		else {
			ElementImpl last = (ElementImpl) stack.peek();
			if (charBuf != null && charBuf.length() > 0) {
				String normalized = charBuf.getNormalizedString(normalize);
				//String normalized = charBuf.getString().toString();
				if (normalized.length() > 0) {
					//TextImpl text =
					//    new TextImpl( normalized );
					text.setData(normalized);
					text.setOwnerDocument(document);
					charBuf = new FastStringBuffer();
					//charBuf.setLength( 0 );
					last.appendChildInternal(text);
					if (!validate)
						broker.store(text, currentPath.toString());
					text.clear();
				}
			}
			last.appendChildInternal(pi);
		}
		if (!validate)
			broker.store(pi, currentPath.toString());

	}

	/**
	 *  Prepare for storing the document.
	 * 
	 * The document is parsed for validation. If a document with the same 
	 * name exists and updates are allowed, the old document is removed.
	 *
	 *@param  inStream                       InputStream
	 *@param  xmlFileName                    the name of the document
	 *@exception  SAXException               
	 *@exception  IOException                
	 *@exception  PermissionDeniedException
	 */
	public void scan(InputStream inStream, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		scan(new InputSource(inStream), xmlFileName);
	}

	/**
	 *  Prepare for storing the document.
	 * 
	 * The document is parsed for validation. If a document with the same 
	 * name exists and updates are allowed, the old document is removed.
	 * The name of the document is determined from the InputSource. 
	 *
	 *@param  src                            Description of the Parameter
	 *@exception  SAXException               Description of the Exception
	 *@exception  IOException                Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public void scan(InputSource src) throws SAXException, IOException, PermissionDeniedException {
		scan(src, null);
	}

	/**
	 *  Prepare for storing the document. 
	 * 
	 * The document is parsed for validation. If a document with the same 
	 * name exists and updates are allowed, the old document is removed. 
	 *
	 *@param  src                            InputSource
	 *@param  xmlFileName                    name of the document
	 *@exception  SAXException               
	 *@exception  IOException                
	 *@exception  PermissionDeniedException  
	 */
	public void scan(InputSource src, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		if (src == null)
			throw new IOException("no input source");
		if (broker.isReadOnly())
			throw new PermissionDeniedException("database is read-only");
		this.fileName = xmlFileName;
		parser.setContentHandler(this);
		parser.setErrorHandler(this);
		validate = true;
		int p;
		if (fileName == null) {
			fileName = src.getSystemId();
			if ((p = fileName.lastIndexOf(File.pathSeparator)) > -1)
				fileName = fileName.substring(p + 1);
		}
		if (fileName.charAt(0) != '/')
			fileName = '/' + fileName;

		if (!fileName.startsWith("/db"))
			fileName = "/db" + fileName;

		final int pos = fileName.lastIndexOf('/');
		final String collName = (pos > 0) ? fileName.substring(0, pos) : "/db";
		if (pos > 0)
			fileName = fileName.substring(pos + 1);

		if (collection == null || (!collection.getName().equals(collName))) {
			collection = broker.getOrCreateCollection(user, collName);
			broker.saveCollection(collection);
		}
		DocumentImpl oldDoc = null;
		// does a document with the same name exist?
		if ((oldDoc = collection.getDocument(collName + '/' + fileName)) != null) {
			// do we have permissions for update?
			if (!oldDoc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"document exists and update " + "is not allowed");
			// no: do we have write permissions?
		} else if (!collection.getPermissions().validate(user, Permission.WRITE))
			throw new PermissionDeniedException(
				"not allowed to write to collection " + collection.getName());
		// if an old document exists, save the new document with a temporary
		// document name
		if (oldDoc != null)
			document = new DocumentImpl(broker, collName + "/__" + fileName, collection);
		else
			document = new DocumentImpl(broker, collName + '/' + fileName, collection);
		//collection.addDocument(document);
		document.setDocId(broker.getNextDocId(collection));
		if (oldDoc == null) {
			document.getPermissions().setOwner(user);
			document.getPermissions().setGroup(user.getPrimaryGroup());
		} else
			document.setPermissions(oldDoc.getPermissions());

		// reset internal variables
		level = 0;
		currentPath.setLength(0);
		stack = new Stack();
		prefixes = new Stack();
		rootNode = null;
		LOG.debug("validating document " + fileName + " ...");
		try {
			parser.parse(src);
		} catch (SAXException e) {
			if (collection != null)
				collection.removeDocument(document.getFileName());
			throw new SAXException(
				"[line " + locator.getLineNumber() + "] " + e.getMessage(),
				e.getException());
		}
		document.setMaxDepth(document.getMaxDepth() + 1);
		try {
			document.calculateTreeLevelStartPoints();
		} catch (EXistException e1) {
			throw new SAXException(
				"the nesting-level of your document is too high. It "
				+ "does not fit into the indexing-scheme. Please split the document into "
				+ "several parts and try to reduce the nesting-level.");
		}
		
		// new document is valid: remove old document 
		if (oldDoc != null) {
			LOG.debug("removing old document " + oldDoc.getFileName());
			broker.removeDocument(oldDoc.getFileName());
			document.setFileName(oldDoc.getFileName());
			//collection.renameDocument(
			//	document.getFileName(),
			//	oldDoc.getFileName());
		}
		collection.addDocument(document);
	}

	/**
		 *  Prepare for storing the document. 
		 * 
		 * The document is parsed for validation. If a document with the same 
		 * name exists and updates are allowed, the old document is removed. 
		 *
		 *@param  src                            InputSource
		 *@param  xmlFileName                    name of the document
		 *@exception  SAXException               
		 *@exception  IOException                
		 *@exception  PermissionDeniedException  
		 */
	public void scan(Node node, String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		if (node == null)
			throw new IOException("no input");
		if (broker.isReadOnly())
			throw new PermissionDeniedException("database is read-only");
		this.fileName = xmlFileName;
		parser.setContentHandler(this);
		parser.setErrorHandler(this);
		validate = true;
		int p;
		if (fileName == null)
			throw new SAXException("no document name specified");
		if (fileName.charAt(0) != '/')
			fileName = '/' + fileName;

		if (!fileName.startsWith("/db"))
			fileName = "/db" + fileName;

		final int pos = fileName.lastIndexOf('/');
		final String collName = (pos > 0) ? fileName.substring(0, pos) : "/db";
		if (pos > 0)
			fileName = fileName.substring(pos + 1);

		if (collection == null || (!collection.getName().equals(collName))) {
			collection = broker.getOrCreateCollection(user, collName);
			//broker.saveCollection(collection);
		}
		DocumentImpl oldDoc = null;
		// does a document with the same name exist?
		if ((oldDoc = collection.getDocument(collName + '/' + fileName)) != null) {
			// do we have permissions for update?
			if (!oldDoc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"document exists and update " + "is not allowed");
			// no: do we have write permissions?
		} else if (!collection.getPermissions().validate(user, Permission.WRITE))
			throw new PermissionDeniedException(
				"not allowed to write to collection " + collection.getName());
		// if an old document exists, save the new document with a temporary
		// document name
		if (oldDoc != null)
			document = new DocumentImpl(broker, collName + "/__" + fileName, collection);
		else
			document = new DocumentImpl(broker, collName + '/' + fileName, collection);
		collection.addDocument(document);
		if (oldDoc == null) {
			document.getPermissions().setOwner(user);
			document.getPermissions().setGroup(user.getPrimaryGroup());
		} else
			document.setPermissions(oldDoc.getPermissions());

		// reset internal variables
		level = 0;
		currentPath.setLength(0);
		stack = new Stack();
		prefixes = new Stack();
		rootNode = null;
		LOG.debug("validating document " + fileName + " ...");
		DOMStreamer streamer = new DOMStreamer(this, this);
		try {
			streamer.stream(node);
		} catch (SAXException e) {
			LOG.debug(e.getMessage());
			if (collection != null)
				collection.removeDocument(document.getFileName());
			throw e;
		}
		// new document is valid: remove old document
		if (oldDoc != null) {
			broker.removeDocument(oldDoc.getFileName());
			collection.renameDocument(document.getFileName(), oldDoc.getFileName());
		}

	}

	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	/**
	 *  set SAX parser feature. This method will catch (and ignore) exceptions
	 *  if the used parser does not support a feature.
	 *
	 *@param  factory  
	 *@param  feature  
	 *@param  value    
	 */
	private void setFeature(SAXParserFactory factory, String feature, boolean value) {
		try {
			factory.setFeature(feature, value);
		} catch (SAXNotRecognizedException e) {
			LOG.warn(e);
		} catch (SAXNotSupportedException snse) {
			LOG.warn(snse);
		} catch (ParserConfigurationException pce) {
			LOG.warn(pce);
		}
	}

	public void skippedEntity(String name) {
	}

	public void startCDATA() {
	}

	// Methods of interface LexicalHandler
	// used to determine Doctype

	public void startDTD(String name, String publicId, String systemId) {
		DocumentTypeImpl docType = new DocumentTypeImpl(name, publicId, systemId);
		document.setDocumentType(docType);
		insideDTD = true;
	}

	public void startDocument() {
	}

	public void startElement(String namespace, String name, String qname, Attributes attributes) {
		ElementImpl last = null;
		ElementImpl node = null;

		if (!stack.empty()) {
			last = (ElementImpl) stack.peek();
			if (charBuf != null && charBuf.length() > 0) {
				final String normalized = charBuf.getNormalizedString(normalize);
				if (normalized.length() > 0) {
					text.setData(normalized);
					text.setOwnerDocument(document);
					last.appendChildInternal(text);
					if (!validate)
						broker.store(text, currentPath.toString());
					text.clear();
				}
			}
			if (!usedElements.isEmpty()) {
				node = (ElementImpl) usedElements.pop();
				node.setNodeName(qname);
			} else
				node = new ElementImpl(qname);
			last.appendChildInternal(node);
		} else {
			if (validate)
				node = new ElementImpl(0, qname);
			else
				node = new ElementImpl(1, qname);

			rootNode = node;
			document.appendChild(node, validate);
		}
		node.setOwnerDocument(document);
		node.setAttributes((short) attributes.getLength());
		if (prefixes.size() > 0)
			node.setPrefixes(prefixes);

		stack.push(node);
		currentPath.append('/').append(qname);
		if (!validate
			&& (broker.getDatabaseType() == DBBroker.DBM
				|| broker.getDatabaseType() == DBBroker.NATIVE)) {
			broker.store(node, currentPath.toString());
		}

		level++;
		if (document.getMaxDepth() < level)
			document.setMaxDepth(level);

		int attrLength = attributes.getLength();
		String attrQName;
		String attrPrefix;
		String attrNS;
		for (int i = 0; i < attrLength; i++) {
			attrNS = attributes.getURI(i);
			attrQName = attributes.getQName(i);
			// skip xmlns-attributes
			if (attrQName.startsWith("xmlns"))
				--attrLength;
			else {
				final AttrImpl attr = new AttrImpl(attrQName, attributes.getValue(i));
				attr.setOwnerDocument(document);
				if (attributes.getType(i).equals("ID"))
					attr.setType(AttrImpl.ID);
				node.appendChildInternal(attr);
				if (!validate)
					broker.store(attr, currentPath.toString());

			}
		}
		if (attrLength > 0)
			node.setAttributes((short) attrLength);
		currentLine = locator.getLineNumber();
		if (!validate) {
			progress.setValue(currentLine);
			setChanged();
			notifyObservers(progress);
		}
		charBuf.setLength(0);
		//		previousPath = currentPath;
	}

	public void startEntity(String name) {
	}

	public void startPrefixMapping(String prefix, String uri) {
		// get the prefix for this namespace if one has been stored
		// before
		String oldPrefix = broker.getNamespacePrefix(uri);
		if (oldPrefix == null) {
			if (prefix == null || prefix.length() == 0)
				// xmlns="namespace"
				prefix = "#" + uri;

			broker.registerNamespace(uri, prefix);
		} else
			prefix = oldPrefix;

		prefixes.push(prefix);
	}

	/**
	 *  Actually store the document to the database.
	 * 
	 * scan() should have been called before. 
	 *
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 *@exception  IOException   Description of the Exception
	 */
	public DocumentImpl store(InputSource src) throws SAXException, IOException {
		LOG.debug("storing document ...");
		try {
			final InputStream is = src.getByteStream();
			if (is != null)
				is.reset();
			else {
				final Reader cs = src.getCharacterStream();
				if (cs != null)
					cs.reset();
			}
		} catch (IOException e) {
			LOG.debug("could not reset input source", e);
		}
		try {
			progress = new ProgressIndicator(currentLine);
			//document.setMaxDepth(document.getMaxDepth() + 1);
			//document.calculateTreeLevelStartPoints();
			validate = false;
			if (document.getDoctype() == null) {
				// we don't know the doctype
				// set it to the root node's tag name
				final DocumentTypeImpl dt =
					new DocumentTypeImpl(rootNode.getTagName(), null, document.getFileName());
				document.setDocumentType(dt);
			}
			if (broker.getDatabaseType() != DBBroker.NATIVE) {
				broker.storeDocument(document);
				broker.saveCollection(collection);
			} else
				broker.addDocument(collection, document);
			document.setChildCount(0);
			parser.parse(src);
			//if(broker.getDatabaseType() == DBBroker.NATIVE)
			//	broker.addDocument(collection, document);
			broker.closeDocument();
			broker.flush();
			if (document.getFileName().equals("/db/system/users.xml") && privileged == false) {
				// inform the security manager that system data has changed
				LOG.debug("users.xml changed");
				broker.getBrokerPool().reloadSecurityManager(broker);
			}
			return document;
		} catch (NullPointerException npe) {
			LOG.debug("null pointer", npe);
			throw new SAXException(npe);
		} catch (PermissionDeniedException e) {
			throw new SAXException("permission denied");
		}
	}

	/**
	 *  Actually store the document to the database.
	 * 
	 * scan() should have been called before. 
	 *
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 *@exception  IOException   Description of the Exception
	 */
	public DocumentImpl store(Node node) throws SAXException, IOException {
		LOG.debug("storing document ...");
		try {
			progress = new ProgressIndicator(currentLine);
			validate = false;
			if (document.getDoctype() == null) {
				// we don't know the doctype
				// set it to the root node's tag name
				final DocumentTypeImpl dt =
					new DocumentTypeImpl(rootNode.getTagName(), null, document.getFileName());
				document.setDocumentType(dt);
			}
			if (broker.getDatabaseType() != DBBroker.NATIVE) {
				broker.storeDocument(document);
				broker.saveCollection(collection);
			} else {
				broker.addDocument(collection, document);
			}
			document.setChildCount(0);
			DOMStreamer streamer = new DOMStreamer(this, this);
			streamer.stream(node);
			broker.flush();
			if (document.getFileName().equals("/db/system/users.xml") && privileged == false) {
				// inform the security manager that system data has changed
				LOG.debug("users.xml changed");
				broker.getBrokerPool().reloadSecurityManager(broker);
			}
			return document;
		} catch (NullPointerException npe) {
			LOG.debug("null pointer", npe);
			throw new SAXException(npe);
		} catch (PermissionDeniedException e) {
			throw new SAXException("permission denied");
		}
	}

	public void warning(SAXParseException e) throws SAXException {
		LOG.debug("warning at line " + e.getLineNumber(), e);
		throw new SAXException("warning at line " + e.getLineNumber() + ": " + e.getMessage(), e);
	}

	/**
	 * Try to resolve external entities.
	 * 
	 * This method forwards the request to the resolver. If that fails,
	 * the method replaces absolute file names with relative ones 
	 * and retries to resolve. This makes it possible to use relative
	 * file names in the catalog.
	 * 
	 * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
	 */
	public InputSource resolveEntity(String publicId, String systemId)
		throws SAXException, IOException {
		InputSource is = resolver.resolveEntity(publicId, systemId);
		// if resolution failed and publicId == null,
		// try to make absolute file names relative and retry
		if (is == null) {
			if (publicId != null)
				return null;
			URL url = new URL(systemId);
			if (url.getProtocol().equals("file")) {
				String path = url.getPath();
				File f = new File(path);
				if (!f.canRead())
					return resolver.resolveEntity(null, f.getName());
				else
					return new InputSource(f.getAbsolutePath());
			} else
				return new InputSource(url.openStream());
		}
		return is;
	}

}
