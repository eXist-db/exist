/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.update;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
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
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;

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
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		if (contextItem != null)
			{contextSequence = contextItem.toSequence();}
		final Sequence inSeq = select.eval(contextSequence);
		if (inSeq.isEmpty())
			{return Sequence.EMPTY_SEQUENCE;}
				
		//START trap Replace failure
        /* If we try and Replace a node at an invalid location,
         * trap the error in a context variable,
         * this is then accessible from xquery via. the context extension module - deliriumsky
         * TODO: This trapping could be expanded further - basically where XPathException is thrown from thiss class
         * TODO: Maybe we could provide more detailed messages in the trap, e.g. couldnt replace node `xyz` into `abc` becuase... this would be nicer for the end user of the xquery application 
         */
        if (!Type.subTypeOf(inSeq.getItemType(), Type.NODE)) 
        {
        	//Indicate the failure to perform this update by adding it to the sequence in the context variable XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR
        	ValueSequence prevUpdateErrors = null;
        	
        	final XPathException xpe = new XPathException(this, Messages.getMessage(Error.UPDATE_SELECT_TYPE));
        	final Object ctxVarObj = context.getXQueryContextVar(XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR);
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
        		{throw xpe;}	//TODO: should we trap this instead of throwing an exception - deliriumsky?
        }
        //END trap Replace failure
		
		Sequence contentSeq = value.eval(contextSequence);
		if (contentSeq.isEmpty())
			{throw new XPathException(this, Messages.getMessage(Error.UPDATE_EMPTY_CONTENT));}
		context.pushInScopeNamespaces();
        contentSeq = deepCopy(contentSeq);
        
        //start a transaction
        final Txn transaction = getTransaction();
        try {
            final StoredNode ql[] = selectAndLock(transaction, inSeq);
            final IndexListener listener = new IndexListener(ql);
            final NotificationService notifier = context.getBroker().getBrokerPool().getNotificationService();
            Item temp;
            TextImpl text;
            AttrImpl attribute;
            ElementImpl parent;
            for (int i = 0; i < ql.length; i++) {
                final StoredNode node = ql[i];
                final DocumentImpl doc = (DocumentImpl)node.getOwnerDocument();
                if (!doc.getPermissions().validate(context.getUser(), Permission.WRITE)) {
                        throw new PermissionDeniedException("User '" + context.getSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                }
                doc.getMetadata().setIndexListener(listener);
                
                //update the document
                parent = (ElementImpl) node.getParentStoredNode();
                if (parent == null)
                    {throw new XPathException(this, "The root element of a document can not be replaced with 'update replace'. " +
                            "Please consider removing the document or use 'update value' to just replace the children of the root.");}
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        temp = contentSeq.itemAt(0);
						if (!Type.subTypeOf(temp.getType(), Type.NODE))
							{throw new XPathException(this,
									Messages.getMessage(Error.UPDATE_REPLACE_ELEM_TYPE,
											Type.getTypeName(temp.getType())));}
                        parent.replaceChild(transaction, ((NodeValue)temp).getNode(), node);
                        break;
                    case Node.TEXT_NODE: 
                        text = new TextImpl(contentSeq.getStringValue());
                        text.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, text);
                        break;
                    case Node.ATTRIBUTE_NODE:
                        final AttrImpl attr = (AttrImpl) node;
                        attribute = new AttrImpl(attr.getQName(), contentSeq.getStringValue(), context.getBroker().getBrokerPool().getSymbols());
                        attribute.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, attribute);
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }
                doc.getMetadata().clearIndexListener();
                doc.getMetadata().setLastModified(System.currentTimeMillis());
                modifiedDocuments.add(doc);
                context.getBroker().storeXMLResource(transaction, doc);
                notifier.notifyUpdate(doc, UpdateListener.UPDATE);
            }
            finishTriggers(transaction);
            //commit the transaction
            commitTransaction(transaction);
        } catch (final LockException e) {
            abortTransaction(transaction);
            throw new XPathException(this, e.getMessage(), e);
		} catch (final PermissionDeniedException e) {
            abortTransaction(transaction);
            throw new XPathException(this, e.getMessage(), e);
		} catch (final EXistException e) {
            abortTransaction(transaction);
            throw new XPathException(this, e.getMessage(), e);
        } catch (final TriggerException e) {
            abortTransaction(transaction);
            throw new XPathException(this, e.getMessage(), e);
		} finally {
            unlockDocuments();
            closeTransaction(transaction);
            context.popInScopeNamespaces();
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);}
        
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
	
	public String toString() {
		final StringBuilder result = new StringBuilder();
		result.append("update replace ");		
		result.append(select.toString());
		result.append(" with ");
		result.append(value.toString());
		return result.toString();
	}	
}
