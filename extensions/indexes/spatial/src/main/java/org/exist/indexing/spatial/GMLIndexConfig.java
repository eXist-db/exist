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
package org.exist.indexing.spatial;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public class GMLIndexConfig {

    private static final Logger LOG = LogManager.getLogger(GMLIndexConfig.class);

    private final static String FLUSH_AFTER = "flushAfter";	
    private int flushAfter = -1;

    public GMLIndexConfig(Map<String, String> namespaces, Element node) {
        String param = node.getAttribute(FLUSH_AFTER);
        if (!param.isEmpty()) {
            try {
                flushAfter = Integer.parseInt(param);
            } catch (NumberFormatException e) {
                LOG.info("Invalid value for '" + FLUSH_AFTER + "'", e);
            }
        }
    }

    public int getFlushAfter() {
        return flushAfter;
    }
}
