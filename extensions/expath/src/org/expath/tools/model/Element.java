/****************************************************************************/
/*  File:       Element.java                                                */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-03-09                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.model;

import javax.xml.namespace.QName;
import org.expath.tools.ToolsException;

/**
 * An abstract representation of an element (just provide the HTTP Client needs).
 *
 * @author Florent Georges
 * @date   2011-03-09
 */
public interface Element
{
    /**
     * Get the local part of the name of the element.
     * 
     * @return The local part of the name of the element, cannot be {@code null}
     * nor empty.
     */
    public String getLocalName();

    /**
     * Return the namespace URI part of the name of the element.
     *
     * @return The empty string if the name is in no namespace (never return
     * {@code null}).
     */
    public String getNamespaceUri();

    /**
     * Get the display name of the element.
     *
     * The display name is the original lexical name (with the original prefix
     * if any).  An implementation is not required to return the exact same
     * name as the original, and can instead make a "best guess".  This is for
     * reporting purpose only.
     * 
     * @return The display name of the element, cannot be {@code null} nor empty.
     */
    public String getDisplayName();

    /**
     * Return the value of an attribute.
     *
     * @param local_name The local name of the attribute to look for.  The
     * attribute is looked for in no namespace.
     *
     * @return The value of the attribute, or null if it does not exist.
     */
    public String getAttribute(String local_name);

    /**
     * Iterate through the attributes.
     * 
     * @return The iterator.
     */
    public Iterable<Attribute> attributes();

    /**
     * Return true if this element has at least one child in no namespace.
     * 
     * @return {@code true} if there is any child in no namespace.
     */
    public boolean hasNoNsChild();

    /**
     * Iterate through the children elements.
     * 
     * @return The iterator.
     */
    public Iterable<Element> children();

    /**
     * Iterate through the children elements in a specific namespace.
     * 
     * @param ns The namespace to use.
     * 
     * @return The iterator.
     */
    public Iterable<Element> children(String ns);

    /**
     * Check the element {@code elem} does not have attributes other than {@code names}.
     *
     * {@code names} contains non-qualified names, for allowed attributes.  The
     * element can have other attributes in other namespace (not in the forbidden
     * namespaces) but no attributes in no namespace.
     *
     * @param names The non-qualified names of allowed attributes (cannot be
     * null, but can be empty.)
     *
     * @param forbidden_ns The forbidden namespaces, no attribute can be in any
     * of those namespaces.
     *
     * @throws ToolsException If the element contains an attribute in any of the
     * forbidden namespaces, or in no namespace and the name of which is not in
     * {@code names}.
     */
    public void noOtherNCNameAttribute(String[] names, String[] forbidden_ns)
            throws ToolsException;

    /**
     * Return the content of the element (the content of the child:: axis).
     * 
     * @return The sequence.
     */
    public Sequence getContent();

    /**
     * Parse a literal QName using the namespace bindings in scope on the element.
     * 
     * @param value The literal QName to parse.
     * 
     * @return The parsed QName.
     */
    public QName parseQName(String value)
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
