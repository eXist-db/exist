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
 *  $Id$
 */
package org.exist.validation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Report containing all validation info (errors, warnings).
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 *
 * @see org.xml.sax.ErrorHandler
 */
public class ValidationReport implements ErrorHandler {
    
    private ArrayList validationReport = new ArrayList();
    
    private long duration = -1L;
    private long start = -1L;
    private long stop = -1L;
    
    private Throwable exception = null;
        
    private ValidationReportItem createValidationReportItem(int type, SAXParseException exception){
        
        ValidationReportItem vri = new ValidationReportItem();
        vri.type=type;
        vri.lineNumber=exception.getLineNumber();
        vri.columnNumber=exception.getColumnNumber();
        vri.message=exception.getMessage();
        vri.publicId=exception.getPublicId();
        vri.systemId=exception.getSystemId();
        return vri;
    }
    
    /**
     *  Receive notification of a recoverable error.
     * @param exception The warning information encapsulated in a
     *                      SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void error(SAXParseException exception) throws SAXException {
        
        validationReport.add( createValidationReportItem(ValidationReportItem.ERROR, exception) );
        
    }
    
    /**
     *  Receive notification of a non-recoverable error.
     *
     * @param exception     The warning information encapsulated in a
     *                      SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void fatalError(SAXParseException exception) throws SAXException {
        validationReport.add( createValidationReportItem(ValidationReportItem.FATAL, exception) );
    }
    
    /**
     * Receive notification of a warning.
     *
     * @param exception     The warning information encapsulated in a
     *                      SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void warning(SAXParseException exception) throws SAXException {
        validationReport.add( createValidationReportItem(ValidationReportItem.WARNING, exception) );
    }
    
    
    public void setException(Exception ex){
        this.exception=ex;
    }
    
    /**
     *  Give validation information of the XML document.
     *
     * @return FALSE if no errors and warnings occurred.
     */
    public boolean isValid(){
        return( (validationReport.size()==0) && (exception==null) );
    }
    
    public List getValidationReport(){
        
        List textReport = new ArrayList();
        
        if( isValid() ){
            textReport.add("Document is valid.");
        } else {
            textReport.add("Document is not valid.");
        }
        
        for (Iterator iter = validationReport.iterator(); iter.hasNext(); ) {     
            textReport.add( iter.next().toString() );
        }
        
        if(exception!=null){
            textReport.add( "Exception: " + exception.getMessage() );
        }
        
        textReport.add("Validated in "+duration+" millisec.");
        return textReport;
    }
    
    public String[] getValidationReportArray(){
        
        List validationReport = getValidationReport();
        String report[] = new String[ validationReport.size() ];
        
        int counter=0;
        for( Iterator iter = validationReport.iterator(); iter.hasNext();){
            report[counter]=iter.next().toString();
            counter++;
        }
        return report;
    }
    
    public void setValidationDuration(long time) {
        duration=time;
    }
    
    public long getValidationDuration() {
        return duration;
    }
    
    public String toString(){
        
        StringBuffer validationReport = new  StringBuffer();
        
        Iterator reportIterator = getValidationReport().iterator();
        while(reportIterator.hasNext()){
            validationReport.append(reportIterator.next().toString());
            validationReport.append("\n");
        }
        
        return validationReport.toString();
    }

    void start() {
        start=System.currentTimeMillis();
    }

    void stop() {
        if(getValidationDuration() == -1L){ // not already stopped
            stop=System.currentTimeMillis();
            setValidationDuration(stop-start);
        }
    }

    void setThrowable(Throwable throwable) {
        exception=throwable;
    }
}

class ValidationReportItem {
    
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int FATAL = 4;
    
    public int type = -1;
    public int lineNumber = -1;
    public int columnNumber = -1;
    public String publicId = null;
    public String systemId = null; 
        
    public String message ="";
    
    
    public String toString(){
        
        String reportType="UNKNOWN";
        
        switch (type) {
            case WARNING:  reportType="Warning"; break;
            case ERROR:    reportType="Error"; break;
            case FATAL:    reportType="Fatal"; break;
            default:       reportType="Unknown Error type"; break;
        }
        
        return (reportType
                + " (" + lineNumber +","+ columnNumber + ") : " + message);
    }
}
