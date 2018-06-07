/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.debuggee.dbgp.Errors;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.Base64Encoder;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;

import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Source extends Command {

	/**
	 * file URI 
	 */
	private String fileURI;
	
	/**
	 * begin line
	 */
	private Integer lineBegin = null; 

	/**
	 * end line
	 */
	private Integer lineEnd = null; 

	private boolean success = false;
    private Exception exception = null;

    private byte[] source;
    private byte[] response = null;
    
    public Source(IoSession session, String args) {
        super(session, args);
    }

    @Override
    protected void setArgument(String arg, String val) {
        if (arg.equals("f"))
            fileURI = val;
        
        else if (arg.equals("b"))
            lineBegin = Integer.valueOf(val);
        
        else if (arg.equals("e"))
            lineEnd = Integer.valueOf(val);
        
        else
            super.setArgument(arg, val);
    }

    @Override
    public void exec() {
    	if (fileURI == null) {
			return;
		}

    	InputStream is = null;
        try {
        	
        	if (fileURI.toLowerCase().startsWith("dbgp://")) {
        		String uri = fileURI.substring(7);
        		if (uri.toLowerCase().startsWith("file:/")) {
        			uri = fileURI.substring(5);
        			is = Files.newInputStream(Paths.get(uri));
        		} else {
	        		XmldbURI pathUri = XmldbURI.create( URLDecoder.decode( fileURI.substring(15) , "UTF-8" ) );
	
	        		Database db = getJoint().getContext().getDatabase();
	        		try(final DBBroker broker = db.getBroker();
						final LockedDocument resource = broker.getXMLResource(pathUri, LockMode.READ_LOCK)) {
		
		    			if (resource.getDocument().getResourceType() == DocumentImpl.BINARY_FILE) {
		    				is = broker.getBinaryResource((BinaryDocument) resource.getDocument());
		    			} else {
		    				//TODO: xml source???
		    				return;
		    			}
	        		} catch (EXistException e) {
	                    exception = e;
					}
        		}
        	} else {
        		URL url = new URL(fileURI);
        		URLConnection conn = url.openConnection();
        		is = conn.getInputStream();
        	}
    		FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
    		byte[] buf = new byte[256];
    		int c;
    		while ((c = is.read(buf)) > -1) {
    			//TODO: begin & end line should affect 
    			baos.write(buf, 0, c);
    		}
    		
    		source = baos.toByteArray();
    		success = true;

        } catch (PermissionDeniedException | IOException e) {
            exception = e;
        } finally {
        	if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					if (exception == null) {
						exception = e;
					}
				}
			}
		}
    }

    public byte[] responseBytes() {
    	if (exception != null) {
    		String url = "NULL";
    		if (fileURI != null)
    			url = fileURI;
			response = errorBytes("source", Errors.ERR_100, 
					exception.getMessage() + " (URL:"+url+")");
    	} else if (response == null) {
    		if (source != null) {
    			try {
    				String head = xml_declaration + 
    					"<response " +
	    					namespaces +
	    					"command=\"source\" " +
	                    	"success=\""+getSuccessString()+"\" " +
                			"encoding=\"base64\" " +
	                    	"transaction_id=\""+transactionID+"\"><![CDATA[";
    				String tail = 
    					"]]></response>";

    				Base64Encoder enc = new Base64Encoder();
    				enc.translate(source);

					FastByteArrayOutputStream baos = new FastByteArrayOutputStream(head.length() + ((source.length / 100) * 33) + tail.length());
    				baos.write(head.getBytes());
    				baos.write(new String(enc.getCharArray()).getBytes());
    				baos.write(tail.getBytes());
    				response = baos.toByteArray();
    			} catch (IOException e) {
    				response = errorBytes("source");
    			}
    		} else {
    			response = errorBytes("source", Errors.ERR_100, Errors.ERR_100_STR);
    		}
    	}
    	return response;
    }
    
    private String getSuccessString() {
    	if (success)
    		return "1";
    	
    	return "0";
    }

	public byte[] commandBytes() {
		String command = "source" +
				" -i "+transactionID+
				" -f "+fileURI;

		if (lineBegin != null)
			command += " -b "+String.valueOf(lineBegin);

		if (lineEnd != null)
			command += " -e "+String.valueOf(lineEnd);
		
		return command.getBytes();
	}
    
    public void setFileURI(String fileURI) {
    	this.fileURI = fileURI;
    }
    
	public String toString() {
		
		StringBuilder response = new StringBuilder();
		response.append("source ");

		if (fileURI != null) {
			response.append("fileURI = '");
			response.append(fileURI);
			response.append("' ");
		}
		
		response.append("["+transactionID+"]");

		return response.toString();
	}
}
