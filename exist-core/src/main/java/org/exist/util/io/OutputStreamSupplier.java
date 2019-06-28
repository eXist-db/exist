/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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

package org.exist.util.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
@FunctionalInterface
interface OutputStreamSupplier {
    /**
     * Returns a newly created {@link OutputStream} instance. The receiver is
     * responsible to close the stream after use.
     *
     * @return a new output stream instance
     * @throws IOException if a error while creating the steam occurs
     */
    OutputStream get() throws IOException;
}