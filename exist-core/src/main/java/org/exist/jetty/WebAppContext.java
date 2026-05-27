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
package org.exist.jetty;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.exist.storage.BrokerPool;

/**
 * eXist {@link org.eclipse.jetty.ee10.webapp.WebAppContext} with Windows path handling for
 * Jetty 12.1 ({@link WindowsPathResource}). Used for the main webapp ({@code /exist} or
 * standalone {@code /}) and the distribution portal at {@code /}.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class WebAppContext extends org.eclipse.jetty.ee10.webapp.WebAppContext {

    @Override
    public String toString() {
        return "eXist-db Open Source Native XML Database";
    }

    @Override
    public void setBaseResource(final Resource baseResource) {
        super.setBaseResource(WindowsPathResource.wrapIfNeeded(baseResource, ResourceFactory.of(this)));
    }

    @Override
    public Resource newResource(final String urlOrPath) {
        return WindowsPathResource.wrapIfNeeded(super.newResource(urlOrPath), ResourceFactory.of(this));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (ownsBrokerPoolLifecycle()) {
            BrokerPool.stopAll(true);
        }
    }

    /**
     * Main eXist webapps stop the embedded database; the distribution portal at {@code /} is
     * static-only and must not tear down {@link BrokerPool} when Jetty stops it alongside {@code /exist}.
     */
    private boolean ownsBrokerPoolLifecycle() {
        if ("/exist".equals(getContextPath())) {
            return true;
        }
        if (!"/".equals(getContextPath())) {
            return false;
        }
        final Resource baseResource = getBaseResource();
        if (baseResource == null) {
            return true;
        }
        return !baseResource.toString().replace('\\', '/').contains("/webapps/portal");
    }
}
