/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.update;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.DocumentTriggers;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.NodeHandle;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;

/**
 * @author wolf
 *
 */
public abstract class Modification extends AbstractExpression
{

    protected final static Logger LOG = LogManager.getLogger(Modification.class);

    /**
     * How many times an update expression is evaluated and validated before the evaluation and the modification are
     * serialized, see {@link #evaluateAndModify(Evaluator, Modifier)}.
     */
    static final int OPTIMISTIC_ATTEMPTS = 3;

    /**
     * How many times a serialized update expression is evaluated and validated before it modifies the nodes
     * without validation, see {@link #evaluateAndModify(Evaluator, Modifier)}.
     */
    static final int SERIALIZED_ATTEMPTS = 10;

    /**
     * How many milliseconds a serialized update expression waits, at most, for the update expressions which locked
     * their documents before it to finish, see {@link #evaluateAndModify(Evaluator, Modifier)}.
     */
    static final int SERIALIZED_WAIT_MS = 10_000;

    protected final Expression select;
    protected final Expression value;

    protected ManagedLocks<ManagedDocumentLock> lockedDocumentsLocks;

    /**
     * True while this expression is counted in {@link BrokerPool#getUpdateExpressionsModifying()}.
     */
    private boolean modifying;
    protected MutableDocumentSet modifiedDocuments = new DefaultDocumentSet();
    protected final Int2ObjectMap<DocumentTrigger> triggers;

