/*
 * Copyright (C) 2018 Adam Retter
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
package org.exist.storage.blob;

import org.exist.storage.journal.Loggable;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface BlobLoggable extends Loggable {
    byte LOG_STORE_BLOB_FILE = 0x50;
    byte LOG_UPDATE_BLOB_REF_COUNT = 0x51;
}
