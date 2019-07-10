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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.persistent.TextImpl;
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
import org.w3c.dom.Node;

/**
 * @author wolf
 *
 */
public class Update extends Modification {

    private final static Logger LOG = LogManager.getLogger(Update.class);

    public Update(XQueryContext context, Expression select, Expression value) {
        super(context, select, value);
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

        final Sequence contentSeq = value.eval(contextSequence);
        if (contentSeq.isEmpty()) {
            throw new XPathException(this, Messages.getMessage(Error.UPDATE_EMPTY_CONTENT));
        }

        final Sequence inSeq = select.eval(contextSequence);

        //START trap Update failure
        /* If we try and Update a node at an invalid location,
         * trap the error in a context variable,
         * this is then accessible from xquery via. the context extension module - deliriumsky
         * TODO: This trapping could be expanded further - basically where XPathException is thrown from thiss class
         * TODO: Maybe we could provide more detailed messages in the trap, e.g. couldnt update node `xyz` into `abc` becuase... this would be nicer for the end user of the xquery application
         */
        if (!Type.subTypeOf(inSeq.getItemType(), Type.NODE))
        {
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
        //END trap Update failure
        
        if (!inSeq.isEmpty()) {          
            context.pushInScopeNamespaces();
            //start a transaction
            try (final Txn transaction = getTransaction()) {
                final NotificationService notifier = context.getBroker().getBrokerPool().getNotificationService();

                final StoredNode ql[] = selectAndLock(transaction, inSeq);
                for (final StoredNode node : ql) {
                    final DocumentImpl doc = node.getOwnerDocument();
                    if (!doc.getPermissions().validate(context.getSubject(),
                        Permission.WRITE)) {
                        throw new XPathException(this, "User '" + context.getSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                    }

                    //update the document
                    switch (node.getNodeType()) {
                        case Node.ELEMENT_NODE:
                            final NodeListImpl content = new NodeListImpl();
                            for (final SequenceIterator j = contentSeq.iterate(); j.hasNext(); ) {
                                final Item next = j.nextItem();
                                if (Type.subTypeOf(next.getType(), Type.NODE)) {
                                    content.add(((NodeValue) next).getNode());
                                } else {
                                    final TextImpl text = new TextImpl(next.getStringValue());
                                    content.add(text);
                                }
                            }
                            ((ElementImpl) node).update(transaction, content);
                            break;

                        case Node.TEXT_NODE:
                            final ElementImpl textParent = (ElementImpl) node.getParentNode();
                            final TextImpl text = new TextImpl(contentSeq.getStringValue());
                            text.setOwnerDocument(doc);
                            textParent.updateChild(transaction, node, text);
                            break;

                        case Node.ATTRIBUTE_NODE:
                            final ElementImpl attrParent = (ElementImpl) ((Attr)node).getOwnerElement();
                            if (attrParent == null) {
                                LOG.warn("parent node not found for " + node.getNodeId());
                                break;
                            }
                            final AttrImpl attr = (AttrImpl) node;
                            final AttrImpl attribute = new AttrImpl(attr.getQName(), contentSeq.getStringValue(), context.getBroker().getBrokerPool().getSymbols());
                            attribute.setOwnerDocument(doc);
                            attrParent.updateChild(transaction, node, attribute);
                            break;

                        default:
                            throw new XPathException(this, "unsupported node-type");
                    }
                    doc.getMetadata().setLastModified(System.currentTimeMillis());
                    modifiedDocuments.add(doc);
                    context.getBroker().storeXMLResource(transaction, doc);
                    notifier.notifyUpdate(doc, UpdateListener.UPDATE);
                }
                finishTriggers(transaction);
                //commit the transaction
                transaction.commit();
            } catch (final LockException | EXistException | TriggerException e) {
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

    public String toString() {
        return "update value" + select.toString() + " with " + value.toString();
	}
}
