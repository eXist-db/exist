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
import java.io.*;
import java.util.Iterator;
import java.util.Observable;
import java.util.Stack;

import javax.xml.parsers.*;

import org.apache.log4j.Category;

//import com.sun.resolver.tools.CatalogResolver;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.exist.dom.*;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.*;
import org.exist.util.*;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
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
	implements ContentHandler, LexicalHandler, ErrorHandler {

	private final static Category LOG =
		Category.getInstance(Parser.class.getName());

	public final static int MAX_STR_LEN = 225;
	public final static int SPARSE_IDENTIFIERS = 100;

	protected DBBroker broker = null;
	protected FastStringBuffer charBuf = new FastStringBuffer(6, 15, 5);
	protected Collection collection = null;
	protected boolean validate;
	protected int currentLine = 0, maxLine;
	protected StringBuffer currentPath = new StringBuffer();
	protected DocumentImpl document = null;
	protected String fileName;
	protected boolean insideDTD = false;
	protected int level = 0;
	protected Locator locator = null;
	protected int maxLevel = 0;
	protected int normalize = FastStringBuffer.SUPPRESS_BOTH;
	protected XMLReader parser;
	protected Stack prefixes = new Stack();
	protected ProgressIndicator progress;
	protected boolean replace = false;
	protected CatalogResolver resolver;
	protected Element rootNode;
	protected InputSource src;
	protected Stack stack = new Stack();
	protected User user;
	private String previousPath = null;
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
	public Parser(DBBroker broker, User user, boolean replace)
		throws EXistException {
		this.broker = broker;
		this.user = user;
		resolver =
			(CatalogResolver) broker.getConfiguration().getProperty("resolver");
		String suppressWS =
			(String) broker.getConfiguration().getProperty(
				"indexer.suppress-whitespace");
		if (suppressWS != null) {
			if (suppressWS.equals("leading"))
				normalize = FastStringBuffer.SUPPRESS_LEADING_WS;
			else if (suppressWS.equals("trailing"))
				normalize = FastStringBuffer.SUPPRESS_TRAILING_WS;
			else if (suppressWS.equals("none"))
				normalize = 0;

		}
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		//saxFactory.setValidating(false);
		saxFactory.setNamespaceAware(true);
		try {
			setFeature(
				saxFactory,
				"http://xml.org/sax/features/namespace-prefixes",
				true);
			setFeature(
				saxFactory,
				"http://apache.org/xml/features/validation/dynamic",
				true);
			setFeature(
				saxFactory,
				"http://apache.org/xml/features/validation/schema",
				false);
			SAXParser sax = saxFactory.newSAXParser();
			parser = sax.getXMLReader();
			parser.setEntityResolver(resolver);
			sax.setProperty(
				"http://xml.org/sax/properties/lexical-handler",
				this);
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
			document.appendChild(comment);
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
				final String normalized =
					charBuf.getNormalizedString(normalize);
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
			currentPath.delete(currentPath.lastIndexOf("/"), 
				currentPath.length());
//				currentPath.substring(0, currentPath.lastIndexOf('/'));
			if (validate) {
				if (document.getTreeLevelOrder(level) < last.getChildCount())
					document.setTreeLevelOrder(level, last.getChildCount());
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
		previousPath = null;
	}

	public void endEntity(String name) {
	}

	public void endPrefixMapping(String prefix) {
		prefix = (String) prefixes.pop();
	}

	public void error(SAXParseException e) throws SAXException {
		LOG.warn(e);
		System.out.println("parse error at line " + e.getLineNumber());
	}

	public void fatalError(SAXParseException e) throws SAXException {
		LOG.debug("fatal error at line " + e.getLineNumber());
		LOG.error(e);
		throw new SAXException(e);
	}

	private final String getCurrentPath() {
		//final StringBuffer buf = new StringBuffer();
		temp.setLength(0);
		ElementImpl current;
		for (Iterator i = stack.iterator(); i.hasNext();) {
			temp.append('/');
			current = (ElementImpl) i.next();
			temp.append(current.getTagName());
		}
		previousPath = temp.toString();
		return previousPath;
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

			return store();
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
	public DocumentImpl parse(
		Collection collection,
		File file,
		String xmlFileName)
		throws SAXException, IOException, PermissionDeniedException {
		this.collection = collection;
		final Object lock = broker.acquireWriteLock();
		try {
			final InputSource in = new InputSource(file.getAbsolutePath());
			scan(in, xmlFileName);
			return store();
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
			this.src = new InputSource(new StringReader(str));
			return store();
		} finally {
			broker.releaseWriteLock(lock);
		}
	}

	public void processingInstruction(String target, String data) {
		ProcessingInstructionImpl pi =
			new ProcessingInstructionImpl(0, target, data);
		pi.setOwnerDocument(document);
		if (stack.isEmpty())
			document.appendChild(pi);
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
	public void scan(InputSource src)
		throws SAXException, IOException, PermissionDeniedException {
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
		final String pathSep = File.pathSeparator;
		this.src = src;
		this.fileName = xmlFileName;
		parser.setContentHandler(this);
		parser.setErrorHandler(this);
		validate = true;
		int p;
		if (fileName == null) {
			fileName = src.getSystemId();
			if ((p = fileName.lastIndexOf(pathSep)) > -1)
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
		if ((oldDoc = collection.getDocument(collName + '/' + fileName))
			!= null) {
			// do we have permissions for update?
			if (!oldDoc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"document exists and update " + "is not allowed");
		// no: do we have write permissions?
		} else if (
			!collection.getPermissions().validate(user, Permission.WRITE))
			throw new PermissionDeniedException(
				"not allowed to write to collection " + collection.getName());
		// if an old document exists, save the new document with a temporary
		// document name
		if(oldDoc != null)
			document =
				new DocumentImpl(broker, collName + "/__" + fileName, collection);
		else
			document =
				new DocumentImpl(broker, collName + '/' + fileName, collection);
		collection.addDocument(document);
		if (oldDoc == null) {
			document.getPermissions().setOwner(user);
			document.getPermissions().setGroup(user.getPrimaryGroup());
		} else
			document.setPermissions(oldDoc.getPermissions());
			
		// reset internal variables
		maxLevel = 0;
		level = 0;
		currentPath.setLength(0);
		stack = new Stack();
		prefixes = new Stack();
		previousPath = null;
		rootNode = null;
		LOG.debug("validating document " + fileName + " ...");
		try {
			parser.parse(src);
		} catch (SAXException e) {
			LOG.debug(e.getMessage());
			if (collection != null)
				collection.removeDocument(document.getFileName());
			throw e;
		}
        // new document is valid: remove old document
        if(oldDoc != null) {
            broker.removeDocument(oldDoc.getFileName());
			collection.renameDocument(document.getFileName(),
				oldDoc.getFileName());
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
	private void setFeature(
		SAXParserFactory factory,
		String feature,
		boolean value) {
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
		DocumentTypeImpl docType =
			new DocumentTypeImpl(name, publicId, systemId);
		document.setDocumentType(docType);
		insideDTD = true;
	}

	public void startDocument() {
	}

	public void startElement(
		String namespace,
		String name,
		String qname,
		Attributes attributes) {
		ElementImpl last = null;
		ElementImpl node = null;

		if (!stack.empty()) {
			last = (ElementImpl) stack.peek();
			if (charBuf != null && charBuf.length() > 0) {
				final String normalized =
					charBuf.getNormalizedString(normalize);
				if (normalized.length() > 0) {
					text.setData(normalized);
					text.setOwnerDocument(document);
					charBuf.setLength(0);
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
			if (!validate)
				node = new ElementImpl(1, qname);
			else
				node = new ElementImpl(0, qname);

			rootNode = node;
			document.appendChild(node);
			document.setDocumentElement(node.getGID());
		}
		node.setOwnerDocument(document);
		node.setAttributes((short) attributes.getLength());
		if (prefixes.size() > 0)
			node.setPrefixes(prefixes);

		stack.push(node);
		currentPath.append('/').append(qname);
		if (!validate
			&& (broker.getDatabaseType() == DBBroker.DBM
				|| broker.getDatabaseType() == DBBroker.NATIVE))
			broker.store(node, currentPath.toString());

		level++;
		if (document.getMaxDepth() < level)
			document.setMaxDepth(level);

		int attrLength = attributes.getLength();
		if (attrLength > 0 && document.getMaxDepth() < level + 1) {
			document.setMaxDepth(level + 1);
			if (validate && document.getTreeLevelOrder(level + 1) < attrLength)
				document.setTreeLevelOrder(level + 1, attrLength);
		}
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
				final AttrImpl attr =
					new AttrImpl(attrQName, attributes.getValue(i));
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
	public DocumentImpl store() throws SAXException, IOException {
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
			document.setMaxDepth(document.getMaxDepth() + 1);
			document.calculateTreeLevelStartPoints();
			validate = false;
			if (document.getDoctype() == null) {
				// we don't know the doctype
				// set it to the root node's tag name
				final DocumentTypeImpl dt =
					new DocumentTypeImpl(
						rootNode.getTagName(),
						null,
						document.getFileName());
				document.setDocumentType(dt);
			}
			if (broker.getDatabaseType() != DBBroker.NATIVE) {
				broker.storeDocument(document);
				broker.saveCollection(collection);
			} else {
				broker.addDocument(collection, document);
			}
			document.setChildCount(0);
			parser.parse(src);
			broker.flush();
			return document;
		} catch (NullPointerException npe) {
			LOG.debug("null pointer", npe);
			throw new SAXException(npe);
		} catch (PermissionDeniedException e) {
			throw new SAXException("permission denied");
		}
	}

	// Methods of interface ErrorHandler

	public void warning(SAXParseException e) throws SAXException {
		LOG.warn(e);
		System.out.println("parser warning at line " + e.getLineNumber());
	}
}
