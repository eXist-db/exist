/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.http.underheavyload;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ClientsManager implements Runnable {
	
	private boolean running;
	
	private int number = 1;
	protected String url;
	
	List<Client> list = new ArrayList<Client>();
	
	public ClientsManager(int number, String url) {
		this.number = number;
		this.url = url;
	}

	public void start() {
		Thread thread = new Thread(this);

		running = true;
		thread.start();
	}

	public void shutdown() {
		running = false;
		
		for (Client client : list) {
			client.shutdown();
		}
	}

//	@Override
	public void run() {
		Client client = null;
		while (running) {
			if (list.size() >= number) {
				running = false;
			} else {
				client = new Client(this);
				Thread thread = new Thread(client);
				thread.start();
				list.add(client);
			}
		}
	}
	
	public String getURL() {
		return url;		
	}
}
