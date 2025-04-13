/*
 * NOTE: This file is in part based on code from Mort Bay Consulting.
 * The original license statement is also included below.
 *
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
 *
 * ---------------------------------------------------------------------
 *
 * Copyright 2002-2005 Mort Bay Consulting Pty. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exist.start;

/**
 * Utility class for parsing and comparing version strings.
 * JDK 1.1 compatible.
 *
 * @author Jan Hlavatý
 */
public class Version {

    private static final int INFERIOR = -1;
    private static final int EQUAL = 0;
    private static final int SUPERIOR = 1;

    int _version = 0;
    int _revision = 0;
    int _subrevision = 0;
    String _suffix = "";

    public Version() {
    }

    public Version(final String version_string) {
        parse(version_string);
    }

    /**
     * parses version string in the form version[.revision[.subrevision[extension]]]
     * into this instance.
     *
     * @param version_string Text representation of a version.
     */
    public void parse(final String version_string) {
        _version = 0;
        _revision = 0;
        _subrevision = 0;
        _suffix = "";
        int pos = 0;
        int startpos = 0;
        final int endpos = version_string.length();
        while ((pos < endpos) && Character.isDigit(version_string.charAt(pos))) {
            pos++;
        }
        _version = Integer.parseInt(version_string.substring(startpos, pos));
        if ((pos < endpos) && version_string.charAt(pos) == '.') {
            startpos = ++pos;
            while ((pos < endpos) && Character.isDigit(version_string.charAt(pos))) {
                pos++;
            }
            _revision = Integer.parseInt(version_string.substring(startpos, pos));
        }
        if ((pos < endpos) && version_string.charAt(pos) == '.') {
            startpos = ++pos;
            while ((pos < endpos) && Character.isDigit(version_string.charAt(pos))) {
                pos++;
            }
            _subrevision = Integer.parseInt(version_string.substring(startpos, pos));
        }
        if (pos < endpos) {
            _suffix = version_string.substring(pos);
        }
    }

    /**
     * @return string representation of this version
     */
    public String toString() {
        return String.valueOf(_version) + '.' + _revision + '.' + _subrevision + _suffix;
    }

    // java.lang.Comparable is Java 1.2! Cannot use it

    /**
     * Compares with other version. Does not take extension into account,
     * as there is no reliable way to order them.
     *
     * @param other Version object to be compared.
     * @return Constants.INFERIOR if this is older version that other,
     * Constants.EQUAL if its same version,
     * Constants.SUPERIOR if it's newer version than other
     */
    public int compare(final Version other) {
        if (other == null) {
            throw new NullPointerException("other version is null");
        }
        if (this._version < other._version) {
            return INFERIOR;
        }
        if (this._version > other._version) {
            return SUPERIOR;
        }
        if (this._revision < other._revision) {
            return INFERIOR;
        }
        if (this._revision > other._revision) {
            return SUPERIOR;
        }
        if (this._subrevision < other._subrevision) {
            return INFERIOR;
        }
        if (this._subrevision > other._subrevision) {
            return SUPERIOR;
        }
        return EQUAL;
    }

    /**
     * Check whether this version is in range of versions specified
     *
     * @param high Highest version, inclusive.
     * @param low  Lowest version, inclusive.
     * @return TRUE if Version is between high and low (inclusive), otherwise FALSE.
     */
    public boolean isInRange(final Version low, final Version high) {
        return (compare(low) >= 0 && compare(high) <= 0);
    }
}
