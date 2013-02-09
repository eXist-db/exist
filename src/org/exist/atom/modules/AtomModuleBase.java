/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.atom.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.exist.EXistException;
import org.exist.atom.AtomModule;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.collections.triggers.TriggerException;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 * 
 * @author R. Alexander Milowski
 */
public class AtomModuleBase implements AtomModule {

	protected Context context;

	/** Creates a new instance of AtomModuleBase */
	public AtomModuleBase() {
	}

	public void init(Context context) throws EXistException {
		this.context = context;
	}

	protected Context getContext() {
		return context;
	}

	public void process(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException,
			IOException, TriggerException {
		
		final String method = request.getMethod();
		if ("GET".equals(method)) {
			doGet(broker, request, response);

		} else if ("POST".equals(method)) {
			doPost(broker, request, response);

		} else if ("PUT".equals(method)) {
			doPut(broker, request, response);

		} else if ("HEAD".equals(method)) {
			doHead(broker, request, response);

		} else if ("DELETE".equals(method)) {
			doDelete(broker, request, response);

		} else {
			throw new BadRequestException("Method " + request.getMethod()
					+ " is not supported by this module.");
		}
	}

	public void doGet(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		
		throw new BadRequestException("Method " + request.getMethod()
				+ " is not supported by this module.");
	}

	public void doHead(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		
		throw new BadRequestException("Method " + request.getMethod()
				+ " is not supported by this module.");
	}

	public void doPost(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		
		throw new BadRequestException("Method " + request.getMethod()
				+ " is not supported by this module.");
	}

	public void doPut(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException {
		
		throw new BadRequestException("Method " + request.getMethod()
				+ " is not supported by this module.");
	}

	public void doDelete(DBBroker broker, IncomingMessage request,
			OutgoingMessage response) throws BadRequestException,
			PermissionDeniedException, NotFoundException, EXistException,
			IOException, TriggerException {
		
		throw new BadRequestException("Method " + request.getMethod()
				+ " is not supported by this module.");
	}

	protected File storeInTemporaryFile(InputStream is, long len) throws IOException {
		
		final File tempFile = File.createTempFile("atom", ".tmp");
		final OutputStream os = new FileOutputStream(tempFile);
		final byte[] buffer = new byte[4096];
		int count = 0;
		long l = 0;
		do {
			count = is.read(buffer);
			if (count > 0) {
				os.write(buffer, 0, count);
			}
			l += count;
		} while ((len < 0 && count >= 0) || l < len);

		os.close();
		return tempFile;
	}
}