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
package org.exist.http;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicLong;

public class SessionCountListener implements HttpSessionListener {

    private static AtomicLong activeSessions = new AtomicLong();

    @Override
    public void sessionCreated(final HttpSessionEvent httpSessionEvent) {
        activeSessions.incrementAndGet();
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent httpSessionEvent) {
        activeSessions.decrementAndGet();
    }

    public static long getActiveSessions() {
        return activeSessions.get();
    }
}
