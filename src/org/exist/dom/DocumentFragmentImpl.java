
package org.exist.dom;

import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;

class DocumentFragmentImpl extends NodeImpl implements DocumentFragment {
   
   public DocumentFragmentImpl() {
      super();
   }

   public DocumentFragmentImpl(long gid) {
      super(Node.DOCUMENT_FRAGMENT_NODE, gid);
   }
}
