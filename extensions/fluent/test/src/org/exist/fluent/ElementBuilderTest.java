package org.exist.fluent;

import org.junit.Test;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class ElementBuilderTest extends DatabaseTestCase {
	@Test public void empty() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(org.w3c.dom.Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.commit();
	}

	@Test(expected = IllegalStateException.class)
	public void elemOnStack() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.elem("blah").commit();
	}

	@Test(expected = IllegalStateException.class)
	public void noFragAllowed() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.elem("test").end("test").elem("test2");
	}

	@Test(expected = IllegalStateException.class)
	public void mismatchedEndSingle() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.elem("test").end("test2");
	}

	@Test(expected = IllegalStateException.class)
	public void mismatchedEndMultiple() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.elem("test").end("test2", "test3", "blah");
	}

	@Test(expected = IllegalStateException.class)
	public void elemAfterCommit() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						return null;
					}
				});
		builder.elem("test").end("test").commit();
		builder.elem("test2");
	}

	@Test(expected = IllegalStateException.class)
	public void attrAfterCommit() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						return null;
					}
				});
		builder.elem("test").end("test").commit();
		builder.attr("test2", "blah");
	}

	@Test(expected = IllegalStateException.class)
	public void textAfterCommit() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						return null;
					}
				});
		builder.elem("test").end("test").commit();
		builder.text("test2");
	}

	@Test(expected = IllegalStateException.class)
	public void attrWithoutElem1() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.attr("blah", "blah");
	}

	@Test(expected = IllegalStateException.class)
	public void attrWithoutElem2() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.elem("test").end("test").attr("blah", "blah");
	}

	@Test(expected = IllegalStateException.class)
	public void textWithoutElemNoFrag1() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.text("blah");
	}

	@Test(expected = IllegalStateException.class)
	public void textWithoutElemNoFrag2() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						fail("completed called");
						return null;
					}
				});
		builder.elem("test").end("test").text("blah");
	}

	@Test public void textWithoutElemFrag1() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						return null;
					}
				});
		builder.text("blah").commit();
	}

	@Test public void textWithoutElemFrag2() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] node) {
						return null;
					}
				});
		builder.elem("test").end("test").text("blah").commit();
	}

	@Test public void commitOne() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals(Node.ELEMENT_NODE, nodes[0].getNodeType());
						return null;
					}
				});
		builder.elem("test").end("test").commit();
	}

	@Test public void commitFrag() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(2, nodes.length);
						assertEquals(Node.ELEMENT_NODE, nodes[0].getNodeType());
						assertEquals(Node.ELEMENT_NODE, nodes[1].getNodeType());
						return null;
					}
				});
		builder.elem("test").end("test").elem("test2").end("test2").commit();
	}

	@Test public void structure1() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals("<test/>", ElementBuilderTest.toString(nodes[0]));
						return null;
					}
				});
		builder.elem("test").end("test").commit();
	}

	@Test public void structure2() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals("<test><test2/></test>", ElementBuilderTest
								.toString(nodes[0]));
						return null;
					}
				});
		builder.elem("test").elem("test2").end("test2").end("test").commit();
	}

	@Test public void structure3() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals("<test foo='bar'/>", ElementBuilderTest
								.toString(nodes[0]));
						return null;
					}
				});
		builder.elem("test").attr("foo", "bar").end("test").commit();
	}

	@Test public void structure4() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals("<test>blah</test>", ElementBuilderTest
								.toString(nodes[0]));
						return null;
					}
				});
		builder.elem("test").text("blah").end("test").commit();
	}

	@Test public void structure5() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals("<test foo='bar'/>", ElementBuilderTest
								.toString(nodes[0]));
						return null;
					}
				});
		builder.elem("test").attrIf(true, "foo", "bar").end("test").commit();
	}

	@Test public void structure6() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), false,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(1, nodes.length);
						assertEquals("<test/>", ElementBuilderTest.toString(nodes[0]));
						return null;
					}
				});
		builder.elem("test").attrIf(false, "foo", "bar").end("test").commit();
	}

	@Test public void frag1() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(2, nodes.length);
						assertEquals("<test1/><test2/>", ElementBuilderTest
								.toString(nodes[0])
								+ ElementBuilderTest.toString(nodes[1]));
						return null;
					}
				});
		builder.elem("test1").end("test1").elem("test2").end("test2").commit();
	}

	@Test public void frag2() {
		ElementBuilder<Object> builder = new ElementBuilder<Object>(db.namespaceBindings(), true,
				new ElementBuilder.CompletedCallback<Object>() {
					public Object completed(Node[] nodes) {
						assertEquals(2, nodes.length);
						assertEquals("blahfoo", ElementBuilderTest.toString(nodes[0])
								+ ElementBuilderTest.toString(nodes[1]));
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
				for (int i = 0; i < children.getLength(); i++)
					toBuf(children.item(i), buf);
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
					buf.append(" ").append(attr.getNodeName()).append("='").append(
							attr.getNodeValue()).append("'");
				}
				NodeList children = node.getChildNodes();
				if (children.getLength() == 0) {
					buf.append("/>");
				} else {
					buf.append(">");
					for (int i = 0; i < children.getLength(); i++)
						toBuf(children.item(i), buf);
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
				throw new RuntimeException("can't deal with node type "
						+ node.getNodeType());
		}

	}
}
