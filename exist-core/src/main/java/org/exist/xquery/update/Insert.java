/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.xquery.update;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.persistent.StoredNode;
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
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Attr;
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

    public Insert(XQueryContext context, Expression select, Expression value, int mode) {
        super(context, select, value);
        this.mode = mode;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        Sequence contentSeq = value.eval(contextSequence);
        if (contentSeq.isEmpty()) {
            throw new XPathException(this, Messages.getMessage(Error.UPDATE_EMPTY_CONTENT));
        }
        
        final Sequence inSeq = select.eval(contextSequence);      
        
        //START trap Insert failure
        /* If we try and Insert a node at an invalid location,
         * trap the error in a context variable,
         * this is then accessible from xquery via. the context extension module - deliriumsky
         * TODO: This trapping could be expanded further - basically where XPathException is thrown from thiss class
         * TODO: Maybe we could provide more detailed messages in the trap, e.g. couldnt insert node `xyz` into `abc` becuase... this would be nicer for the end user of the xquery application 
         */
        if (!Type.subTypeOf(inSeq.getItemType(), Type.NODE)) {
            //Indicate the failure to perform this update by adding it to the sequence in the context variable XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR
            ValueSequence prevUpdateErrors = null;

            final XPathException xpe = new XPathException(this, Messages.getMessage(Error.UPDATE_SELECT_TYPE));
            final Object ctxVarObj = context.getAttribute(XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR);
            if(ctxVarObj == null) {
                prevUpdateErrors = new ValueSequence();
            } else {
                prevUpdateErrors = (ValueSequence)XPathUtil.javaObjectToXPath(ctxVarObj, context);
            }
            prevUpdateErrors.add(new StringValue(xpe.getMessage()));
            context.setAttribute(XQueryContext.XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR, prevUpdateErrors);

            if(!inSeq.isEmpty()) {
                //TODO: should we trap this instead of throwing an exception - deliriumsky?
                throw xpe;
            }
        }
        //END trap Insert failure
        
        if (!inSeq.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found: " + inSeq.getItemCount() + " nodes");
            }

            context.pushInScopeNamespaces();
            contentSeq = deepCopy(contentSeq);

            //start a transaction
            try (final Txn transaction = getTransaction()) {
                final StoredNode[] ql = selectAndLock(transaction, inSeq);
                final NotificationService notifier = context.getBroker().getBrokerPool().getNotificationService();
                final NodeList contentList = seq2nodeList(contentSeq);
                for (final StoredNode node : ql) {
                    final DocumentImpl doc = node.getOwnerDocument();
                    if (!doc.getPermissions().validate(context.getSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("User '" + context.getSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                    }

                    //update the document
                    if (mode == INSERT_APPEND) {
                        node.appendChildren(transaction, contentList, -1);
                    } else {
                        final NodeImpl parent = (NodeImpl) getParent(node);
                        switch (mode) {
                            case INSERT_BEFORE:
                                parent.insertBefore(transaction, contentList, node);
                                break;
                            case INSERT_AFTER:
                                parent.insertAfter(transaction, contentList, node);
                                break;
                        }
                    }
                    doc.getMetadata().setLastModified(System.currentTimeMillis());
                    modifiedDocuments.add(doc);
                    context.getBroker().storeXMLResource(transaction, doc);
                    notifier.notifyUpdate(doc, UpdateListener.UPDATE);
                }
                finishTriggers(transaction);
                //commit the transaction
                transaction.commit();
            } catch (final PermissionDeniedException | EXistException | LockException | TriggerException e) {
                throw new XPathException(this, e.getMessage(), e);
            } finally {
                unlockDocuments();
                context.popInScopeNamespaces();
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);
        }
        
        return Sequence.EMPTY_SEQUENCE;
    }

    private NodeList seq2nodeList(Sequence contentSeq) throws XPathException {
        final NodeListImpl nl = new NodeListImpl();
        for (final SequenceIterator i = contentSeq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                final NodeValue val = (NodeValue) item;
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

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("update insert ");
        result.append(value.toString());
        switch (mode) {
            case INSERT_AFTER:
                result.append(" following ");
                break;
            case INSERT_BEFORE:
                result.append(" preceding ");
                break;
            case INSERT_APPEND:
                result.append(" into ");
                break;
        }
        result.append(select.toString());
        return result.toString();
    }
}
