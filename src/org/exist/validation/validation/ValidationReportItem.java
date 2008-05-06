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
 * $Id$
 */
package org.exist.validation;

public class ValidationReportItem {
    
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int FATAL = 4;
    
    private int type = -1;
    private int lineNumber = -1;
    private int columnNumber = -1;
    private String publicId = null;
    private String systemId = null; 
    private String message ="";
    private int repeat=1;
    
    public void setType(int type){
        this.type=type;
    }
    
    public int getType(){
        return type;
    }
    
    public void setLineNumber(int nr){
        this.lineNumber=nr;
    }
    
    public int getLineNumber(){
        return this.lineNumber;
    }
    
    public void setColumnNumber(int nr){
        this.columnNumber=nr;
    }
    
    public int getColumnNumber(){
        return this.columnNumber;
    }
    
    public void setMessage(String message){
        this.message=message;
    }
    
    public String getMessage(){
        return this.message;
    }
    
    public void setPublicId(String publicId){
        this.publicId=publicId;
    }
    
    public String getPublicId(){
        return this.publicId;
    }
    
    public void setSystemId(String systemId){
        this.systemId=systemId;
    }
    
    public String getSystemId(){
        return this.systemId;
    }
    
    public String getTypeText(){
        
        String reportType="UNKNOWN";
        
        switch (type) {
            case WARNING:  reportType="Warning"; break;
            case ERROR:    reportType="Error"; break;
            case FATAL:    reportType="Fatal"; break;
            default:       reportType="Unknown Error type"; break;
        }
        
        return reportType;
    }
    
    public String toString(){
        
        String reportType=getTypeText();
        
        return (reportType
                + " (" + lineNumber +","+ columnNumber + ") : " + message);
    }
    
    public void increaseRepeat(){
        repeat++;
    }
    
    public int getRepeat(){
        return repeat;
    }
}

