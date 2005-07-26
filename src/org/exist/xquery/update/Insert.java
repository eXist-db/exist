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
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeListImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
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
import org.w3c.dom.NodeList;

/**
 * @author wolf
 *
 */
public class Insert extends Modification {

	public final static int INSERT_BEFORE = 0;

    public final static int INSERT_AFTER = 1;

	public final static int INSERT_APPEND = 2;
	
    private int mode = INSERT_BEFORE;
	
	/**
	 * @param context
	 * @param select
	 * @param value
	 */
	public Insert(XQueryContext context, Expression select, Expression value, int mode) {
		super(context, select, value);
		this.mode = mode;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence inSeq = select.eval(contextSequence);
        LOG.debug("Found: " + inSeq.getLength());
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
            NodeImpl[] ql = selectAndLock(inSeq.toNodeSet());
            IndexListener listener = new IndexListener(ql);
            NodeImpl node;
            NodeImpl parent;
            DocumentImpl doc = null;
            DocumentSet modifiedDocs = new DocumentSet();
            NodeList contentList = seq2nodeList(contentSeq);
            for (int i = 0; i < ql.length; i++) {
                node = ql[i];
                doc = (DocumentImpl) node.getOwnerDocument();
                doc.setIndexListener(listener);
                if (!doc.getPermissions().validate(context.getUser(),
                        Permission.UPDATE))
                    throw new XPathException(getASTNode(),
                        "permission to remove document denied");
                modifiedDocs.add(doc);
				if (mode == INSERT_APPEND) {
					node.appendChildren(transaction, contentList, -1);
				} else {
	                parent = (NodeImpl) node.getParentNode();
	                switch (mode) {
	                    case INSERT_BEFORE:
	                        parent.insertBefore(transaction, contentList, node);
	                        break;
	                    case INSERT_AFTER:
	                        ((NodeImpl) parent).insertAfter(transaction, contentList, node);
	                        break;
	                }
				}
                doc.clearIndexListener();
                doc.setLastModified(System.currentTimeMillis());
                context.getBroker().storeDocument(transaction, doc);
            }
            checkFragmentation(transaction, modifiedDocs);

            transact.commit(transaction);
        } catch (PermissionDeniedException e) {
			throw new XPathException(getASTNode(), e.getMessage(), e);
		} catch (EXistException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
		} catch (LockException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
		} finally {
            unlockDocuments();
        }
		return Sequence.EMPTY_SEQUENCE;
	}

	private NodeList seq2nodeList(Sequence contentSeq) {
        NodeListImpl nl = new NodeListImpl();
        for (SequenceIterator i = contentSeq.iterate(); i.hasNext(); ) {
            Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                NodeValue val = (NodeValue) item;
                nl.add(val.getNode());
            }
        }
        return nl;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
	 */
	public void dump(ExpressionDumper dumper) {
        dumper.display("update insert").nl();
        dumper.startIndent();
        value.dump(dumper);
        dumper.endIndent();
        switch (mode) {
            case INSERT_AFTER:
                dumper.display(" following ");
                break;
            case INSERT_BEFORE:
                dumper.display(" preceding ");
                break;
            case INSERT_APPEND:
                dumper.display("into");
                break;
        }
        dumper.startIndent();
        select.dump(dumper);
        dumper.nl().endIndent();
	}
}
