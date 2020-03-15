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
package org.exist.storage.index;

import org.exist.storage.BrokerPool;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.util.FileUtils;

import java.nio.file.Path;

public class BTreeStore extends BTree {

    public BTreeStore(final BrokerPool pool, final byte fileId, final short fileVersion, final boolean recoverEnabled, final Path file, final DefaultCacheManager cacheManager) throws DBException {
        super(pool, fileId, fileVersion, recoverEnabled, cacheManager, file);

        if(exists()) {
            open(fileVersion);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating data file: " + FileUtils.fileName(getFile()));
            }
            create((short)-1);
        }
        setSplitFactor(0.7);
    }

    @Override
    public String getLockName() {
        return FileUtils.fileName(getFile());
    }
}
