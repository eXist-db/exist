/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

package org.exist.validation;

import java.util.ArrayList;
import java.util.Iterator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Report containing all validation info (errors, warnings).
 * @author dizzz
 * @see org.xml.sax.ErrorHandler
 */
public class ValidationReport implements ErrorHandler {
    
    private ArrayList warnings = new ArrayList();
    private ArrayList errors = new ArrayList();
    
    /**
     *  Receive notification of a recoverable error.
     * @param exception The warning information encapsulated in a 
     *                      SAX parse exception.
     * @throws SAXException Any SAX exception, possibly wrapping another 
     *                      exception.
     */
    public void error(SAXParseException exception) throws SAXException {
        addError(exception);
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
        addError(exception);
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
        addWarning(exception);
    }
    
    
    /**
     *  Add error report to list
     * @param e Exception
     */
    private void addError(SAXParseException e) {
        getErrors().add("Error: (" + e.getLineNumber() + ", " 
                                   + e.getColumnNumber() + "): " 
                                   + e.getMessage());
    }
    
    /**
     *  Get errors
     * @return List of errors
     */
    public ArrayList getErrors() {
        return errors;
    }
    
    /**
     *  Add warning report to list
     * @param e Exception
     */
    private void addWarning(SAXParseException e) {
        getWarnings().add("Warning: (" + e.getLineNumber() + ", " 
                                       + e.getColumnNumber() + "): " 
                                       + e.getMessage());
    }
    
    /**
     *  Get warnings
     * @return List of warnings
     */
    public ArrayList getWarnings() {
        return warnings;
    }
    
    /**
     *  Get report of all errors.
     * @return Report of errors
     */
    public String getErrorReport() {
        StringBuffer sb = new StringBuffer();
        for (Iterator i = getErrors().iterator(); i.hasNext();){
            sb.append( (String) i.next() + "\n" );
        }
        return sb.toString();
    }
    
    /**
     *  Get report of all warnings.
     * @return Report of warnings
     */
    public String getWarningReport() {
        StringBuffer sb = new StringBuffer();
        for (Iterator i = getWarnings().iterator(); i.hasNext();){
            sb.append( (String) i.next() + "\n" );
        }
        return sb.toString();
    }
    
    
    /**
     *  Has validation errors.
     * @return TRUE when there are validation errors.
     */
    public boolean hasErrors(){
        return (errors.size()>0);
    }
    
    /**
     *  Has validation warnings.
     * @return TRUE when there are validation warnings.
     */
    public boolean hasWarnings(){
        return (warnings.size()>0);
    }
    
    /**
     *  Has validation errors and warnings.
     * @return TRUE when there are errors and warnings.
     */
    public boolean hasErrorsAndWarnings(){
        return ( hasErrors() || hasWarnings() );
    }
    
    /**
     *  Get errors as exception
     * @return XMLDBException object containing all errors.
     */
    public XMLDBException toException() {
        String errors = "";
        for (Iterator i = getErrors().iterator(); i.hasNext();)
            errors += (String) i.next() + "\n";
        return new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error validating: \n" + errors, null);
    }
    
}
