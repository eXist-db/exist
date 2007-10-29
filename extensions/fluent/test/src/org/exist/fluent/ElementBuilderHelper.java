package org.exist.fluent;

import org.w3c.dom.*;
import org.w3c.dom.Node;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:48:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class ElementBuilderHelper extends DatabaseHelper {
    public void testEmpty() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(org.w3c.dom.Node[] node) {fail("completed called"); return null;}
        });
        builder.commit();
    }

    public void testElemOnStack() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.elem("blah").commit();
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testNoFragAllowed() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.elem("test").end("test").elem("test2");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testMismatchedEndSingle() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.elem("test").end("test2");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testMismatchedEndMultiple() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.elem("test").end("test2", "test3", "blah");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testElemAfterCommit() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {return null;}
        });
        try {
            builder.elem("test").end("test").commit();
            builder.elem("test2");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testAttrAfterCommit() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {return null;}
        });
        try {
            builder.elem("test").end("test").commit();
            builder.attr("test2","blah");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testTextAfterCommit() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {return null;}
        });
        try {
            builder.elem("test").end("test").commit();
            builder.text("test2");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testAttrWithoutElem1() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.attr("blah", "blah");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testAttrWithoutElem2() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.elem("test").end("test").attr("blah", "blah");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testTextWithoutElemNoFrag1() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.text("blah");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testTextWithoutElemNoFrag2() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {fail("completed called"); return null;}
        });
        try {
            builder.elem("test").end("test").text("blah");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    public void testTextWithoutElemFrag1() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {return null;}
        });
        builder.text("blah").commit();
    }

    public void testTextWithoutElemFrag2() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] node) {return null;}
        });
        builder.elem("test").end("test").text("blah").commit();
    }

    public void testCommitOne() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {assertEquals(1, nodes.length); assertEquals(Node.ELEMENT_NODE, nodes[0].getNodeType()); return null;}
        });
        builder.elem("test").end("test").commit();
    }

    public void testCommitFrag() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {assertEquals(2, nodes.length); assertEquals(Node.ELEMENT_NODE, nodes[0].getNodeType()); assertEquals(Node.ELEMENT_NODE, nodes[1].getNodeType()); return null;}
        });
        builder.elem("test").end("test").elem("test2").end("test2").commit();
    }

    public void testStructure1() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(1, nodes.length);
                assertEquals("<test/>", ElementBuilderHelper.toString(nodes[0]));
                return null;
            }
        });
        builder.elem("test").end("test").commit();
    }

    public void testStructure2() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(1, nodes.length);
                assertEquals("<test><test2/></test>", ElementBuilderHelper.toString(nodes[0]));
                return null;
            }
        });
        builder.elem("test").elem("test2").end("test2").end("test").commit();
    }

    public void testStructure3() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(1, nodes.length);
                assertEquals("<test foo='bar'/>", ElementBuilderHelper.toString(nodes[0]));
                return null;
            }
        });
        builder.elem("test").attr("foo", "bar").end("test").commit();
    }

    public void testStructure4() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(1, nodes.length);
                assertEquals("<test>blah</test>", ElementBuilderHelper.toString(nodes[0]));
                return null;
            }
        });
        builder.elem("test").text("blah").end("test").commit();
    }

    public void testStructure5() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(1, nodes.length);
                assertEquals("<test foo='bar'/>", ElementBuilderHelper.toString(nodes[0]));
                return null;
            }
        });
        builder.elem("test").attrIf(true, "foo", "bar").end("test").commit();
    }

    public void testStructure6() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(1, nodes.length);
                assertEquals("<test/>", ElementBuilderHelper.toString(nodes[0]));
                return null;
            }
        });
        builder.elem("test").attrIf(false, "foo", "bar").end("test").commit();
    }

    public void testFrag1() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(2, nodes.length);
                assertEquals("<test1/><test2/>", ElementBuilderHelper.toString(nodes[0]) + ElementBuilderHelper.toString(nodes[1]));
                return null;
            }
        });
        builder.elem("test1").end("test1").elem("test2").end("test2").commit();
    }

    public void testFrag2() {
        ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true, new ElementBuilder.CompletedCallback<Object>() {
            public Object completed(Node[] nodes) {
                assertEquals(2, nodes.length);
                assertEquals("blahfoo", ElementBuilderHelper.toString(nodes[0]) + ElementBuilderHelper.toString(nodes[1]));
                return null;
            }
        });
        builder.text("blah").text("foo").commit();
    }

    static String toString(Node node) {
        StringBuilder buf = new StringBuilder();
        toBuf(node, buf);
        return buf.toString();
    }

    private static void toBuf(Node node, StringBuilder buf) {
        switch (node.getNodeType()) {

            case Node.DOCUMENT_FRAGMENT_NODE: {
                NodeList children = node.getChildNodes();
                for (int i=0; i<children.getLength(); i++) toBuf(children.item(i), buf);
                break;
            }

            case Node.DOCUMENT_NODE:
                buf.append("<?xml version=\"1.0\" ?>");
                toBuf(((org.w3c.dom.Document) node).getDocumentElement(), buf);
                break;

            case Node.ELEMENT_NODE: {
                buf.append("<").append(node.getNodeName());
                NamedNodeMap attrs = node.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    Node attr = attrs.item(i);
                    buf.append(" ").append(attr.getNodeName()).append("='").append(attr.getNodeValue()).append("'");
                }
                NodeList children = node.getChildNodes();
                if (children.getLength() == 0) {
                    buf.append("/>");
                } else {
                    buf.append(">");
                    for (int i = 0; i < children.getLength(); i++) toBuf(children.item(i), buf);
                    buf.append("</").append(node.getNodeName()).append(">");
                }
                break;
            }

            case Node.ENTITY_REFERENCE_NODE:
                buf.append("&").append(node.getNodeName()).append(";");
                break;

            case Node.TEXT_NODE:
                buf.append(node.getNodeValue());
                break;

            default:
                throw new RuntimeException("can't deal with node type " + node.getNodeType());
        }

    }
}
