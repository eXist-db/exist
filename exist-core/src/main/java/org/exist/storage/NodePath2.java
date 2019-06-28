/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.exist.dom.INode;
import org.xml.sax.Attributes;

import java.util.HashMap;

/**
 * @author <a href="mailto:stenlee@gmail.com">Stanislav Jordanov</a>
 * @version 1.0
 *
 * This is an extension of class NodePath, that keeps track of a *real* node/element path, not just a QName path
 * as its base class -- NodePath does.
 * This is required in order to implement the feature requested/discussed here:
 * @see <a href='https://sourceforge.net/p/exist/mailman/message/36392026/'>
 * [Exist-open] Are more elaborate xpath expressions allowed in Lucene's index config &lt;text match='...'/&gt;
 * </a>
 */
public class NodePath2 extends NodePath {

    private final static Logger LOG = LogManager.getLogger(NodePath2.class);

    private HashMap<String, String> attribs[] = new HashMap[4];

    private int n_pos = 0;

    public NodePath2() {
        super();
    }

    public NodePath2(NodePath2 o) {
        super(o);
        n_pos = o.n_pos;
        attribs = new HashMap[n_pos];
        for (int i = 0; i < n_pos; i++) {
            attribs[i] = o.attribs(i);
        }
    }


    public void addNode(final Node node) {
        addNode(node, null);
    }

    public void addNode(final Node node, Attributes saxAttribs) {
        assert node instanceof Element;

        super.addComponent(((INode) node).getQName());

        if (n_pos == attribs.length) {
            //final HashMap<String, String>[] t = new HashMap[n_pos + 4];
            final HashMap[] t = new HashMap[n_pos + 4];
            System.arraycopy(attribs, 0, t, 0, n_pos);
            attribs = t;
        }

        HashMap<String, String> amap = new HashMap<>();

        if (saxAttribs != null) {
            int alen = saxAttribs.getLength();

            for (int i = 0; i < alen; ++i) {
                amap.put(saxAttribs.getQName(i), saxAttribs.getValue(i));
            }
        } else {
            NamedNodeMap nnm = node.getAttributes();
            int alen = node.getAttributes().getLength();

            for (int i = 0; i < alen; ++i) {
                Node child = nnm.item(i);
                if (child.getNodeType() == Node.ATTRIBUTE_NODE)
                    amap.put(child.getNodeName(), child.getNodeValue());
            }
        }

        attribs[n_pos++] = amap;
    }


    public void reverseNodes() {
        super.reverseComponents();
        for (int i = 0; i < n_pos / 2; ++i) {
            HashMap tmp = attribs[i];
            attribs[i] = attribs[attribs.length - 1 - i];
            attribs[attribs.length - 1 - i] = tmp;
        }
    }


    public void removeLastNode() {
        super.removeLastComponent();

        if (n_pos > 0) {
            attribs[--n_pos] = null;
        }
    }


    public void removeLastComponent() {
        if (this.length() <= n_pos) {
            LOG.error("Whoa!!! addNode() possibly paired with removeLastComponent() instead of removeLastNode()");
        }
        super.removeLastComponent();
    }


    public void reset() {
        super.reset();

        for (int i = 0; i < n_pos; i++) {
            attribs[i] = null;
        }
    }


    //public HashMap<String, String>  attribs(int elementIdx) {
    public HashMap<String, String> attribs(int elementIdx) {
        return attribs[elementIdx];
    }
}
