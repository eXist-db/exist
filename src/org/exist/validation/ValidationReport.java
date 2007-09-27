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
    
    private ValidationReportItem lastItem;
    
    private long duration = -1L;
    private long start = -1L;
    private long stop = -1L;
    
    private Throwable throwed = null;
    private String namespaceUri = null;
        
    private ValidationReportItem createValidationReportItem(int type, SAXParseException exception){
        
        ValidationReportItem vri = new ValidationReportItem();
        vri.setType(type);
        vri.setLineNumber(exception.getLineNumber());
        vri.setColumnNumber(exception.getColumnNumber());
        vri.setMessage(exception.getMessage());
        vri.setPublicId(exception.getPublicId());
        vri.setSystemId(exception.getSystemId());
        return vri;
    }
    
    private void addItem(ValidationReportItem newItem) {
        if (lastItem == null) {
            // First reported item
            validationReport.add(newItem);
            lastItem = newItem;
            
        } else if (lastItem.getMessage().equals(newItem.getMessage())) {
            // Message is repeated
            lastItem.increaseRepeat();
            
        } else {
            // Received new message
            validationReport.add(newItem);

            // Swap reported item
            lastItem = newItem;
        }
    }
    
    /**
     *  Receive notification of a recoverable error.
     * @param exception The warning information encapsulated in a
     *                      SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another
     *                      exception.
     */
    public void error(SAXParseException exception) throws SAXException {
        addItem( createValidationReportItem(ValidationReportItem.ERROR, exception) );
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
        addItem( createValidationReportItem(ValidationReportItem.FATAL, exception) );
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
        addItem( createValidationReportItem(ValidationReportItem.WARNING, exception) );
    }
    
    
    public void setException(Exception ex){
        this.throwed=ex;
    }
    
    /**
     *  Give validation information of the XML document.
     *
     * @return FALSE if no errors and warnings occurred.
     */
    public boolean isValid(){
        return( (validationReport.size()==0) && (throwed==null) );
    }
    
    public List getValidationReportItemList(){
        return validationReport;
    }
    
    public List getTextValidationReport(){
        
        List textReport = new ArrayList();
        
        if( isValid() ){
            textReport.add("Document is valid.");
        } else {
            textReport.add("Document is not valid.");
        }
        
        if(throwed!=null){
            textReport.add( "Exception: " + throwed.getMessage() );
        }
        
        for (Iterator iter = validationReport.iterator(); iter.hasNext(); ) {     
            textReport.add( iter.next().toString() );
        }

        textReport.add("Validated in "+duration+" millisec.");
        return textReport;
    }
    
    public String[] getValidationReportArray(){
        
        List validationReport = getTextValidationReport();
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
        
        Iterator reportIterator = getTextValidationReport().iterator();
        while(reportIterator.hasNext()){
            validationReport.append(reportIterator.next().toString());
            validationReport.append("\n");
        }
        
        return validationReport.toString();
    }

    public void start() {
        start=System.currentTimeMillis();
    }

    public void stop() {
        if(getValidationDuration() == -1L){ // not already stopped
            stop=System.currentTimeMillis();
            setValidationDuration(stop-start);
        }
    }

    public void setThrowable(Throwable throwable) {
        throwed=throwable;
    }
    
    public Throwable getThrowable() {
        return throwed;
    }
    
    public void setNamespaceUri(String namespace){
        namespaceUri=namespace;
    }
    
    public String getNamespaceUri(){
        return namespaceUri;
    }
}
