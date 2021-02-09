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
package org.exist;

import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 * @deprecated Use {@link org.exist.storage.BrokerPoolService} instead which
 * offers more fine grained events.
 */
@Deprecated
public interface LifeCycle {

	public void start(DBBroker broker, final Txn transaction) throws EXistException;

	public void sync(DBBroker broker) throws EXistException;

	public void stop(DBBroker broker) throws EXistException;
	
//    public boolean isRunning();
//
//    public boolean isStarted();
//    public boolean isStarting();
//
//    public boolean isStopping();
//    public boolean isStopped();
//
//    public boolean isFailed();
}
