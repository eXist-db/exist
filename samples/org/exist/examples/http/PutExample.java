/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.examples.http;

import org.exist.storage.DBBroker;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * PutExample
 * 
 * @author wolf
 *
 */
public class PutExample {

	public static void main(String[] args) {
		String fileName =args[0];
		File file =new File(fileName);
		if(!file.canRead()) {
			System.err.println("Cannot read file " + file);
			return;
		}
		String docName =file.getName();
		
		try {
			URL url = new URL("http://admin:@localhost:8080/exist/rest" + DBBroker.ROOT_COLLECTION + "/test/" + 
                docName);
			HttpURLConnection connect =(HttpURLConnection)url.openConnection();
			connect.setRequestMethod("PUT");
			connect.setDoOutput(true);
			connect.setRequestProperty("ContentType", "text/xml");
			
			OutputStream os = connect.getOutputStream();
			InputStream is =new FileInputStream(file);
			byte[] buf =new byte[1024];
			int c;
			while((c = is.read(buf)) > -1)
				os.write(buf, 0, c);
			
			System.out.println("Connecting to " + url.toString());
			connect.connect();
			
			System.out.println("Result:");
			BufferedReader bis = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			String line;
			while((line = bis.readLine()) !=null)
				System.out.println(line);
		} catch (Exception e) {
			System.err.println("An exception occurred: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
