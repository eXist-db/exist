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
package org.exist.collections.triggers;

import org.exist.storage.txn.Txn;
import org.exist.storage.txn.TxnListener;
import org.exist.xmldb.XmldbURI;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Avoid infinite recursions in Triggers by preventing the same trigger
 * from running in the same phase on the same event for the same URIs.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TriggerStatePerThread {
    private static final Map<Thread, TriggerStates> TRIGGER_STATES = Collections.synchronizedMap(new WeakHashMap<>());

    public static void setAndTest(final Txn txn, final Trigger trigger, final TriggerPhase triggerPhase, final TriggerEvent triggerEvent, final XmldbURI src, final @Nullable XmldbURI dst) throws CyclicTriggerException {
        final TriggerStates states = getStates(txn);

        if (states.isEmpty()) {
            if (triggerPhase != TriggerPhase.BEFORE) {
                throw new IllegalStateException("The Before phase of a trigger must occur before the After phase");
            }
            states.addFirst(new TriggerState(trigger, triggerPhase, triggerEvent, src, dst, false));
            return;
        }

        TriggerState prevState = states.peekFirst();

        // is the new state the same as the previous state (excluding the phase)
        if (prevState.equalsIgnoringPhase(trigger, triggerEvent, src, dst)) {

            // is this the after phase (i.e. matching completion) of a previous non-cyclic before phase?
            if (triggerPhase == TriggerPhase.AFTER) {

                int skipBefores = 0;

                for (final Iterator<TriggerState> it = states.iterator(); it.hasNext(); ) {
                    prevState = it.next();

                    // travel up, first "Before" we encounter - we should check if (a) that we complete it, and/or (b) is non-cyclic (if not we are also cyclic)
                    if (prevState.triggerPhase == TriggerPhase.BEFORE) {

                        if (skipBefores > 0) {
                            skipBefores--;

                        } else {
                            if (prevState.isCompletedBy(trigger, triggerPhase, triggerEvent, src, dst)) {
                                if (prevState.possiblyCyclic()) {
                                    // if the Before phase is a PossibleCyclicTriggerState then this completing After phase must also be a PossibleCyclicTriggerState
                                    final TriggerState newState = new TriggerState(trigger, triggerPhase, triggerEvent, src, dst, true);
                                    states.addFirst(newState);

                                    throw new CyclicTriggerException("Detected Matching possible cyclic trigger event for After phase (" + newState + ") of previous Before phase (" + prevState + ")");

                                } else {
                                    // if the Before Phase is NOT a PossibleCyclicTriggerState, then neither is this completing After phase...
                                    states.addFirst(new TriggerState(trigger, triggerPhase, triggerEvent, src, dst, false));
                                    return;
                                }

                            } else {
                                throw new IllegalStateException("Cannot interleave Trigger states");
                            }
                        }
                    } else if (prevState.triggerPhase == TriggerPhase.AFTER) {
                        skipBefores++;
                    }
                }

                throw new IllegalStateException("Could not find a matching Before phase for After phase");

            } else {
                // it's a cyclic exception!
                final TriggerState newState = new TriggerState(trigger, triggerPhase, triggerEvent, src, dst, true);
                states.addFirst(newState);

                throw new CyclicTriggerException("Detected possible cyclic trigger events: " + newState);
            }
        }

        states.addFirst(new TriggerState(trigger, triggerPhase, triggerEvent, src, dst, false));
    }

    public static class CyclicTriggerException extends Exception {
        public CyclicTriggerException(final String message) {
            super(message);
        }
    }

    public static void clearIfFinished(final Txn txn, final TriggerPhase phase) {
        if (phase == TriggerPhase.AFTER) {

            int depth = 0;
            final TriggerStates states = getStates(txn);
            for (final Iterator<TriggerState> it = states.descendingIterator(); it.hasNext(); ) {
                final TriggerState state = it.next();
                switch (state.triggerPhase) {
                    case BEFORE:
                        depth++;
                        break;
                    case AFTER:
                        depth--;
                        break;
                    default:
                        throw new IllegalStateException("Unknown phase: " + state.triggerPhase + "for trigger state: " + state);
                }
            }

            if (depth == 0) {
                clear(txn);
            }
        }
    }

    public static int keys() {
        return TRIGGER_STATES.size();
    }

    public static void clearAll() {
        TRIGGER_STATES.clear();
    }

    public static void clear(final Txn txn) {
        TRIGGER_STATES.remove(Thread.currentThread());
    }

    public static boolean isEmpty(final Txn txn) {
        return getStates(txn).isEmpty();
    }

    public static void dumpTriggerStates() {
        TRIGGER_STATES.forEach((k, s) -> System.err.format("key: %s, size: %s", k, s.size()).println());
    }

    public static void forEach(BiConsumer<Thread, TriggerStates> action) {
        TRIGGER_STATES.forEach(action);
    }

    private static TriggerStates getStates(final Txn txn) {
        return TRIGGER_STATES.computeIfAbsent(Thread.currentThread(), key -> new TriggerStates());
    }

    private static TriggerStates initStates(final Txn txn) {
        txn.registerListener(new TransactionCleanUp(txn, TriggerStatePerThread::clear));
        return new TriggerStates();
    }

    public record TransactionCleanUp(Txn txn, Consumer<Txn> consumer) implements  TxnListener {
        @Override
        public void commit() {
            consumer.accept(txn);
        }

        @Override
        public void abort() {
            consumer.accept(txn);
        }
    }

    public static final class TriggerStates extends WeakReference<Deque<TriggerState>> {
        public TriggerStates() {
            super(new ArrayDeque<>());
        }

        public Iterator<TriggerState> descendingIterator() {
            return get().descendingIterator();
        }

        public boolean isEmpty() {
            return get().isEmpty();
        }

        public int size() {
            return get().size();
        }

        public Iterator<TriggerState> iterator() {
            return get().iterator();
        }

        public TriggerState peekFirst() {
            return get().peekFirst();
        }

        public void addFirst(TriggerState newState) {
            get().addFirst(newState);
        }
    }

    public record TriggerState(Trigger trigger, TriggerPhase triggerPhase, TriggerEvent triggerEvent, XmldbURI src,
                                @Nullable XmldbURI dst, boolean possiblyCyclic) {

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append(triggerPhase);
            builder.append(' ');
            builder.append(triggerEvent);
            builder.append('(');
            if (triggerPhase == TriggerPhase.AFTER && dst != null) {
                builder.append(dst);
                builder.append(", ");
            }
            builder.append(src);
            if (triggerPhase == TriggerPhase.BEFORE && dst != null) {
                builder.append(", ");
                builder.append(dst);
            }
            builder.append(')');
            builder.append(": ");
            builder.append(trigger.getClass().getSimpleName());
            if (trigger instanceof XQueryTrigger queryTrigger) {
                final String urlQuery = queryTrigger.getUrlQuery();
                if (urlQuery != null && !urlQuery.isEmpty()) {
                    builder.append('(');
                    builder.append(urlQuery);
                    builder.append(')');
                }
            }
            return builder.toString();
        }


        boolean equalsIgnoringPhase(final Trigger otherTrigger, final TriggerEvent otherTriggerEvent, final XmldbURI otherSrc, @Nullable final XmldbURI otherDst) {
            if (!trigger.equals(otherTrigger)) {
                return false;
            }

            if (triggerEvent != otherTriggerEvent) {
                return false;
            }

            if (!src.equals(otherSrc)) {
                return false;
            }

            return Objects.equals(dst, otherDst);
        }

        public boolean isCompletedBy(final Trigger otherTrigger, final TriggerPhase otherTriggerPhase, final TriggerEvent otherTriggerEvent, final XmldbURI otherSrc, @Nullable final XmldbURI otherDst) {
            if (this.triggerPhase != TriggerPhase.BEFORE
                    || otherTriggerPhase != TriggerPhase.AFTER) {
                return false;
            }

            if (!trigger.equals(otherTrigger)) {
                return false;
            }

            if (triggerEvent != otherTriggerEvent) {
                return false;
            }

            if (!src.equals(otherSrc)) {
                return false;
            }

            return Objects.equals(dst, otherDst);
        }
    }
}
