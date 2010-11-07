/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id: Source.java 11737 2010-05-02 21:25:21Z ixitar $
 */

package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.dbgp.Errors;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.Base64Encoder;
import org.exist.xmldb.XmldbURI;

import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
    	if (fileURI == null)
    		return;
    	
    	InputStream is = null;
        try {
        	
        	if (fileURI.toLowerCase().startsWith("dbgp:database")) {
        		XmldbURI pathUri = XmldbURI.create( URLDecoder.decode( fileURI.substring(16) , "UTF-8" ) );

        		DBBroker broker = getJoint().getContext().getBroker();
    			DocumentImpl resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

    			if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
    				is = broker.getBinaryResource((BinaryDocument) resource);
    			} else {
    				//TODO: xml source???
    				return;
    			}

        	} else {
        		URL url = new URL(fileURI);
        		URLConnection conn = url.openConnection();
        		is = conn.getInputStream();
        	}
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		byte[] buf = new byte[256];
    		int c;
    		while ((c = is.read(buf)) > -1) {
    			//TODO: begin & end line should affect 
    			baos.write(buf, 0, c);
    		}
    		
    		source = baos.toByteArray();
    		success = true;

        } catch (MalformedURLException e) {
            exception = e;
        } catch (IOException e) {
            exception = e;
        } catch (PermissionDeniedException e) {
            exception = e;
        } finally {
        	if (is != null)
				try {
					is.close();
				} catch (IOException e) {
					if (exception == null)
						exception = e;
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
    				String head = "<response " +
    					"command=\"source\" " +
                    	"success=\""+getSuccessString()+"\" " +
                    	"transaction_id=\""+transactionID+"\">";
    				String tail = "</response>";

    				Base64Encoder enc = new Base64Encoder();
    				enc.translate(source);

    				ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
}
