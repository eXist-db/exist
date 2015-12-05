/****************************************************************************/
/*  File:       ExtensionHandler.java                                       */
/*  Author:     F. Georges                                                  */
/*  Company:    H2O Consulting                                              */
/*  Date:       2015-01-08                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2015 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.serial;

import javax.xml.namespace.QName;
import org.expath.tools.ToolsException;

/**
 * Handler for extension serialization parameters.
 * 
 * @author Florent Georges
 * @date   2015-01-08
 */
public interface ExtensionHandler
{
    /**
     * Get an extension output property.
     * 
     * If the property is not known by the specific implementation, it must
     * raise a technical exception.
     */
    public String getExtension(QName name)
            throws ToolsException;

    /**
     * Set an extension output property.
     * 
     * If the property is not known by the specific implementation, it must
     * raise a technical exception.
     */
    public void setExtension(QName name, String value)
            throws ToolsException;
}


/* ------------------------------------------------------------------------ */
/*  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS COMMENT.               */
/*                                                                          */
/*  The contents of this file are subject to the Mozilla Public License     */
/*  Version 1.0 (the "License"); you may not use this file except in        */
/*  compliance with the License. You may obtain a copy of the License at    */
/*  http://www.mozilla.org/MPL/.                                            */
/*                                                                          */
/*  Software distributed under the License is distributed on an "AS IS"     */
/*  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.  See    */
/*  the License for the specific language governing rights and limitations  */
/*  under the License.                                                      */
/*                                                                          */
/*  The Original Code is: all this file.                                    */
/*                                                                          */
/*  The Initial Developer of the Original Code is Florent Georges.          */
/*                                                                          */
/*  Contributor(s): none.                                                   */
/* ------------------------------------------------------------------------ */
