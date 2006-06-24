/*
 * NodeFilter.java
 *
 * Created on June 20, 2006, 12:31 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.util;

import org.w3c.dom.Node;

/**
 *
 * @author R. Alexander Milowski
 */
public interface NodeHandler {
   void process(Node parent,Node input);
}
