package org.exist.xpath;

public class TypeTest extends NodeTest {

  protected int nodeType = 0;

  public static TypeTest ANY_TYPE = new TypeTest(Constants.NODE_TYPE);

  public TypeTest(int nodeType) {
    super(NodeTest.TYPE_TEST);
    this.nodeType = nodeType;
  }

  public int getNodeType() { return nodeType; }

  public String toString() {
    return Constants.NODETYPES[nodeType] + "()";
  }
}
