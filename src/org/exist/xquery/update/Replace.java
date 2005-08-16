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
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.update.Modification.IndexListener;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author wolf
 *
 */
public class Replace extends Modification {
	
	/**
	 * @param context
	 */
	public Replace(XQueryContext context, Expression select, Expression value) {
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
        contentSeq = deepCopy(contentSeq);
        
		try {
            TransactionManager transact = context.getBroker().getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();
            NodeImpl ql[] = selectAndLock(inSeq.toNodeSet());
            IndexListener listener = new IndexListener(ql);
            NodeImpl node;
            Item temp;
            TextImpl text;
            AttrImpl attribute;
            ElementImpl parent;
            DocumentImpl doc = null;
            DocumentSet modifiedDocs = new DocumentSet();
            for (int i = 0; i < ql.length; i++) {
                node = ql[i];
                doc = (DocumentImpl) node.getOwnerDocument();
                doc.setIndexListener(listener);
                modifiedDocs.add(doc);
                if (!doc.getPermissions().validate(context.getUser(),
                        Permission.UPDATE))
                        throw new PermissionDeniedException(
                                "permission to update document denied");
                parent = (ElementImpl) node.getParentNode();
                if (parent == null)
                    throw new XPathException(getASTNode(), "The root element of a document can not be replaced with 'update replace'. " +
                            "Please consider removing the document or use 'update value' to just replace the children of the root.");
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        temp = contentSeq.itemAt(0);
						if (!Type.subTypeOf(temp.getType(), Type.NODE))
							throw new XPathException(getASTNode(),
									Messages.getMessage(Error.UPDATE_REPLACE_ELEM_TYPE,
											Type.getTypeName(temp.getType())));
                        parent.replaceChild(transaction, ((NodeValue)temp).getNode(), node);
                        break;
                    case Node.TEXT_NODE: 
                        text = new TextImpl(contentSeq.getStringValue());
                        text.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, text);
                        break;
                    case Node.ATTRIBUTE_NODE:
                        AttrImpl attr = (AttrImpl) node;
                        attribute = new AttrImpl(attr.getQName(), contentSeq.getStringValue());
                        attribute.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, attribute);
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }
                doc.setLastModified(System.currentTimeMillis());
                context.getBroker().storeDocument(transaction, doc);
            }
            checkFragmentation(transaction, modifiedDocs);
            transact.commit(transaction);
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
		dumper.display("update replace").nl();
		dumper.startIndent();
		select.dump(dumper);
		dumper.nl().endIndent().display("with").nl().startIndent();
		value.dump(dumper);
		dumper.nl().endIndent();
	}
}
