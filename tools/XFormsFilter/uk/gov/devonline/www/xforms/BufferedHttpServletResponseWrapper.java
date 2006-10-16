/*
 *  XFormsFilter: BufferedHttpServletResponseWrapper
 *  Copyright (C) 2006 Matthijs Wensveen <m.wensveen@func.nl>
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
 *  $Id: XFormsFilter.java 4565 2006-10-12 12:42:18 +0000 (Thu, 12 Oct 2006) deliriumsky $
 */
package uk.gov.devonline.www.xforms;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * @author Matthijs Wensveen <m.wensveen@func.nl>
 */
public class BufferedHttpServletResponseWrapper extends HttpServletResponseWrapper
{
    private ByteArrayOutputStream output;
    private BufferedServletOutputStream servletOutputStream;

    private int contentLength;
    private String contentType;

    /** GenericResponseWrapper constructor
     * 
     * @param response HttpServletResponse
     */
    public BufferedHttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
        output = new ByteArrayOutputStream();
        servletOutputStream = new BufferedServletOutputStream(output);
    }

    /**
     * getData get the data that would be written to the response as array of bytes
     * @return byte[] array of bytes
     */
    public byte[] getData() {
        return output.toByteArray();
    }

    /**
     * getData get the data that would be written to the response as String
     * @return String String with output
     */
    public String getDataAsString() {
        return output.toString();
    }
    
    /** getOutputStream
     * overriden method to capture the output written to the ServlertOutputStream
     */
    public ServletOutputStream getOutputStream() {
        return servletOutputStream;
    }

    
    public void setContentLength(int length) {
        this.contentLength = length;
        super.setContentLength(length);
    }

    public int getContentLength() {
        return contentLength;
    }

    public void setContentType(String type) {
        this.contentType = type;
        super.setContentType(type);
    }

    public String getContentType() {
        return contentType;
    }

    /** getWriter get the PrintWriter to write data to
     * @return PrintWriter 
     */
    public PrintWriter getWriter() {
        return new PrintWriter(getOutputStream(), true);
    }
}
