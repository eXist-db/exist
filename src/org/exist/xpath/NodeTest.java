
package org.exist.xpath;

public abstract class NodeTest {

   public final static int NAME_TEST = 0;
   public final static int TYPE_TEST = 1;
   public final static int ANY_TEST = 2;

   protected int type;
   protected String name;

   public NodeTest(int type, String name) {
      this.type = type;
      this.name = name;
   }

   public NodeTest(int type) {
      this(type, null);
   }

   public String getName() {
      return name;
   }

   public int getType() {
      return type;
   }

   public String toString() {
      return name;
   }
}
