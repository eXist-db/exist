/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.test;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.function.QuadFunction7E;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.TransactionTestDSL.BiTransactionScheduleBuilder.BiTransactionScheduleBuilderOperation;
import org.exist.test.TransactionTestDSL.TransactionScheduleBuilder.NilOperation;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static org.exist.dom.persistent.DocumentImpl.XML_FILE;
import static org.exist.util.ThreadUtils.nameInstanceThread;
import static org.exist.util.ThreadUtils.newInstanceSubThreadGroup;

/**
 * A DSL for describing a schedule of
 * transaction operations upon the database.
 *
 * A type-safe builder pattern is provided
 * for constructing the schedule. Once
 * the schedule is build a scheduler
 * can execute it upon the database
 * and return the results.
 *
 * The DSL uses recursive types
 * in a similar way to a typed heterogeneous
 * list (such as Shapeless's HList) to ensure
 * that the each operation in the schedule
 * receives the correct input type, i.e.
 * the output type of the previous operation.
 * At the cost of complexity in implementing
 * the DSL, the recursive typing makes use of
 * the DSL by the user much simpler and safer.
 *
 * The recursive type implementation was
 * inspired by <a href="https://apocalisp.wordpress.com/2008/10/23/heterogeneous-lists-and-the-limits-of-the-java-type-system/">https://apocalisp.wordpress.com/2008/10/23/heterogeneous-lists-and-the-limits-of-the-java-type-system/</a>.
 *
 * Example usage for creating a schedule of
 * two transactions, where each will execute in
 * its own thread but operationally linear
 * according to the schedule:
 *
 * <pre>
 *
 * import static org.exist.test.TransactionTestDSL.TransactionOperation.*;
 * import static org.exist.test.TransactionTestDSL.TransactionScheduleBuilder.biSchedule;
 *
 * {@code @Test}
 * public void getDocuments() throws ExecutionException, InterruptedException {
 *   final String documentUri = "/db/test/hamlet.xml";
 *
 *   final Tuple2{@code <DocumentImpl, DocumentImpl>} result = biSchedule()
 *       .firstT1(getDocument(documentUri))
 *                                            .andThenT2(getDocument(documentUri))
 *       .andThenT1(commit())
 *                                            .andThenT2(commit())
 *       .build()
 *   .execute(existEmbeddedServer.getBrokerPool());
 *
 *   assertNotNull(result);
 *   assertNotNull(result._1);
 *   assertNotNull(result._2);
 *
 *   assertEquals(documentUri, result._1.getURI().getCollectionPath());
 *   assertEquals(documentUri, result._2.getURI().getCollectionPath());
 * }
 *
 * </pre>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface TransactionTestDSL {

    /**
     * A Transaction Schedule builder.
     *
     * Enables us to build a schedule of operations to be executed
     * within one or more transactions.
     *
     * @param <A> A recursive type, holds the type of the previously scheduled
     *           operation(s).
     */
    interface TransactionScheduleBuilder<A extends TransactionScheduleBuilder<A>> {
        NilOperation nilOperation = new NilOperation();

        static NilOperation nil() {
            return nilOperation;
        }

        final class NilOperation implements TransactionScheduleBuilder<NilOperation> {
            private NilOperation() {}
        }

        /**
         * Creates a Schedule Builder factory for two transactions T1 and T2.
         *
         * @return a Schedule Builder factory for two transactions
         */
        static BiTransactionScheduleBuilderFactory biSchedule() {
            return BiTransactionScheduleBuilderFactory.getInstance();
        }
    }

    /**
     * A schedule builder factory for two transactions T1 and T2.
     *
     * Responsible for creating a Schedule Builder which is initialized
     * to the first transaction state.
     */
    class BiTransactionScheduleBuilderFactory {
        private static final BiTransactionScheduleBuilderFactory INSTANCE = new BiTransactionScheduleBuilderFactory();

        private BiTransactionScheduleBuilderFactory() { }

        private static BiTransactionScheduleBuilderFactory getInstance() {
            return INSTANCE;
        }

        /**
         * Constructs a Schedule Builder for two transactions T1 and T2,
         * whose first schedule is an operation with T1.
         *
         * @param <U> The state returned by the first operation with T1.
         *
         * @param stateTransform The initial state for T1.
         *
         * @return the schedule builder.
         */
        public <U> BiTransactionScheduleBuilderOperation<Void, U, Void, Void, NilOperation> firstT1(final TransactionOperation<Void, U> stateTransform) {
            return BiTransactionScheduleBuilderOperation.first(Left(stateTransform));
        }

        /**
         * Constructs a Schedule Builder for two transactions T1 and T2,
         * whose first schedule is an operation with T2.
         *
         * @param <U> The state returned by the first operation with T2.
         *
         * @param stateTransform The initial state for T2.
         *
         * @return the schedule builder.
         */
        public <U> BiTransactionScheduleBuilderOperation<Void, Void, Void, U, NilOperation> firstT2(final TransactionOperation<Void, U> stateTransform) {
            return BiTransactionScheduleBuilderOperation.first(Right(stateTransform));
        }
    }

    /**
     * A Schedule for two transactions T1 and T2.
     *
     * @param <A> A recursive type, holds the type of the previously scheduled
     *           operation(s).
     */
    abstract class BiTransactionScheduleBuilder <A extends TransactionScheduleBuilder<A>> implements TransactionScheduleBuilder<A> {

        /**
         * Describes a scheduled operation on transaction T1 and/or T2.
         *
         * @param <T1> The type of the state held for T1 before the
         *            operation has executed
         * @param <U1> The type of the state held for T1 after the
         *            operation has executed
         * @param <T2> The type of the state held for T2 before the
         *            operation has executed
         * @param <U2> The type of the state held for T2 after the
         *            operation has executed
         *
         * @param <B> A recursive type, holds the type of the previously scheduled
         *           operation(s).
         */
        public static class BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B extends TransactionScheduleBuilder<B>> extends BiTransactionScheduleBuilder<BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B>> {

            // was this created by a transformation on T1 (if not then T2)
            private final boolean operationOnT1;

            // the previous schedule builder operation
            private final B previous;

            // transformations on the transactions
            private final TransactionOperation<T1, U1> t1_state;
            private final TransactionOperation<T2, U2> t2_state;

            // latches, used to switch scheduling between t1 and t2
            private final NamedCountDownLatch t1WaitLatch;
            private final NamedCountDownLatch t2WaitLatch;

            // just a counter to help us name our latches for debugging
            private static int countDownLatchNum = 0;

            /**
             * Constructs an initial schedule builder.
             *
             * @param <T1> The type of the state held for T1 before the
             *            <pre>operation</pre> has executed
             * @param <U1> The type of the state held for T1 after the
             *            <pre>operation</pre> has executed
             * @param <T2> The type of the state held for T2 before the
             *            <pre>operation</pre> has executed
             * @param <U2> The type of the state held for T2 after the
             *            <pre>operation</pre> has executed
             *
             * @param operation An operation on either Transaction T1 or T2
             *
             * @return the builder
             */
            public static <T1, U1, T2, U2> BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, NilOperation> first(final Either<TransactionOperation<T1, U1>, TransactionOperation<T2, U2>> operation) {
                if(operation.isLeft()) {
                    final TransactionOperation<T1, T1> initial_t1_state = (broker, txn, listener, t1) -> {
                        listener.event("Initialized T1: Starting schedule execution with T1...");
                        return t1;
                    };

                    // as we start with t1, we add a function that pauses t2, awaiting countdown latch signalled from t1
                    final NamedCountDownLatch t2WaitForT1 = new NamedCountDownLatch("t2WaitForT1-" + (++countDownLatchNum), 1);
                    final TransactionOperation<T2, U2> initial_t2_state = (broker, txn, listener, t2) -> {
                        // instruct t2 to wait
                        listener.event("Initialized T2: Instructing T2 to wait for T1 (" + t2WaitForT1.getName() + ")...");
                        t2WaitForT1.await();
                        return null;
                    };

                    return new BiTransactionScheduleBuilderOperation<>(true, TransactionScheduleBuilder.nil(), initial_t1_state.andThen(operation.left().get()), initial_t2_state, null, t2WaitForT1);

                } else {
                    final TransactionOperation<T2, T2> initial_t2_state = (broker, txn, listener, t2) -> {
                        listener.event("Initialized T2: Starting schedule execution with T2...");
                        return t2;
                    };


                    // as we start with t2, we add a function that pauses t1, awaiting countdown latch signalled from t2
                    final NamedCountDownLatch t1WaitForT2 = new NamedCountDownLatch("t1WaitForT2-" + (++countDownLatchNum), 1);
                    final TransactionOperation<T1, U1> initial_t1_state = (broker, txn, listener, t1) -> {
                        // instruct t1 to wait
                        listener.event("Initialized T1: Instructing T1 to wait for T2 (" + t1WaitForT2.getName() + ")...");
                        t1WaitForT2.await();
                        return null;
                    };

                    return new BiTransactionScheduleBuilderOperation<>(false, TransactionScheduleBuilder.nil(), initial_t1_state, initial_t2_state.andThen(operation.right().get()), t1WaitForT2, null);
                }
            }

            private BiTransactionScheduleBuilderOperation(final boolean operationOnT1, final B previous, final TransactionOperation<T1, U1> t1_state, final TransactionOperation<T2, U2> t2_state) {
                this(operationOnT1, previous, t1_state, t2_state, null, null);
            }

            private BiTransactionScheduleBuilderOperation(final boolean operationOnT1, final B previous, final TransactionOperation<T1, U1> t1_state, final TransactionOperation<T2, U2> t2_state, final NamedCountDownLatch t1WaitLatch, final NamedCountDownLatch t2WaitLatch) {
                this.operationOnT1 = operationOnT1;
                this.previous = previous;
                this.t1_state = t1_state;
                this.t2_state = t2_state;
                this.t1WaitLatch = t1WaitLatch;
                this.t2WaitLatch = t2WaitLatch;
            }

            /**
             * Utility getter to access the previous state.
             *
             * @return The previous schedule builder operation, or null
             *  if there was no previous operation.
             */
            public B previous() {
                return previous;
            }

            /**
             * Schedules the next operation on Transaction T1.
             *
             * @param <V1> The type of the state held for T1 after the
             *     <pre>t1Transformation</pre> has executed
             *
             * @param t1Transformation An operation to perform with T1
             *     on the current state held for T1, which yields a
             *     new state of type <pre>V1</pre>
             *
             * @return the schedule builder.
             */
            public <V1> BiTransactionScheduleBuilderOperation<T1, V1, T2, U2, BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B>> andThenT1(final TransactionOperation<U1, V1> t1Transformation) {
                if(operationOnT1) {
                    //continue executing t1
                    return new BiTransactionScheduleBuilderOperation<>(true, this, t1_state.andThen(t1Transformation), t2_state, t1WaitLatch, t2WaitLatch);

                } else {
                    // switch execution from t2 to t1

                    // we add a function that pauses t2 awaiting countdown latch signalled from t1
                    final NamedCountDownLatch t2WaitForT1 = new NamedCountDownLatch("t2WaitForT1-" + (++countDownLatchNum), 1);
                    final TransactionOperation<T2, U2> next_t2_state = t2_state
                            .andThen((broker, txn, listener, t2) -> {
                                // resume t1, by counting down the latch
                                if(t1WaitLatch != null) {
                                    listener.event("Releasing T1 from wait (" + t1WaitLatch.getName() + ")...");
                                    t1WaitLatch.countDown();
                                }
                                return t2;
                            })
                            .andThen((broker, txn, listener, t2) -> {
                                // instruct t2 to wait
                                listener.event("Instructing T2 to wait for T1 (" + t2WaitForT1.getName() + ")...");
                                t2WaitForT1.await();
                                return t2;
                            });

                    return new BiTransactionScheduleBuilderOperation<>(true, this, t1_state.andThen(t1Transformation), next_t2_state, t1WaitLatch, t2WaitForT1);
                }
            }

            /**
             * Schedules the next operation on Transaction T2.
             *
             * @param <V2> The type of the state held for T2 after the
             *     <pre>t2Transformation</pre> has executed
             *
             * @param t2Transformation An operation to perform with T2
             *     on the current state held for T2, which yields a
             *     new state of type <pre>V2</pre>
             *
             * @return the schedule builder.
             */
            public <V2> BiTransactionScheduleBuilderOperation<T1, U1, T2, V2, BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B>> andThenT2(final TransactionOperation<U2, V2> t2Transformation) {
                if(!operationOnT1) {
                    //continue executing t2
                    return new BiTransactionScheduleBuilderOperation<>(false, this, t1_state, t2_state.andThen(t2Transformation), t1WaitLatch, t2WaitLatch);

                } else {
                    // switch execution from t1 to t2

                    // we add a function that pauses t1 awaiting countdown latch signalled from t2
                    final NamedCountDownLatch t1WaitForT2 = new NamedCountDownLatch("t1WaitForT2-" + (++countDownLatchNum), 1);
                    final TransactionOperation<T1, U1> next_t1_state = t1_state
                            .andThen((broker, txn, listener, t1) -> {
                                // resume t2, by counting down the latch
                                if(t2WaitLatch != null) {
                                    listener.event("Releasing T2 from wait (" + t2WaitLatch.getName() + ")...");
                                    t2WaitLatch.countDown();
                                }
                                return t1;
                            })
                            .andThen((broker, txn, listener, t1) -> {
                                // instruct t1 to wait
                                listener.event("Instructing T1 to wait for T2 (" + t1WaitForT2.getName() + ")...");
                                t1WaitForT2.await();
                                return t1;
                            });

                    return new BiTransactionScheduleBuilderOperation<>(false, this, next_t1_state, t2_state.andThen(t2Transformation), t1WaitForT2, t2WaitLatch);
                }
            }

            /**
             * Constructs the final Transaction Schedule
             * from the builder.
             *
             * @return the transaction schedule.
             */
            public BiTransactionSchedule<T1, U1, T2, U2, BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B>> build() {

                // we must compose a final operation to countdown any last remaining latches between t1->t2, or t2->t1 scheduling transitions
                final TransactionOperation<T1, U1> final_t1_state = t1_state.andThen((broker, txn, listener, t1) -> {
                    if(t2WaitLatch != null && t2WaitLatch.getCount() == 1) {
                        listener.event("Final release of T2 from wait (" + t2WaitLatch.getName() + ")...");
                        t2WaitLatch.countDown();
                    }
                    return t1;
                });
                final TransactionOperation<T2, U2> final_t2_state = t2_state.andThen((broker, txn, listener, t2) -> {
                    if(t1WaitLatch != null && t1WaitLatch.getCount() == 1) {
                        listener.event("Final release of T1 from wait (" + t1WaitLatch.getName() + ")...");
                        t1WaitLatch.countDown();
                    }
                    return t2;
                });

                final BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B>> finalOperation =
                        new BiTransactionScheduleBuilderOperation<>(false /* value of this parameter here is not significant */, this, final_t1_state, final_t2_state, t1WaitLatch, t2WaitLatch);

                return new BiTransactionSchedule<>(finalOperation);
            }
        }
    }

    /**
     * A schedule of one or more transactions that may
     * be executed upon the database
     *
     * @param <U> The type of the result from executing the schedule.
     */
    interface TransactionSchedule<U> {

        /**
         * Execute the schedule on the database.
         *
         * @param brokerPool The database
         *
         * @return The result of executing the schedule.
         *
         * @throws ExecutionException if the execution fails.
         * @throws InterruptedException if the execution is interrupted.
         */
        default U execute(final BrokerPool brokerPool) throws ExecutionException, InterruptedException {
            return execute(brokerPool, NULL_SCHEDULE_LISTENER);
        }

        /**
         * Execute the schedule on the database.
         *
         * @param brokerPool The database
         * @param executionListener A listener which receives execution events.
         *
         * @return The result of executing the schedule.
         *
         * @throws ExecutionException if the execution fails.
         * @throws InterruptedException if the execution is interrupted.
         */
        U execute(final BrokerPool brokerPool, final ExecutionListener executionListener)
                throws ExecutionException, InterruptedException;
    }

    /**
     * A schedule of two transactions T1 and T2.
     *
     * Which are executed concurrently, each in their
     * own thread, but linearly according to the schedule.
     *
     * @param <T1> The initial type of the state held for T1 before the
     *             schedule is executed
     * @param <U1> The final type of the state held for T1 after the
     *             schedule has executed
     * @param <T2> The initial type of the state held for T2 before the
     *             schedule is executed
     * @param <U2> The final type of the state held for T2 after the
     *             schedule has executed
     *
     * @param <B> A recursive type, which enforces the types of all operations which
     *           which make up the schedule.
     */
    class BiTransactionSchedule<T1, U1, T2, U2, B extends TransactionScheduleBuilder<B>> implements TransactionSchedule<Tuple2<U1, U2>> {
        private final BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B> lastOperation;

        private BiTransactionSchedule(final BiTransactionScheduleBuilderOperation<T1, U1, T2, U2, B> lastOperation) {
            this.lastOperation = lastOperation;
        }

        // TODO(AR) enable the creation of transactions to be specified in the DSL just like in Granite, so we can show isolation (or not)!
        @Override
        public Tuple2<U1, U2> execute(final BrokerPool brokerPool, final ExecutionListener executionListener) throws ExecutionException, InterruptedException {
            Objects.requireNonNull(brokerPool);
            Objects.requireNonNull(executionListener);

            final ThreadGroup transactionsThreadGroup = newInstanceSubThreadGroup(brokerPool, "transactionTestDSL");

            // submit t1
            final ExecutorService t1ExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(transactionsThreadGroup, r, nameInstanceThread(brokerPool, "transaction-test-dsl.transaction-1-schedule")));
            final Future<U1> t1Result = t1ExecutorService.submit(() -> {
                try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                     final Txn txn = brokerPool.getTransactionManager().beginTransaction()) {
                    final U1 result = lastOperation.t1_state.apply(broker, txn, executionListener, null);
                    txn.commit();
                    return result;
                }
            });

            // submit t2
            final ExecutorService t2ExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(transactionsThreadGroup, r, nameInstanceThread(brokerPool, "transaction-test-dsl.transaction-2-schedule")));
            final Future<U2> t2Result = t2ExecutorService.submit(() -> {
                try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                     final Txn txn = brokerPool.getTransactionManager().beginTransaction()) {
                    final U2 result = lastOperation.t2_state.apply(broker, txn, executionListener, null);
                    txn.commit();
                    return result;
                }
            });

            try {

                U1 u1 = null;
                U2 u2 = null;
                while (true) {
                    if (t1Result.isDone()) {
                        u1 = t1Result.get();
                    }

                    if (t2Result.isDone()) {
                        u2 = t2Result.get();
                    }

                    if (t1Result.isDone() && t2Result.isDone()) {
                        return new Tuple2<>(u1, u2);
                    }

                    Thread.sleep(50);
                }
            } catch (final ExecutionException | InterruptedException e) {
                // if we get to here then t1Result or t2Result has thrown an exception

                // force shutdown of transaction threads

                t2ExecutorService.shutdownNow();
                t1ExecutorService.shutdownNow();

                //TODO(AR) rather than working with exceptions, it would be better to encapsulate them in a similar way to working on an empty sequence, e.g. could use Either<L,R>???

                throw e;
            }
        }
    }

    /**
     * A function which describes an operation on the database with a Transaction.
     *
     * You can think of this as a function <pre>f(T) -&gt; U</pre>
     * where the database and transaction are available to the
     * function <pre>f</pre>.
     *
     * @param <T> The initial state before the transaction operation.
     * @param <U> The state after the transaction operation.
     */
    @FunctionalInterface
    interface TransactionOperation<T, U>
            extends QuadFunction7E<DBBroker, Txn, ExecutionListener, T, U, EXistException, XPathException, PermissionDeniedException, LockException, TriggerException, IOException, InterruptedException> {

        /**
         * Get a document from the database.
         *
         * @param <T> The type of the state held for the transaction
         *     before this operation executes.
         *
         * @param uri The uri of the document.
         *
         * @return an operation which will retrieve the document from the database.
         */
        static <T> TransactionOperation<T, DocumentImpl> getDocument(final String uri) {
            return (broker, txn, listener, t) -> {
                listener.event("Getting document: " + uri);
                return (DocumentImpl)broker.getXMLResource(XmldbURI.create(uri));
            };
        }

        /**
         * Delete a document from the database.
         *
         * @param <T> The type of the document held for the transaction
         *     before this operation executes.
         *
         * @return an operation which will delete a document from the database.
         */
        static <T extends DocumentImpl> TransactionOperation<T, Void> deleteDocument() {
            return (broker, transaction, listener, doc) -> {
                listener.event("Deleting document: " + doc.getDocumentURI());

                final XmldbURI collectionUri = doc.getURI().removeLastSegment();
                try(final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
                    if (collection == null) {
                        throw new EXistException("No such collection: " + collectionUri);
                    }

                    if (XML_FILE == doc.getResourceType()) {
                        collection.removeXMLResource(transaction, broker, doc.getFileURI());
                    } else {
                        collection.removeBinaryResource(transaction, broker, doc.getFileURI());
                    }
                }

                return null;
            };
        }

        /**
         * Update a document in the database.
         *
         * @param <T> The type of the document held for the transaction
         *     before this operation executes.
         *
         * @param xqueryUpdate The XQuery Update to execute on the document.
         *
         * @return an operation which will update a document in the database.
         */
        static <T extends DocumentImpl> TransactionOperation<T, Void> updateDocument(final String xqueryUpdate) {
            return (broker, transaction, listener, doc) -> {
                listener.event("Updating document: " + doc.getDocumentURI() + ", with: " + xqueryUpdate);

                final XQuery xquery = broker.getBrokerPool().getXQueryService();
                final NodeSet nodeSet = new NewArrayNodeSet();
                nodeSet.add(new NodeProxy(doc));
                xquery.execute(broker, xqueryUpdate, nodeSet);
                return null;
            };
        }

        /**
         * Query a document in the database.
         *
         * @param <T> The type of the document held for the transaction
         *     before this operation executes.
         *
         * @param query The XQuery to execute against the document.
         *
         * @return an operation which will update a document in the database.
         */
        static <T extends DocumentImpl> TransactionOperation<T, Void> queryDocument(final String query) {
            return (broker, transaction, listener, doc) -> {
                listener.event("Querying document: " + doc.getDocumentURI() + ", with: " + query);

                final XQuery xquery = broker.getBrokerPool().getXQueryService();
                final NodeSet nodeSet = new NewArrayNodeSet();
                nodeSet.add(new NodeProxy(doc));
                xquery.execute(broker, query, nodeSet);
                return null;
            };
        }

        /**
         * Commit the transaction.
         *
         * @param <T> The type of the state held for the transaction
         *     before this operation executes.
         *
         * @return an operation which will commit the transaction
         *    and return the input unchanged.
         */
        static <T> TransactionOperation<T, T> commit() {
            return (broker, transaction, listener, t) -> {
                listener.event("Committing Transaction");
                transaction.commit();
                return t;
            };
        }

        /**
         * Abort the transaction.
         *
         * @param <T> The type of the state held for the transaction
         *     before this operation executes.
         *
         * @return an operation which will abort the transaction
         *    and return the input unchanged.
         */
        static <T> TransactionOperation<T, T> abort() {
            return (broker, transaction, listener, t) -> {
                listener.event("Aborting Transaction");
                transaction.abort();
                return t;
            };
        }

        /**
         * Executes this, and then the other Transaction Operation
         * on the input type {@code <T>} and returns
         * the results as a tuple.
         *
         * e.g. <pre>Tuple2(f(T) -&gt; U, other(T) -&gt; U2)</pre>
         *
         * @param <U2> thr result of the other operation.
         *
         * @param other another transaction operation which also operates on T
         *
         * @return The tuple of results.
         */
        default <U2> TransactionOperation<T, Tuple2<U, U2>> with(final TransactionOperation<T, ? extends U2> other) {
            return (broker, txn, listener, t) -> new Tuple2<>(apply(broker, txn, listener, t), other.apply(broker, txn, listener, t));
        }

        /**
         * Returns a composed function that first applies this function to
         * its input, and then applies the {@code after} function to the result.
         *
         * See {@link Function#andThen(Function)}
         *
         * @param <V> the result of the after operation.
         *
         * @param after the after function
         *
         * @return the composed function
         */
        default <V> TransactionOperation<T, V> andThen(final TransactionOperation<? super U, ? extends V> after) {
            return (broker, txn, listener, t) -> after.apply(broker, txn, listener, apply(broker, txn, listener, t));
        }

        /**
         * Returns a composed function that first applies the {@code before}
         * function to its input, and then applies this function to the result.
         *
         * See {@link Function#compose(Function)}
         *
         * @param <V> the input type of the before operation.
         *
         * @param before the before function.
         *
         * @return the composed function
         */
        default <V> TransactionOperation<V, U> compose(final TransactionOperation<? super V, ? extends T> before) {
            Objects.requireNonNull(before);
            return (broker, txn, listener, v) -> apply(broker, txn, listener, before.apply(broker, txn, listener, v));
        }

        /**
         * Returns a function that always returns its input argument.
         *
         * See {@link Function#identity()}
         *
         * @param <T> the result of the identity operation.
         *
         * @return the identity operation
         */
        static <T> TransactionOperation<T, T> identity() {
            return (broker, transaction, listener, t) -> t;
        }
    }

    /**
     * A simple extension of {@link CountDownLatch}
     * which also provides a name for the latch.
     *
     * Useful for debugging latching ordering in
     * transaction schedules.
     */
    class NamedCountDownLatch extends CountDownLatch {
        private final String name;

        public NamedCountDownLatch(final String name, final int count) {
            super(count);
            this.name = name;
        }

        public final String getName() {
            return name;
        }
    }

    /**
     * A listener that receives events
     * from the scheduler during execution
     * of the schedule.
     */
    @FunctionalInterface
    interface ExecutionListener {

        /**
         * Called when a execution event occurs.
         *
         * @param message a message describing the event.
         */
        void event(final String message);
    }

    /**
     * Discards {@link ExecutionListener} Events
     */
    ExecutionListener NULL_SCHEDULE_LISTENER = message -> {};

    /**
     * Just writes {@link ExecutionListener} Events to Standard Out
     */
    ExecutionListener STD_OUT_SCHEDULE_LISTENER = message -> System.out.println("[" + System.nanoTime() + "]: " + Thread.currentThread().getName() + ": " + message);
}
