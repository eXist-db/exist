/****************************************************************************/
/*  File:       DomAttribute.java                                           */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2015-01-08                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2015 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.model.dom;

import org.expath.tools.ToolsException;
import org.expath.tools.model.Attribute;
import org.w3c.dom.Attr;

/**
 * Trivial, in-memory implementation, for test purposes.
 *
 * @author Florent Georges
 * @date   2015-01-08
 */
public class DomAttribute
        implements Attribute
{
    public DomAttribute(Attr attr)
    {
        myAttr = attr;
    }

    @Override
    public String getLocalName()
    {
        return myAttr.getName();
    }

    @Override
    public String getNamespaceUri()
    {
        String ns = myAttr.getNamespaceURI();
        return ns == null ? "" : ns;
    }

    @Override
    public String getValue()
    {
        return myAttr.getValue();
    }

    @Override
    public boolean getBoolean()
            throws ToolsException
    {
        String value = getValue();
        if ( "1".equals(value) ) {
            return true;
        }
        else {
            return Boolean.parseBoolean(value);
        }
    }

    @Override
    public int getInteger()
            throws ToolsException
    {
        String value = getValue();
        return Integer.parseInt(value);
    }

    private final Attr myAttr;
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
