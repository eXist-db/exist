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
package org.exist.indexing;

/**
 * SPI for index module auto-discovery via {@code ServiceLoader}.
 *
 * <p>Register implementations in
 * {@code META-INF/services/org.exist.indexing.IndexFactory}.
 * A conf.xml {@code <module>} entry with the same id takes precedence;
 * an entry with {@code enabled="no"} suppresses the SPI-registered module.</p>
 */
public interface IndexFactory {

    /**
     * The default conf.xml {@code id} for this index (e.g. {@code "lucene-index"}).
     * Used as the key in the index registry and to match conf.xml override entries.
     */
    String getDefaultId();

    /** The concrete {@link AbstractIndex} subclass to instantiate. */
    Class<? extends AbstractIndex> getIndexClass();
}
