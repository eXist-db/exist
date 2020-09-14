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
package org.exist.client.tristatecheckbox;

import javax.annotation.Nullable;

/**
 * See <a href="http://www.javaspecialists.eu/archive/Issue145.html">TristateCheckBox Revisited</a>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public enum TristateState {
    DESELECTED {
        public TristateState next() {
            return INDETERMINATE;
        }
    },
    INDETERMINATE {
        public TristateState next() {
            return SELECTED;
        }
    },
    SELECTED {
        public TristateState next() {
            return DESELECTED;
        }
    };

    public abstract TristateState next();

    public static TristateState fromBoolean(@Nullable final Boolean state) {
        if (state == null) {
            return TristateState.INDETERMINATE;
        } else if (state) {
            return TristateState.SELECTED;
        } else {
            return TristateState.DESELECTED;
        }
    }

    public static @Nullable Boolean toBoolean(final TristateState state) {
        switch (state) {
            case DESELECTED:
                return false;
            case SELECTED:
                return true;

            case INDETERMINATE:
            default:
                return null;
        }
    }
}
