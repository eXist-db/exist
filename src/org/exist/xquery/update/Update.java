/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
 *  http://exist-db.org
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
package org.exist.xquery.update;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.util.LockException;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * @author wolf
 *
 */
public class Update extends Modification {

	public Update(XQueryContext context, Expression select, Expression value) {
		super(context, select, value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence inSeq = select.eval(contextSequence);
		if (inSeq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		if (!Type.subTypeOf(inSeq.getItemType(), Type.NODE))
			throw new XPathException(getASTNode(), Messages.getMessage(Error.UPDATE_SELECT_TYPE));
		
		Sequence contentSeq = value.eval(contextSequence);
		if (contentSeq.getLength() == 0)
			throw new XPathException(getASTNode(), Messages.getMessage(Error.UPDATE_EMPTY_CONTENT));
		try {
            NodeImpl ql[] = selectAndLock(inSeq.toNodeSet());
            IndexListener listener = new IndexListener(ql);
            NodeImpl node;
            Node temp;
            TextImpl text;
            AttrImpl attribute;
            ElementImpl parent;
            DocumentImpl doc = null;
            Collection collection = null, prevCollection = null;
            DocumentSet modifiedDocs = new DocumentSet();
            for (int i = 0; i < ql.length; i++) {
                node = ql[i];
                doc = (DocumentImpl) node.getOwnerDocument();
                doc.setIndexListener(listener);
                modifiedDocs.add(doc);
                collection = doc.getCollection();
                if (!doc.getPermissions().validate(context.getUser(),
                        Permission.UPDATE))
                        throw new XPathException(getASTNode(),
                                "permission to update document denied");
                if (prevCollection != null && collection != prevCollection)
                        doc.getBroker().saveCollection(prevCollection);
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
						NodeListImpl content = new NodeListImpl();
						for (SequenceIterator j = contentSeq.iterate(); j.hasNext(); ) {
							Item next = j.nextItem();
							if (Type.subTypeOf(next.getType(), Type.NODE))
								content.add(((NodeValue)next).getNode());
							else {
								text = new TextImpl(next.getStringValue());
								content.add(text);
							}
						}
                        ((ElementImpl) node).update(content);
                        break;
                    case Node.TEXT_NODE:
                        parent = (ElementImpl) node.getParentNode();
                    	text = new TextImpl(contentSeq.getStringValue());
                        text.setOwnerDocument(doc);
                        parent.updateChild(node, text);
                        break;
                    case Node.ATTRIBUTE_NODE:
                        parent = (ElementImpl) node.getParentNode();
                        if (parent == null) {
                            LOG.warn("parent node not found for "
                                    + node.getGID());
                            break;
                        }
                        AttrImpl attr = (AttrImpl) node;
                        attribute = new AttrImpl(attr.getQName(), contentSeq.getStringValue());
                        attribute.setOwnerDocument(doc);
                        parent.updateChild(node, attribute);
                        break;
                    default:
                        throw new XPathException(getASTNode(), "unsupported node-type");
                }
                doc.setLastModified(System.currentTimeMillis());
                prevCollection = collection;
            }
            if (doc != null) doc.getBroker().saveCollection(collection);
            checkFragmentation(modifiedDocs);
        } catch (LockException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
		} catch (PermissionDeniedException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
		} catch (EXistException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
		} finally {
            unlockDocuments();
        }
		return Sequence.EMPTY_SEQUENCE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
	 */
	public void dump(ExpressionDumper dumper) {
		dumper.display("update value").nl();
		dumper.startIndent();
		select.dump(dumper);
		dumper.nl().endIndent().display("with").nl().startIndent();
		value.dump(dumper);
		dumper.nl().endIndent();
	}
}