    public Modification(XQueryContext context, Expression select, Expression value) {
        super(context);
        this.select = select;
        this.value = value;
        this.triggers = new Int2ObjectOpenHashMap<>();
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EMPTY_SEQUENCE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#returnsType()
     */
    public int returnsType() {
        return Type.EMPTY_SEQUENCE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        select.resetState(postOptimization);
        if (value != null) {
            value.resetState(postOptimization);
        }
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        select.accept(visitor);
        if (value != null) {
            value.accept(visitor);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression, int)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        contextInfo.addFlag(IN_UPDATE);
        select.analyze(contextInfo);
        if (value != null) {
            value.analyze(contextInfo);
        }
    }

    /**
     * What an update expression evaluated: the nodes to modify, and what to modify them with.
     *
     * @param nodes the selected nodes
     * @param with what the nodes are modified with, or null if the expression has no value
     * @param <T> the type of what the nodes are modified with
     */
    protected record Selection<T>(Sequence nodes, @Nullable T with) {
    }

    /**
     * Evaluates an update expression's selection and value, without any lock held.
     *
     * @param <T> the type of what the selected nodes are modified with
     */
    @FunctionalInterface
    protected interface Evaluator<T> {
        /**
         * @return the selection, or null if there is nothing to modify
         * @throws XPathException if the evaluation fails
         */
        @Nullable Selection<T> evaluate() throws XPathException;
    }

    /**
     * Modifies the selected nodes, with their documents locked.
     *
     * @param <T> the type of what the selected nodes are modified with
     */
    @FunctionalInterface
    protected interface Modifier<T> {
        void modify(Txn transaction, StoredNode[] nodes, @Nullable T with) throws XPathException, EXistException, PermissionDeniedException, LockException, TriggerException;
    }

    /**
     * Evaluates the expression and modifies the nodes it selects, so that the nodes and the value that are modified
     * are those of the documents as they are when they are modified.
     *
     * The selection and the value are evaluated without any lock held, as evaluation takes Collection locks, and
     * a Collection lock requested while holding a Document lock can deadlock (see #6774). After the documents to
     * modify are locked, the evaluation is validated against the WRITE_LOCKs acquired on them by other threads since
     * it started (see {@link LockManager#stampDocumentWrites()}): if another thread acquired one, they may have changed
     * after they were read, so the locks are released and the expression is evaluated again. Nothing has been written
     * to the transaction at that point. Values read from other documents than those modified are not validated.
     *
     * After {@link #OPTIMISTIC_ATTEMPTS} attempts, or when Document WRITE_LOCKs are not counted because path locks
     * are used for them, the evaluation and the modification are serialized against other update expressions by
     * holding the {@link org.exist.storage.BrokerPool#getGlobalUpdateLock()} throughout. That lock is never taken
     * while the thread holds a Document WRITE_LOCK, for example in a trigger fired by another modification, so it
     * adds no lock-order edge: then the modification proceeds as before validation existed.
     *
     * No other update expression can lock documents while the global update lock is held, but those which locked
     * them before may still be modifying, so a serialized evaluation first waits for them to finish (see
     * {@link BrokerPool#getUpdateExpressionsModifying()}), for up to {@link #SERIALIZED_WAIT_MS} ms. Then only another
     * kind of write, such as storing a document, can invalidate it: it is validated, and evaluated again if invalid,
     * up to {@link #SERIALIZED_ATTEMPTS} times, after which, or if it can't be validated, the modification proceeds
     * without validation, as before validation existed.
     *
     * @param evaluator evaluates the selection and the value
     * @param modifier modifies the selected nodes
     * @param <T> the type of what the selected nodes are modified with
     *
     * @throws XPathException if the evaluation or the modification fails
     */
    protected <T> void evaluateAndModify(final Evaluator<T> evaluator, final Modifier<T> modifier) throws XPathException {
        final Attempts attempts = new Attempts(this, context.getBroker().getBrokerPool());
        try {
            boolean done;
            do {
                done = attempt(attempts, evaluator, modifier);
            } while (!done);
        } catch (final LockException | EXistException | PermissionDeniedException | TriggerException e) {
            throw new XPathException(this, e.getMessage(), e);
        } finally {
            attempts.closeTransaction();
            unlockDocuments();
            attempts.releaseGlobalUpdateLock();
        }
    }

    /**
     * Evaluates the expression, and modifies the nodes it selects if the evaluation is valid, or can't be repeated.
     *
     * The evaluation is repeated if it is invalid, retryable, and wrote nothing itself, as evaluating it again would
     * repeat its own writes. That includes an evaluation which failed, or selected nothing, as it may have read
     * nodes that another thread was replacing.
     *
     * @return true if done, or false if the expression must be evaluated again
     */
    private <T> boolean attempt(final Attempts attempts, final Evaluator<T> evaluator, final Modifier<T> modifier) throws XPathException, LockException, EXistException, PermissionDeniedException, TriggerException {
        final LockManager lockManager = attempts.lockManager;
        final LockManager.DocumentWriteStamp stamp = attempts.start();
        final long ownAtStamp = lockManager.documentWriteLocksAcquiredByCurrentThread();

        final Selection<T> selection;
        try {
            selection = evaluator.evaluate();
        } catch (final XPathException e) {
            if (attempts.isRetryable() && lockManager.documentWriteLocksAcquiredByCurrentThread() == ownAtStamp
                    && !lockManager.validateDocumentWrites(stamp)) {
                LOG.debug("Evaluating {} again after a concurrent modification: {}", this, e.getMessage());
                return false;
            }
            throw e;
        }
        final boolean repeatable = attempts.isRetryable() && lockManager.documentWriteLocksAcquiredByCurrentThread() == ownAtStamp;

        if (selection == null) {
            if (repeatable && !lockManager.validateDocumentWrites(stamp)) {
                LOG.debug("Evaluating {} again after a concurrent modification: nothing was selected", this);
                return false;
            }
            attempts.commitTransaction();
            return true;
        }

        final Txn transaction = attempts.transaction(this::getTransaction);
        final DocumentSet documents = selection.nodes().getDocumentSet();
        lockDocuments(documents, attempts.nested);

        if (!lockManager.validateDocumentWrites(stamp, documentPaths(documents))) {
            if (repeatable) {
                unlockDocuments();
                LOG.debug("Evaluating {} again after a concurrent modification", this);
                return false;
            }
            LOG.debug("Modifying with {} without validation: a Document was modified while it was evaluated", this);
        }

        final StoredNode[] nodes = prepareTriggers(transaction, selection.nodes());
        modifier.modify(transaction, nodes, selection.with());
        finishTriggers(transaction);
        transaction.commit();
        return true;
    }

    private static List<XmldbURI> documentPaths(final DocumentSet documents) {
        final List<XmldbURI> paths = new ArrayList<>(documents.getDocumentCount());
        for (final Iterator<DocumentImpl> iterator = documents.getDocumentIterator(); iterator.hasNext(); ) {
            paths.add(iterator.next().getURI());
        }
        return paths;
    }

    /**
     * The attempts of one evaluation of an update expression, see {@link #evaluateAndModify(Evaluator, Modifier)}.
     */
    private static final class Attempts {
        private final Expression expression;
        private final BrokerPool pool;
        private final LockManager lockManager;
        private final boolean nested;
        private boolean serialized;
        private int optimisticAttempts;
        private int serializedAttempts;
        private boolean retryable;
        @Nullable private Txn transaction;

        Attempts(final Expression expression, final BrokerPool pool) {
            this.expression = expression;
            this.pool = pool;
            this.lockManager = pool.getLockManager();
            this.nested = lockManager.currentThreadMayHoldDocumentWriteLock();
        }

        /**
         * Starts an attempt, serializing it if needed.
         *
         * @return the stamp to validate the attempt with, or null if it can't be validated
         */
        @Nullable LockManager.DocumentWriteStamp start() throws XPathException {
            if (nested) {
                retryable = false;
                return null;
            }
            if (!serialized && optimisticAttempts < OPTIMISTIC_ATTEMPTS) {
                final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
                if (stamp != null) {
                    optimisticAttempts++;
                    retryable = true;
                    return stamp;
                }
            }
            if (!serialized) {
                pool.getGlobalUpdateLock().lock();
                serialized = true;
                awaitUpdateExpressionsModifying();
            }
            final LockManager.DocumentWriteStamp stamp = lockManager.stampDocumentWrites();
            retryable = ++serializedAttempts < SERIALIZED_ATTEMPTS && stamp != null;
            return stamp;
        }

        /**
         * Returns true if the current attempt may be repeated.
         *
         * @return true if the current attempt may be repeated
         */
        boolean isRetryable() {
            return retryable;
        }

        /**
         * Waits, for up to {@link #SERIALIZED_WAIT_MS} ms, until no update expression is modifying documents.
         */
        private void awaitUpdateExpressionsModifying() throws XPathException {
            final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SERIALIZED_WAIT_MS);
            while (pool.getUpdateExpressionsModifying().get() > 0) {
                if (System.nanoTime() > deadline) {
                    LOG.warn("Evaluating {} while other update expressions are still modifying documents", expression);
                    return;
                }
                try {
                    Thread.sleep(1);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new XPathException(expression, "Interrupted while waiting to modify", e);
                }
            }
        }

        /**
         * Gets the transaction, beginning it on the first call. It is begun before the documents are locked, as
         * a system task, which starts only when no transaction is open, may need their locks.
         */
        Txn transaction(final Supplier<Txn> begin) {
            if (transaction == null) {
                transaction = begin.get();
            }
            return transaction;
        }

        void commitTransaction() throws TransactionException {
            if (transaction != null) {
                transaction.commit();
            }
        }

        void closeTransaction() {
            if (transaction != null) {
                transaction.close();
            }
        }

        void releaseGlobalUpdateLock() {
            if (serialized) {
                pool.getGlobalUpdateLock().unlock();
            }
        }
    }

