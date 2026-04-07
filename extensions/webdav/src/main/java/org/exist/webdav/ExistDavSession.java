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
package org.exist.webdav;

import org.apache.jackrabbit.webdav.DavSession;
import org.exist.security.Subject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple DavSession implementation wrapping an eXist-db Subject.
 * Authentication is handled by the servlet container (Jetty JAAS),
 * so this session simply holds the authenticated subject and lock tokens.
 *
 * @author Joe Wicentowski
 */
public class ExistDavSession implements DavSession {

    private final Subject subject;
    private final Set<Object> references = new HashSet<>();
    private final List<String> lockTokens = new ArrayList<>();

    public ExistDavSession(final Subject subject) {
        this.subject = subject;
    }

    /**
     * Get the authenticated eXist-db subject for this session.
     *
     * @return the authenticated subject
     */
    public Subject getSubject() {
        return subject;
    }

    @Override
    public void addReference(final Object reference) {
        references.add(reference);
    }

    @Override
    public void removeReference(final Object reference) {
        references.remove(reference);
    }

    @Override
    public void addLockToken(final String token) {
        lockTokens.add(token);
    }

    @Override
    public String[] getLockTokens() {
        return lockTokens.toArray(new String[0]);
    }

    @Override
    public void removeLockToken(final String token) {
        lockTokens.remove(token);
    }
}
