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
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.StoredNode;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.NotificationService;
import org.exist.storage.UpdateListener;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;

/**
 * @author wolf
 *
 */

//TODO: this is the only update function that uses transaction.abort() do we really need to use it and if so shouldnt the others use it as well?!?

public class Delete extends Modification {

	/**
     * 
     * 
     * @param context 
     * @param select 
     */
	public Delete(XQueryContext context, Expression select) {
		super(context, select, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
        
		Sequence inSeq = select.eval(contextSequence);
		
		//START trap Delete failure
        /* If we try and Delete a node at an invalid location,
         * trap the error in a context variable,
         * this is then accessible from xquery via. the context extension module - deliriumsky
         * TODO: This trapping could be expanded further - basically where XPathException is thrown from thiss class
         * TODO: Maybe we could provide more detailed messages in the trap, e.g. couldnt delete node `xyz` into `abc` becuase... this would be nicer for the end user of the xquery application 
         */
        if (!Type.subTypeOf(inSeq.getItemType(), Type.NODE)) 
        {
        	//Indicate the failure to perform this update by adding it to the sequence in the context variable XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR
        	ValueSequence prevUpdateErrors = null;
        	
        	XPathException xpe = new XPathException(getASTNode(), Messages.getMessage(Error.UPDATE_SELECT_TYPE));
        	Object ctxVarObj = context.getXQueryContextVar(XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR);
        	if(ctxVarObj == null)
        	{
        		prevUpdateErrors = new ValueSequence();
        	}
        	else
        	{
        		prevUpdateErrors = (ValueSequence)XPathUtil.javaObjectToXPath(ctxVarObj, context);
        	}
        	prevUpdateErrors.add(new StringValue(xpe.getMessage()));
			context.setXQueryContextVar(XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR, prevUpdateErrors);
			
        	if(!inSeq.isEmpty())
        		throw xpe;	//TODO: should we trap this instead of throwing an exception - deliriumsky?
        }
        //END trap Delete failure
        
		if (!inSeq.isEmpty()) {
            //start a transaction
            Txn transaction = getTransaction();
    		try {
    			NotificationService notifier = context.getBroker().getBrokerPool().getNotificationService();
                StoredNode[] ql = selectAndLock(transaction, inSeq.toNodeSet());
                IndexListener listener = new IndexListener(ql);
                NodeImpl parent;
                for (int i = 0; i < ql.length; i++) {
                    StoredNode node = ql[i];
                    DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
                    if (!doc.getPermissions().validate(context.getUser(),
                            Permission.UPDATE)) {
                        //transact.abort(transaction);    
                        throw new XPathException(getASTNode(), "permission to update document denied");
                    }
                    doc.getMetadata().setIndexListener(listener);
                    
                    //update the document
                    parent = (StoredNode) node.getParentNode();
                    if (parent==null) {
                        LOG.debug("Cannot remove the document element (no parent node)");
                        throw new XPathException(getASTNode(),
                                "It is not possible to remove the document element.");

                    } else if (parent.getNodeType() != Node.ELEMENT_NODE) {
                        LOG.debug("parent = " + parent.getNodeType() + "; " + parent.getNodeName());
                        //transact.abort(transaction);
                        throw new XPathException(getASTNode(),
                                "you cannot remove the document element. Use update "
                                        + "instead");
                    } else {
                        parent.removeChild(transaction, node);
                    }
                    
                    doc.getMetadata().clearIndexListener();
                    doc.getMetadata().setLastModified(System.currentTimeMillis());
                    modifiedDocuments.add(doc);
                    context.getBroker().storeXMLResource(transaction, doc);
                    notifier.notifyUpdate(doc, UpdateListener.UPDATE);
                }
                checkFragmentation(transaction, modifiedDocuments);
                finishTriggers(transaction);
                //commit the transaction
                commitTransaction(transaction);
            } catch (EXistException e) {
                //transact.abort(transaction);
                throw new XPathException(getASTNode(), e.getMessage(), e);
    		} catch (PermissionDeniedException e) {
                //transact.abort(transaction);
                throw new XPathException(getASTNode(), e.getMessage(), e);
    		} catch (LockException e) {
                //transact.abort(transaction);
                throw new XPathException(getASTNode(), e.getMessage(), e);
    		} finally {
                unlockDocuments();
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);
        
        return Sequence.EMPTY_SEQUENCE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
	 */
	public void dump(ExpressionDumper dumper) {
		// TODO Auto-generated method stub

	}
	
	public String toString() {
		return "'Delete' string representation";
	}	

}
