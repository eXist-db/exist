/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2008 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.examples.http;

import org.exist.xmldb.XmldbURI;

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
 * Execute: bin\run.bat org.exist.examples.http.PutExample <fileid>
 * Make sure you have the server started with bin\startup.bat beforehand.
 *
 * @author wolf
 * @author ljo
 *
 */
public class PutExample {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: bin/run.sh org.exist.examples.http.PutExample <fileid>");
            System.exit(0);
        }

        String fileName = args[0];
        File file = new File(fileName);
        if (!file.canRead()) {
            System.err.println("Cannot read file " + file);
            return;
        }
        String docName = file.getName();

        try {
            URL url = new URL("http://localhost:8080/exist/rest" 
                    + XmldbURI.ROOT_COLLECTION + "/test/" + docName);
            
            System.out.println("PUT file to "+url.toString());
            HttpURLConnection connect = (HttpURLConnection) url.openConnection();
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xml");

            try (OutputStream os = connect.getOutputStream();
                 InputStream is = new FileInputStream(file) ) {

                byte[] buf = new byte[1024];
                int c;
                while ((c = is.read(buf)) > -1) {
                    os.write(buf, 0, c);
                }
                os.flush();
            }

            System.out.println("Statuscode "+connect.getResponseCode() 
                    +" ("+ connect.getResponseMessage() +")");

            System.out.println("GET file from " + url.toString());

            connect = (HttpURLConnection) url.openConnection();
            connect.setRequestMethod("GET");
            connect.connect();
            System.out.println("Result:");

            try (InputStream connectInputStream = connect.getInputStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(connectInputStream);
                 BufferedReader bis = new BufferedReader(inputStreamReader) ) {

                String line;
                while ((line = bis.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } catch (Exception e) {
            System.err.println("An exception occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