    /**
     * Checks that the nodes can be modified, and fires the triggers before the update of their documents.
     *
     * @param transaction the transaction
     * @param nodes the nodes to modify, with their documents locked
     *
     * @return the nodes
     *
     * @throws TriggerException in case of error thrown by triggers
     * @throws XPathException if a node can't be modified
     */
    private StoredNode[] prepareTriggers(final Txn transaction, final Sequence nodes) throws XPathException, TriggerException {
        final StoredNode[] ql = new StoredNode[nodes.getItemCount()];
        for (int i = 0; i < ql.length; i++) {
            final Item item = nodes.itemAt(i);
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, "XQuery update expressions can only be applied to nodes. Got: " +
                    item.getStringValue());
            }
            final NodeValue nv = (NodeValue) item;
            if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                throw new XPathException(this, "XQuery update expressions can not be applied to in-memory nodes.");
            }
            final Node n = nv.getNode();
            if (n.getNodeType() == Node.DOCUMENT_NODE) {
                throw new XPathException(this, "Updating the document object is not allowed.");
            }
            ql[i] = (StoredNode) n;
            prepareTrigger(transaction, ql[i].getOwnerDocument());
        }
        return ql;
    }

    /**
     * Acquire a WRITE_LOCK on each of the documents, so that their nodes' positions can't change during the
     * modification.
     *
     * @param lockedDocuments the documents of the nodes to modify
     * @param nested true if the thread already holds a document WRITE_LOCK
     *
     * @throws LockException if a lock can't be acquired
     */
    private void lockDocuments(final DocumentSet lockedDocuments, final boolean nested) throws LockException {
        if (nested) {
            // the thread already holds a Document WRITE_LOCK, so taking the global update lock now could deadlock with a thread which holds it and waits for that Document
            lockedDocumentsLocks = lockedDocuments.lock(context.getBroker(), true);
            return;
        }
        final BrokerPool pool = context.getBroker().getBrokerPool();
        final java.util.concurrent.locks.Lock globalLock = pool.getGlobalUpdateLock();
        globalLock.lock();
        try {
            lockedDocumentsLocks = lockedDocuments.lock(context.getBroker(), true);
            pool.getUpdateExpressionsModifying().incrementAndGet();
            modifying = true;
        } finally {
            globalLock.unlock();
        }
    }

    protected Sequence deepCopy(Sequence inSeq) throws XPathException {
        context.pushDocumentContext();
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(this, builder);
        final Serializer serializer = context.getBroker().borrowSerializer();
        serializer.setReceiver(receiver);

        try {
            final Sequence out = new ValueSequence();
            for (final SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                if (item.getType() == Type.DOCUMENT) {
                    if (((NodeValue)item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final NodeHandle root = (NodeHandle) ((NodeProxy)item).getOwnerDocument().getDocumentElement();
                        item = new NodeProxy(this, root);
                    } else {
                        item = (Item)((Document)item).getDocumentElement();
                    }
                }
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    if (((NodeValue)item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final int last = builder.getDocument().getLastNode();
                        final NodeProxy p = (NodeProxy) item;
                        serializer.toReceiver(p, false, false);
                        if (p.getNodeType() == Node.ATTRIBUTE_NODE)
                            {item = builder.getDocument().getLastAttr();}
                        else
                            {item = builder.getDocument().getNode(last + 1);}
                    } else {
                        ((org.exist.dom.memtree.NodeImpl)item).deepCopy();
                    }
                }
                out.add(item);
            }
            return out;
        } catch(final SAXException | DOMException e) {
            throw new XPathException(this, e.getMessage(), e);
        } finally {
            context.getBroker().returnSerializer(serializer);
            context.popDocumentContext();
        }
    }

    protected void finishTriggers(Txn transaction) throws TriggerException {
        final Iterator<DocumentImpl> iterator = modifiedDocuments.getDocumentIterator();

        while(iterator.hasNext()) {
            final DocumentImpl doc = iterator.next();
            context.addModifiedDoc(doc);
            finishTrigger(transaction, doc);
        }
        
        triggers.clear();
    }

    /**
     * Release all acquired document locks.
     */
    protected void unlockDocuments() {
        if(lockedDocumentsLocks == null) {
            return;
        }

        modifiedDocuments.clear();

        //unlock documents
        lockedDocumentsLocks.close();
        lockedDocumentsLocks = null;

        if (modifying) {
            modifying = false;
            context.getBroker().getBrokerPool().getUpdateExpressionsModifying().decrementAndGet();
        }
    }

    public static void checkFragmentation(XQueryContext context, DocumentSet docs) throws EXistException, LockException {
        int fragmentationLimit = -1;
        final Object property = context.getBroker().getBrokerPool().getConfiguration().getProperty(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
        if (property != null) {
            fragmentationLimit = (Integer) property;
        }
        checkFragmentation(context, docs, fragmentationLimit);
    }

    /**
     * Check if any of the modified documents needs defragmentation.
     *
     * Defragmentation will take place if the number of split pages in the
     * document exceeds the limit defined in the configuration file.
     *
     * @param context current context
     * @param docs document set to check
     * @param splitCount number of page splits
     * @throws EXistException on general errors during defrag
     * @throws LockException in case locking failed
     */
    public static void checkFragmentation(XQueryContext context, DocumentSet docs, int splitCount) throws EXistException, LockException {
        final DBBroker broker = context.getBroker();
        final LockManager lockManager = broker.getBrokerPool().getLockManager();
        //if there is no batch update transaction, start a new individual transaction
        try(final Txn transaction = broker.continueOrBeginTransaction()) {
            for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
                final DocumentImpl next = i.next();
                if(next.getSplitCount() > splitCount) {
                    try(final ManagedDocumentLock nextLock = lockManager.acquireDocumentWriteLock(next.getURI())) {
                        broker.defragXMLResource(transaction, next);
                    }
                }
                broker.checkXMLResourceConsistency(next);
            }

            transaction.commit();
        }
    }

    /**
     * Fires the prepare function for the UPDATE_DOCUMENT_EVENT trigger for the Document doc
     *
     * @param transaction	The transaction
     * @param doc	The document to trigger for
     *
     * @throws TriggerException
     */
    private void prepareTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {

        final Collection col = doc.getCollection();
        final DBBroker broker = context.getBroker();

        final DocumentTrigger trigger = new DocumentTriggers(broker, transaction, col);

        //prepare the trigger
        trigger.beforeUpdateDocument(context.getBroker(), transaction, doc);
        triggers.put(doc.getDocId(), trigger);
    }

    /** Fires the finish function for UPDATE_DOCUMENT_EVENT for the documents trigger
     *
     * @param transaction	The transaction
     * @param doc	The document to trigger for
     *
     * @throws TriggerException
     */
    private void finishTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {
        //finish the trigger
        final DocumentTrigger trigger = triggers.get(doc.getDocId());
        if(trigger != null) {
            trigger.afterUpdateDocument(context.getBroker(), transaction, doc);
        }
    }

    /**
     * Gets the Transaction to use for the update (can be batch or individual)
     *
     * @return The transaction
     */
    protected Txn getTransaction() {
        return context.getBroker().continueOrBeginTransaction();
    }

    /**
     * Get's the parent of the node.
     *
     * @param node The node of which to retrieve the parent.
     *
     * @return the parent node, or null if not available
     */
    protected @Nullable Node getParent(@Nullable final Node node) {
        if (node == null) {
            return null;
        } else if (node instanceof Attr attr) {
            return attr.getOwnerElement();
        } else {
            return node.getParentNode();
        }
    }
}
