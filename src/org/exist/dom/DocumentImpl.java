/*
 *  eXist Open Source Native XML Database
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
 */
package org.exist.dom;
import java.io.*;
import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.User;

import org.exist.storage.*;
import org.w3c.dom.*;
import org.exist.util.SyntaxException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    21. Mai 2002
 */
public class DocumentImpl extends NodeImpl implements Document, Comparable {

	private NodeIndexListener listener = null;

	private static Category LOG =
		Category.getInstance(DocumentImpl.class.getName());

	protected DBBroker broker = null;

	// number of child nodes
	protected int children = 0;

	// the collection this document belongs to
	private Collection collection = null;

	// the document's id
	protected int docId = -1;

	// document's document type
	protected DocumentType docType = null;

	// id of the document element
	protected long documentRootId = -1;

	// the document's file name
	private String fileName = null;

	// number of levels in this DOM tree
	protected int maxDepth = 0;

	// if set to > -1, the document needs to be partially reindexed
	// - beginning at the tree-level defined by reindex
	protected int reindex = -1;

	protected Permission permissions = new Permission(0754);

	// storage address for the document metadata
	protected long address = -1;

	// arity of the tree at every level
	protected int treeLevelOrder[] = new int[25];

	protected long treeLevelStartPoints[] = new long[25];

	// has document-metadata been loaded?
	private boolean complete = true;
	
	/**
	 *  Constructor for the DocumentImpl object
	 *
	 *@param  broker      Description of the Parameter
	 *@param  collection  Description of the Parameter
	 */
	public DocumentImpl(DBBroker broker, Collection collection) {
		super(Node.DOCUMENT_NODE, 0);
		this.broker = broker;
		this.collection = collection;
		this.ownerDocument = this;
		treeLevelOrder[0] = 1;
	}

	/**
	 *  Constructor for the DocumentImpl object
	 *
	 *@param  broker  Description of the Parameter
	 */
	public DocumentImpl(DBBroker broker) {
		this(broker, null, null);
	}

	/**
	 *  Constructor for the DocumentImpl object
	 *
	 *@param  broker    Description of the Parameter
	 *@param  fileName  Description of the Parameter
	 */
	public DocumentImpl(DBBroker broker, String fileName) {
		this(broker, null, null);
		this.fileName = fileName;
	}

	/**
	 *  Constructor for the DocumentImpl object
	 *
	 *@param  broker      Description of the Parameter
	 *@param  fileName    Description of the Parameter
	 *@param  collection  Description of the Parameter
	 */
	public DocumentImpl(
		DBBroker broker,
		String fileName,
		Collection collection) {
		super(Node.DOCUMENT_NODE, 0);
		this.broker = broker;
		this.fileName = fileName;
		this.ownerDocument = this;
		this.collection = collection;
		treeLevelOrder[0] = 1;
	}

	public DocumentImpl(DocumentImpl old) {
		super(Node.DOCUMENT_NODE, 0);
		this.broker = old.broker;
		this.fileName = old.fileName;
		this.ownerDocument = this;
		this.collection = old.collection;
		this.children = old.children;
		this.maxDepth = old.maxDepth;
		this.docId = old.docId;
		this.documentRootId = old.documentRootId;
		this.docType = old.docType;
		this.permissions = old.permissions;
		treeLevelOrder = new int[old.treeLevelOrder.length];
		for (int i = 0; i < treeLevelOrder.length; i++)
			treeLevelOrder[i] = old.treeLevelOrder[i];
		treeLevelStartPoints = new long[old.treeLevelStartPoints.length];
		for (int i = 0; i < treeLevelStartPoints.length; i++)
			treeLevelStartPoints[i] = old.treeLevelStartPoints[i];
	}

