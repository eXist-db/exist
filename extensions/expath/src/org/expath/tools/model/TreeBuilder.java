/****************************************************************************/
/*  File:       TreeBuilder.java                                            */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-03-10                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.model;

import org.expath.tools.ToolsException;

/**
 * A generic interface to build a tree, independent on any processor.
 *
 * @author Florent Georges
 * @date   2011-03-10
 */
public interface TreeBuilder
{
    /**
     * Open an element in this tree builder namespace.
     * 
     * @param localname The local name of the element to open.
     * 
     * @throws ToolsException If there is any error opening the element.
     */
    public void startElem(String localname)
            throws ToolsException;

    /**
     * Create an attribute in no namespace.
     * 
     * @param localname The local name of the attribute to create.
     * 
     * @param value The string value of the attribute to create.
     * 
     * @throws ToolsException If there is any error creating the attribute.
     */
    public void attribute(String localname, CharSequence value)
            throws ToolsException;

    /**
     * Allow putting content in an open element.
     * 
     * Once an attribute has been open, you can add attributes.  In order to
     * add child elements, you have to call this method before.
     * 
     * You can see {@code openElement} as opening the opening tag, then you can
     * add as many attribute as you want, then you have to "close the opening
     * tag" before adding any content to the element.
     * 
     * @throws ToolsException If there is any error starting content.
     */
    public void startContent()
            throws ToolsException;

    /**
     * Close the current element.
     * 
     * @throws ToolsException If there is any error closing the current element.
     */
    public void endElem()
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
