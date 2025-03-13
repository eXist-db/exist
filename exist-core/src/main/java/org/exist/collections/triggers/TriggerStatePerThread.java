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

import org.exist.xmldb.XmldbURI;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

/**
 * Avoid infinite recursions in Triggers by preventing the same trigger
 * from running in the same phase on the same event for the same URIs.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TriggerStatePerThread {
	
	private final static ThreadLocal<Deque<TriggerState>> THREAD_LOCAL_STATES = ThreadLocal.withInitial(ArrayDeque::new);

	public static void setAndTest(final Trigger trigger, final TriggerPhase triggerPhase, final TriggerEvent triggerEvent, final XmldbURI src, final @Nullable XmldbURI dst) throws CyclicTriggerException {
		final Deque<TriggerState> states = THREAD_LOCAL_STATES.get();

		if (states.isEmpty()) {
			if (triggerPhase != TriggerPhase.BEFORE) {
				throw new IllegalStateException("The Before phase of a trigger must occur before the After phase");
			}
			states.addFirst(new TriggerState(trigger, triggerPhase, triggerEvent, src, dst));
			return;
		}

		TriggerState prevState = states.peekFirst();

		// is the new state the same as the previous state (excluding the phase)
		if (prevState.equalsIgnoringPhase(trigger, triggerEvent, src, dst)) {

			// is this the after phase (i.e. matching completion) of a previous non-cyclic before phase?
			if (triggerPhase == TriggerPhase.AFTER) {

				int skipBefores = 0;

                for (TriggerState state : states) {
                    prevState = state;

                    // travel up, first "Before" we encounter - we should check if (a) that we complete it, and/or (b) is non-cyclic (if not we are also cyclic)
                    if (prevState.triggerPhase == TriggerPhase.BEFORE) {

                        if (skipBefores > 0) {
                            skipBefores--;

                        } else {
                            if (prevState.isCompletedBy(trigger, triggerPhase, triggerEvent, src, dst)) {
                                if (prevState instanceof PossibleCyclicTriggerState) {
                                    // if the Before phase is a PossibleCyclicTriggerState then this completing After phase must also be a PossibleCyclicTriggerState
                                    final TriggerState newState = new PossibleCyclicTriggerState(trigger, triggerPhase, triggerEvent, src, dst);
                                    states.addFirst(newState);

                                    throw new CyclicTriggerException("Detected Matching possible cyclic trigger event for After phase (" + newState + ") of previous Before phase (" + prevState + ")");

                                } else {
                                    // if the Before Phase is NOT a PossibleCyclicTriggerState, then neither is this completing After phase...
                                    states.addFirst(new TriggerState(trigger, triggerPhase, triggerEvent, src, dst));
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
				final TriggerState newState = new PossibleCyclicTriggerState(trigger, triggerPhase, triggerEvent, src, dst);
				states.addFirst(newState);

				throw new CyclicTriggerException("Detected possible cyclic trigger events: " + newState);
			}
		}

		states.addFirst(new TriggerState(trigger, triggerPhase, triggerEvent, src, dst));
	}

	public static class CyclicTriggerException extends Exception {
		public CyclicTriggerException(final String message) {
			super(message);
		}
	}

	public static void clearIfFinished(final TriggerPhase phase) {
		if (phase == TriggerPhase.AFTER) {

			int depth = 0;
			final Deque<TriggerState> states = THREAD_LOCAL_STATES.get();
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
				clear();
			}
		}
	}

	public static void clear() {
		THREAD_LOCAL_STATES.remove();
	}

	public static boolean isEmpty() {
		return THREAD_LOCAL_STATES.get().isEmpty();
	}

	private static class PossibleCyclicTriggerState extends TriggerState {
		public PossibleCyclicTriggerState(final TriggerState triggerState) {
			super(triggerState.trigger, triggerState.triggerPhase, triggerState.triggerEvent, triggerState.src, triggerState.dst);
		}

		public PossibleCyclicTriggerState(final Trigger trigger, final TriggerPhase triggerPhase, final TriggerEvent triggerEvent, final XmldbURI src, final @Nullable XmldbURI dst) {
			super(trigger, triggerPhase, triggerEvent, src, dst);
		}
	}

	private static class TriggerState {
		private final Trigger trigger;
		private final TriggerPhase triggerPhase;
		private final TriggerEvent triggerEvent;
		private final XmldbURI src;
		private final @Nullable XmldbURI dst;

		public TriggerState(final Trigger trigger, final TriggerPhase triggerPhase, final TriggerEvent triggerEvent, final XmldbURI src, final @Nullable XmldbURI dst) {
			this.trigger = trigger;
			this.triggerPhase = triggerPhase;
			this.triggerEvent = triggerEvent;
			this.src = src;
			this.dst = dst;
		}

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
			if (trigger instanceof XQueryTrigger) {
				final String urlQuery = ((XQueryTrigger) trigger).getUrlQuery();
				if (urlQuery != null && !urlQuery.isEmpty()) {
					builder.append('(');
					builder.append(urlQuery);
					builder.append(')');
				}
			}
			return builder.toString();
		}

		@Override
		public boolean equals(final Object o) {
			return equals(o, false);
		}

		public boolean equalsIgnoringPhase(final Object o) {
			return equals(o, true);
		}

		private boolean equals(final Object o, final boolean ignorePhase) {
			if (this == o) {
				return true;
			}

			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			final TriggerState that = (TriggerState) o;

			if (!trigger.equals(that.trigger)) {
				return false;
			}

			if (!ignorePhase) {
				if (triggerPhase != that.triggerPhase) {
					return false;
				}
			}

			if (triggerEvent != that.triggerEvent) {
				return false;
			}

			if (!src.equals(that.src)) {
				return false;
			}

			return Objects.equals(dst, that.dst);
		}

		private boolean equalsIgnoringPhase(final Trigger otherTrigger, final TriggerEvent otherTriggerEvent, final XmldbURI otherSrc, @Nullable final XmldbURI otherDst) {
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

		public boolean completes(final Object o) {
			if (this == o) {
				return false;
			}

			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			final TriggerState that = (TriggerState) o;

			if (this.triggerPhase != TriggerPhase.AFTER
					|| that.triggerPhase != TriggerPhase.BEFORE) {
				return false;
			}

			if (!trigger.equals(that.trigger)) {
				return false;
			}

			if (triggerEvent != that.triggerEvent) {
				return false;
			}

			if (!src.equals(that.src)) {
				return false;
			}

			return Objects.equals(dst, that.dst);
		}
	}
}