	/**
	 *  Description of the Method
	 *
	 *@param  gid   Description of the Parameter
	 *@param  type  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	protected static NodeImpl createNode(long gid, short type) {
		NodeImpl node;
		switch (type) {
			case Node.TEXT_NODE :
				node = new TextImpl(gid);
				break;
			case Node.ELEMENT_NODE :
				node = new ElementImpl(gid);
				break;
			case Node.ATTRIBUTE_NODE :
				node = new AttrImpl(gid);
				break;
			default :
				LOG.debug("unknown node type");
				node = null;
		}
		return node;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  gid       Description of the Parameter
	 *@param  type      Description of the Parameter
	 *@param  name      Description of the Parameter
	 *@param  data      Description of the Parameter
	 *@param  children  Description of the Parameter
	 *@return           Description of the Return Value
	 */
	protected static NodeImpl createNode(
		long gid,
		short type,
		String name,
		String data,
		int children) {
		NodeImpl node;
		switch (type) {
			case Node.TEXT_NODE :
				node = new TextImpl(gid, data);
				break;
			case Node.ELEMENT_NODE :
				node = new ElementImpl(gid, name);
				node.setChildCount(children);
				break;
			default :
				node = null;
		}
		return node;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  node              Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Node adoptNode(Node node) throws DOMException {
		return node;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  child             Description of the Parameter
	 *@exception  DOMException  Description of the Exception
	 */
	public void appendChild(NodeImpl child) throws DOMException {
		++children;
		child.setGID(children);
		treeLevelOrder[0] = children;
	}

	/**  Description of the Method */
	public void calculateTreeLevelStartPoints() {
		treeLevelStartPoints = new long[maxDepth];
		// we know the start point of the root element (which is always 1)
		// and the start point of the first non-root node (2)
		treeLevelStartPoints[0] = 1;
		treeLevelStartPoints[1] = children + 1;
		for (int i = 1; i < maxDepth - 1; i++) {
			treeLevelStartPoints[i + 1] =
				(treeLevelStartPoints[i] - treeLevelStartPoints[i - 1])
					* treeLevelOrder[i]
					+ treeLevelStartPoints[i];
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  other  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public int compareTo(Object other) {
		if (!(other instanceof DocumentImpl))
			throw new RuntimeException("cannot compare nodes from different implementations");
		if (((DocumentImpl) other).docId == docId)
			return 0;
		else if (docId < ((DocumentImpl) other).docId)
			return -1;
		else
			return 1;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name              Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Attr createAttribute(String name) throws DOMException {
		AttrImpl attr = new AttrImpl(name, null);
		attr.setOwnerDocument(this);
		return attr;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  namespaceURI      Description of the Parameter
	 *@param  qualifiedName     Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Attr createAttributeNS(String namespaceURI, String qualifiedName)
		throws DOMException {
		return createAttribute(qualifiedName);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  data              Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public CDATASection createCDATASection(String data) throws DOMException {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  data  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	public Comment createComment(String data) {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public DocumentFragment createDocumentFragment() throws DOMException {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  tagName           Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Element createElement(String tagName) throws DOMException {
		ElementImpl element = new ElementImpl(tagName);
		element.setOwnerDocument(this);
		return element;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  namespaceURI      Description of the Parameter
	 *@param  qualifiedName     Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Element createElementNS(String namespaceURI, String qualifiedName)
		throws DOMException {
		return createElement(qualifiedName);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name              Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public EntityReference createEntityReference(String name)
		throws DOMException {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  target            Description of the Parameter
	 *@param  data              Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public ProcessingInstruction createProcessingInstruction(
		String target,
		String data)
		throws DOMException {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  data  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	public Text createTextNode(String data) {
		TextImpl text = new TextImpl(data);
		text.setOwnerDocument(this);
		return text;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  root     Description of the Parameter
	 *@param  tagName  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	protected NodeList findElementsByTagName(NodeImpl root, String tagName) {
		DocumentSet docs = new DocumentSet();
		docs.add(this);
		NodeSet context = new ArraySet(1);
		NodeSet result = new ArraySet(100);
		context.add(root);
		NodeSet temp = (NodeSet) broker.findElementsByTagName(docs, tagName);
		NodeProxy p;
		for (Iterator iter = temp.iterator(); iter.hasNext();) {
			p = (NodeProxy) iter.next();
			if (context.nodeHasParent(p.doc, p.gid, true))
				result.add(p);
		}
		return result;
	}

	/**
	 *  Gets the childCount attribute of the DocumentImpl object
	 *
	 *@return    The childCount value
	 */
	public int getChildCount() {
		return children;
	}

	/*
	 *  methods of NodeImpl
	 */
	/**
	 *  Gets the childNodes attribute of the DocumentImpl object
	 *
	 *@return    The childNodes value
	 */
	public NodeList getChildNodes() {
		NodeListImpl list = new NodeListImpl();
		for (int i = 1; i <= children; i++)
			list.add(getNode(i));

		return list;
	}

	/**
	 *  Gets the collection attribute of the DocumentImpl object
	 *
	 *@return    The collection value
	 */
	public Collection getCollection() {
		return collection;
	}

	/**
	 *  Gets the docId attribute of the DocumentImpl object
	 *
	 *@return    The docId value
	 */
	public int getDocId() {
		return docId;
	}

	/**
	 *  Gets the doctype attribute of the DocumentImpl object
	 *
	 *@return    The doctype value
	 */
	public DocumentType getDoctype() {
		checkAvail();
		return docType;
	}

	/*
	 *  W3C Document-Methods
	 */
	/**
	 *  Gets the documentElement attribute of the DocumentImpl object
	 *
	 *@return    The documentElement value
	 */
	public Element getDocumentElement() {
		checkAvail();
		if (-1 < documentRootId)
			return (Element) getNode(documentRootId);
		else {
			Node n;
			for (int i = 1; i <= children; i++) {
				n = getNode(i);
				if (n.getNodeType() == Node.ELEMENT_NODE)
					return (Element) n;
			}
			LOG.warn("document element for " + getFileName() + " not found!");
		}
		return null;
	}

	/**
	 *  Gets the documentElementId attribute of the DocumentImpl object
	 *
	 *@return    The documentElementId value
	 */
	public long getDocumentElementId() {
		checkAvail();
		if (documentRootId < 0)
			return ((ElementImpl) getDocumentElement()).getGID();
		return documentRootId;
	}

	/**
	 *  Gets the elementById attribute of the DocumentImpl object
	 *
	 *@param  elementId  Description of the Parameter
	 *@return            The elementById value
	 */
	public Element getElementById(String elementId) {
		return null;
	}

	/**
	 *  Gets the elementsByTagName attribute of the DocumentImpl object
	 *
	 *@param  tagname  Description of the Parameter
	 *@return          The elementsByTagName value
	 */
	public NodeList getElementsByTagName(String tagname) {
		DocumentSet docs = new DocumentSet();
		docs.add(this);
		return broker.findElementsByTagName(docs, tagname);
	}

	/**
	 *  Gets the elementsByTagNameNS attribute of the DocumentImpl object
	 *
	 *@param  namespaceURI  Description of the Parameter
	 *@param  localName     Description of the Parameter
	 *@return               The elementsByTagNameNS value
	 */
	public NodeList getElementsByTagNameNS(
		String namespaceURI,
		String localName) {
		return null;
	}

	/**
	 *  Gets the encoding attribute of the DocumentImpl object
	 *
	 *@return    The encoding value
	 */
	public String getEncoding() {
		return "UTF-8";
	}

	/**
	 *  Gets the fileName attribute of the DocumentImpl object
	 *
	 *@return    The fileName value
	 */
	public String getFileName() {
		//checkAvail();
		return fileName;
	}

	private void checkAvail() {
		if(!complete)
			broker.readDocumentMetadata(this);
		complete = true;
	}
	
	/**
	 *  Gets the implementation attribute of the DocumentImpl object
	 *
	 *@return    The implementation value
	 */
	public org.w3c.dom.DOMImplementation getImplementation() {
		return null;
	}

	/**
	 *  Gets the levelStartPoint attribute of the DocumentImpl object
	 *
	 *@param  level  Description of the Parameter
	 *@return        The levelStartPoint value
	 */
	public long getLevelStartPoint(int level) {
		if (level >= maxDepth || level < 0)
			return -1;
		return treeLevelStartPoints[level];
	}

	/**
	 *  Gets the maxDepth attribute of the DocumentImpl object
	 *
	 *@return    The maxDepth value
	 */
	public int getMaxDepth() {
		return maxDepth;
	}

	/**
	 *  Gets the node attribute of the DocumentImpl object
	 *
	 *@param  gid  Description of the Parameter
	 *@return      The node value
	 */
	public Node getNode(long gid) {
		return broker.objectWith(this, gid);
	}

	/**
	 *  Gets the privileges attribute of the DocumentImpl object
	 *
	 *@return    The privileges value
	 */
	public Permission getPermissions() {
		return permissions;
	}

	/**
	 *  Gets the range attribute of the DocumentImpl object
	 *
	 *@param  first  Description of the Parameter
	 *@param  last   Description of the Parameter
	 *@return        The range value
	 */
	public NodeList getRange(long first, long last) {
		return broker.getRange(this, first, last);
	}

	public boolean getStandalone() {
		return true;
	}

	public boolean getStrictErrorChecking() {
		return false;
	}

	public SymbolTable getSymbols() {
		//return collection.getSymbols();
		return NativeBroker.getSymbols();
	}

	public int getTreeLevel(long gid) {
		for (int i = 0; i < maxDepth; i++) {
			if ((gid >= treeLevelStartPoints[i])
				&& (i + 1 == maxDepth || gid < treeLevelStartPoints[i + 1]))
				return i;
		}
		return -1;
	}

	/**
	 *  Gets the treeLevelOrder attribute of the DocumentImpl object
	 *
	 *@param  level  Description of the Parameter
	 *@return        The treeLevelOrder value
	 */
	public int getTreeLevelOrder(int level) {
		if (level >= treeLevelOrder.length) {
			LOG.fatal("tree level " + level + " does not exist");
			return -1;
		}
		return treeLevelOrder[level];
	}

	/**
	 *  Gets the treeLevelOrder attribute of the DocumentImpl object
	 *
	 *@param  gid  Description of the Parameter
	 *@return      The treeLevelOrder value
	 */
	public int getTreeLevelOrder(long gid) {
		int order = 0;
		for (int i = 0; i < maxDepth; i++)
			if (gid >= treeLevelStartPoints[i]
				&& gid < treeLevelStartPoints[i + 1]) {
				order = treeLevelOrder[i];
				break;
			}
		return order;
	}

	/**
	 *  Gets the version attribute of the DocumentImpl object
	 *
	 *@return    The version value
	 */
	public String getVersion() {
		return "";
	}

	/**
	 *  Description of the Method
	 *
	 *@param  importedNode      Description of the Parameter
	 *@param  deep              Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Node importNode(Node importedNode, boolean deep)
		throws DOMException {
		return null;
	}

	/**
	 *  Gets the supported attribute of the DocumentImpl object
	 *
	 *@param  type   Description of the Parameter
	 *@param  value  Description of the Parameter
	 *@return        The supported value
	 */
	public boolean isSupported(String type, String value) {
		return false;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  istream          Description of the Parameter
	 *@exception  IOException  Description of the Exception
	 */
	public void read(DataInput istream) throws IOException, EOFException {
		fileName = istream.readUTF();
		docId = istream.readInt();
		children = istream.readInt();
		maxDepth = istream.readInt();
		short ocount = istream.readShort();
		treeLevelOrder = new int[ocount];
		for (int i = 0; i < ocount; i++)
			treeLevelOrder[i] = istream.readInt();

		short tcount = istream.readShort();
		treeLevelStartPoints = new long[tcount];
		for (int i = 0; i < tcount; i++)
			treeLevelStartPoints[i] = istream.readLong();

		docType = new DocumentTypeImpl();
		((DocumentTypeImpl) docType).read(istream);
		documentRootId = istream.readLong();
		permissions.read(istream);
	}

	public void read(VariableByteInputStream istream)
		throws IOException, EOFException {
		docId = istream.readInt();
		fileName = collection.getName() + '/' + istream.readUTF();
		children = istream.readInt();
		address = DOMFile.createPointer(istream.readInt(), istream.readInt());
		maxDepth = istream.readInt();
		treeLevelOrder = new int[maxDepth];
		for (int i = 0; i < maxDepth; i++)
			treeLevelOrder[i] = istream.readInt();
		final SecurityManager secman = broker.getBrokerPool().getSecurityManager();
		final int uid = istream.readInt();
		final int gid = istream.readInt();
		final int perm = istream.readByte();
		if(secman == null) {
			permissions.setOwner(SecurityManager.DBA_USER);
			permissions.setGroup(SecurityManager.DBA_GROUP);
		} else {
			permissions.setOwner(secman.getUser(uid));
			permissions.setGroup(secman.getGroup(gid).getName());
		}
		permissions.setPermissions(perm);
		calculateTreeLevelStartPoints();
		complete = false;
	}

	/**
	 *  Sets the broker attribute of the DocumentImpl object
	 *
	 *@param  broker  The new broker value
	 */
	public void setBroker(DBBroker broker) {
		this.broker = broker;
	}

	/**
	 *  Sets the childCount attribute of the DocumentImpl object
	 *
	 *@param  count  The new childCount value
	 */
	public void setChildCount(int count) {
		children = count;
	}

	public void setDocId(int docId) {
		this.docId = docId;
	}

	public void setDocumentElement(long gid) {
		documentRootId = gid;
	}

	public void setDocumentType(DocumentType docType) {
		this.docType = docType;
	}

	public void setEncoding(String enc) {
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setMaxDepth(int depth) {
		maxDepth = depth;
	}

	public void incMaxDepth() {
		++maxDepth;
		if (treeLevelOrder.length < maxDepth) {
			int temp[] = new int[maxDepth];
			System.arraycopy(treeLevelOrder, 0, temp, 0, maxDepth - 1);
			treeLevelOrder = temp;
			treeLevelOrder[maxDepth - 1] = 0;
		}
	}

	public void setPermissions(int mode) {
		permissions.setPermissions(mode);
	}

	public void setPermissions(String mode) throws SyntaxException {
		permissions.setPermissions(mode);
	}

	public void setPermissions(Permission perm) {
		permissions = perm;
	}

	public void setStandalone(boolean alone) {
	}

	public void setStrictErrorChecking(boolean strict) {
	}

	public void setTreeLevelOrder(int level, int order) {
		treeLevelOrder[level] = order;
	}

	public void setVersion(String version) {
	}

	public void store(NodeImpl node, String path) {
		node.setOwnerDocument(this);
		broker.store(node, path);
	}

	public void write(DataOutput ostream) throws IOException {
		ostream.writeUTF(fileName);
		ostream.writeInt(docId);
		ostream.writeInt(children);
		ostream.writeInt(maxDepth);
		ostream.writeShort(treeLevelOrder.length);
		for (int i = 0; i < treeLevelOrder.length; i++)
			ostream.writeInt(treeLevelOrder[i]);

		ostream.writeShort(treeLevelStartPoints.length);
		for (int i = 0; i < treeLevelStartPoints.length; i++)
			ostream.writeLong(treeLevelStartPoints[i]);

		((DocumentTypeImpl) docType).write(ostream);
		ostream.writeLong(documentRootId);
		permissions.write(ostream);
		//symbols.write(ostream);
	}

	public void write(VariableByteOutputStream ostream) throws IOException {
		ostream.writeInt(docId);
		ostream.writeUTF(fileName.substring(collection.getName().length() + 1));
		ostream.writeInt(children);
		ostream.writeInt(DOMFile.pageFromPointer(address));
		ostream.writeInt(DOMFile.tidFromPointer(address));
		//System.out.println("doc = " + docId + "address = " + DOMFile.tidFromPointer(address));
		//Thread.dumpStack();
		ostream.writeInt(maxDepth);
		for (int i = 0; i < maxDepth; i++)
			ostream.writeInt(treeLevelOrder[i]);
		SecurityManager secman = broker.getBrokerPool().getSecurityManager();
		if(secman == null) {
			ostream.writeInt(1);
			ostream.writeInt(1);
		} else {
			User user = secman.getUser(permissions.getOwner());
			Group group = secman.getGroup(permissions.getOwnerGroup());
			ostream.writeInt(user.getUID());
			ostream.writeInt(group.getId());
		}
		ostream.writeByte((byte)permissions.getPermissions());
	}

	public int reindexRequired() {
		return reindex;
	}
	
	public byte[] serialize() {
		VariableByteOutputStream ostream = new VariableByteOutputStream();
		try {
			//ostream.writeUTF(fileName.substring(collection.getName().length() + 1));
			ostream.writeLong(documentRootId);
			((DocumentTypeImpl) docType).write(ostream);
		} catch(IOException e) {
			LOG.warn("io error while writing document data", e);
			return null;
		}
		return ostream.toByteArray();
	}
	
	public void deserialize(byte[] data) {
		VariableByteInputStream istream = new VariableByteInputStream(data);
		try {
			//fileName = collection.getName() + '/' + istream.readUTF();
			//children = istream.readInt();
			documentRootId = istream.readLong();
			docType = new DocumentTypeImpl();
			((DocumentTypeImpl) docType).read(istream);
		} catch(IOException e) {
			LOG.warn("io error while writing document data", e);
		}
	}
	
	public void setReindexRequired(int level) {
		reindex = level;
	}
	
	public void setIndexListener(NodeIndexListener listener) {
		this.listener = listener;
	}
	
	public NodeIndexListener getIndexListener() {
		return listener;
	}
	
	public void clearIndexListener() {
		listener = null;
	}
	
	public void setAddress(long address) {
		this.address = address;
	}
	
	public long getAddress() {
		return address;
	}
}
