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

package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamReader;

/**
 * A NodeTest which never matches.
 *
 * Used for the AbbrevForwardStep '@' when
 * attempting to match not attribute node kinds.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class FailTest implements NodeTest {
    private static final int NO_TYPE = Integer.MIN_VALUE;

    public static final FailTest INSTANCE = new FailTest();

    private FailTest() {
    }

    @Override
    public void setType(final int nodeType) {
    }

    @Override
    public int getType() {
        return NO_TYPE;
    }

    @Override
    public boolean matches(final NodeProxy proxy) {
        return false;
    }

    @Override
    public boolean matches(final Node node) {
        return false;
    }

    @Override
    public boolean matches(final XMLStreamReader reader) {
        return false;
    }

    @Override
    public boolean matches(final QName name) {
        return false;
    }

    @Override
    public boolean isWildcardTest() {
        return false;
    }

    @Override
    public QName getName() {
        return null;
    }
}
