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

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringTokenizer;

public enum TriggerEvent {
	CREATE_COLLECTION,
	UPDATE_COLLECTION,
	COPY_COLLECTION,
	MOVE_COLLECTION,
	DELETE_COLLECTION,

	CREATE_DOCUMENT,
	UPDATE_DOCUMENT,
	COPY_DOCUMENT,
	MOVE_DOCUMENT,
	DELETE_DOCUMENT;

	@Deprecated
	public String legacyEventName() {
		return name().replace('_', '-');
	}

	@Deprecated
	public static @Nullable
	TriggerEvent forLegacyEventName(final String legacyEventName) {
		for (final TriggerEvent event : TriggerEvent.values()) {
			if (event.legacyEventName().equals(legacyEventName)) {
				return event;
			}
		}
		return null;
	}

	public static Set<TriggerEvent> convertFromLegacyEventNamesString(final String events) {
		final Set<TriggerEvent> result = EnumSet.noneOf(TriggerEvent.class);
		final StringTokenizer tok = new StringTokenizer(events, ", ");
		while (tok.hasMoreTokens()) {
			final String eventStr = tok.nextToken();
			final TriggerEvent event = TriggerEvent.forLegacyEventName(eventStr);
			if (event == null) {
//	        	throw new TriggerException("Unknown event type: " + eventStr);
			} else {
				result.add(event);
			}
		}
		return result;
	}

	public static Set<TriggerEvent> convertFromOldDesign(final String events) {
		final Set<TriggerEvent> result = EnumSet.noneOf(TriggerEvent.class);
		final StringTokenizer tok = new StringTokenizer(events, ", ");
		while (tok.hasMoreTokens()) {
			final String eventStr = tok.nextToken();
			switch (eventStr) {
				case "STORE":
					result.add(TriggerEvent.CREATE_DOCUMENT);
					break;

				case "UPDATE":
					result.add(TriggerEvent.UPDATE_DOCUMENT);
					break;

				case "REMOVE":
					result.add(TriggerEvent.DELETE_DOCUMENT);
					break;

				default:
//					throw new TriggerException("Unknown event type: " + eventStr);
			}
		}
		return result;
	}
}
