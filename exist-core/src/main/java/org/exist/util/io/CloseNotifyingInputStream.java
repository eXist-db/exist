/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

import com.evolvedbinary.j8fu.function.RunnableE;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An Input Stream filter which executes a callback
 * after the stream has been closed.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class CloseNotifyingInputStream extends FilterInputStream {

    private final RunnableE<IOException> closedCallback;

    /**
     * @param is The input stream.
     * @param closedCallback the callback to execute when this stream is closed.
     */
    public CloseNotifyingInputStream(final InputStream is, final RunnableE<IOException> closedCallback) {
        super(is);
        this.closedCallback = closedCallback;
    }

    @Override
    public void close() throws IOException {
        super.close();
        closedCallback.run();
    }
}
