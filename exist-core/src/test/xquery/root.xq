(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

module namespace rt = "http://exist-db.org/xquery/test/fn-root";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("true", "false", "false")
function rt:memtree-document() {
  let $x := document{()}
  return
    (
	    $x/root() instance of document-node(),
	    $x/ancestor::node() instance of document-node(),
	    $x/parent::node() instance of document-node()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "false", "false")
function rt:memtree-element() {
  let $x := element e1{}
  return
    (
	    $x/root() instance of document-node(),
	    $x/root() instance of element(),
	    $x/ancestor::node() instance of document-node(),
	    $x/ancestor::node() instance of element(),
	    $x/parent::node() instance of document-node(),
	    $x/parent::node() instance of element()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "false", "false")
function rt:memtree-attribute() {
  let $x := attribute a1{ "a1" }
  return
    (
	    $x/root() instance of document-node(),
	    $x/root() instance of attribute(),
	    $x/ancestor::node() instance of document-node(),
	    $x/ancestor::node() instance of attribute(),
	    $x/parent::node() instance of document-node(),
	    $x/parent::node() instance of attribute()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "false", "false")
function rt:memtree-comment() {
  let $x := comment { "c1" }
  return
    (
	    $x/root() instance of document-node(),
	    $x/root() instance of comment(),
	    $x/ancestor::node() instance of document-node(),
	    $x/ancestor::node() instance of comment(),
	    $x/parent::node() instance of document-node(),
	    $x/parent::node() instance of comment()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "false", "false")
function rt:memtree-processing-instruction() {
  let $x := processing-instruction p1 { "p1" }
  return
    (
	    $x/root() instance of document-node(),
	    $x/root() instance of processing-instruction(),
	    $x/ancestor::node() instance of document-node(),
	    $x/ancestor::node() instance of processing-instruction(),
	    $x/parent::node() instance of document-node(),
	    $x/parent::node() instance of processing-instruction()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "false", "false")
function rt:memtree-text() {
  let $x := text { "t1" }
  return
    (
        $x/root() instance of document-node(),
        $x/root() instance of text(),
        $x/ancestor::node() instance of document-node(),
        $x/ancestor::node() instance of text(),
        $x/parent::node() instance of document-node(),
        $x/parent::node() instance of text()
    )
};

declare
    %test:assertEquals("false", "true", "false", "true", "false", "true")
function rt:memtree-element-in-element() {
  let $x := element e1 {
    element e2{}
  }
  return
    (
	    root($x/e2) instance of document-node(),
        root($x/e2) instance of element(),
        $x/e2/ancestor::node() instance of document-node(),
        $x/e2/ancestor::node() instance of element(),
        $x/e2/parent::node() instance of document-node(),
        $x/e2/parent::node() instance of element()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "true", "false", "false", "true", "false")
function rt:memtree-attribute-in-element() {
  let $x := element e1 {
    attribute lang { "en" }
  }
  return
    (
        root($x/@lang) instance of document-node(),
        root($x/@lang) instance of element(),
        root($x/@lang) instance of attribute(),
        $x/@lang/ancestor::node() instance of document-node(),
        $x/@lang/ancestor::node() instance of element(),
        $x/@lang/ancestor::node() instance of attribute(),
        $x/@lang/parent::node() instance of document-node(),
        $x/@lang/parent::node() instance of element(),
        $x/@lang/parent::node() instance of attribute()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "true", "false", "false", "true", "false")
function rt:memtree-comment-in-element() {
  let $x := element e1 {
    comment { "c1" }
  }
  return
    (
        root($x/comment()) instance of document-node(),
        root($x/comment()) instance of element(),
        root($x/comment()) instance of comment(),
        $x/comment()/ancestor::node() instance of document-node(),
        $x/comment()/ancestor::node() instance of element(),
        $x/comment()/ancestor::node() instance of comment(),
        $x/comment()/parent::node() instance of document-node(),
        $x/comment()/parent::node() instance of element(),
        $x/comment()/parent::node() instance of comment()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "true", "false", "false", "true", "false")
function rt:memtree-processing-instruction-in-element() {
  let $x := element e1 {
    processing-instruction p1 { "p1" }
  }
  return
    (
        root($x/processing-instruction()) instance of document-node(),
        root($x/processing-instruction()) instance of element(),
        root($x/processing-instruction()) instance of processing-instruction(),
        $x/processing-instruction()/ancestor::node() instance of document-node(),
        $x/processing-instruction()/ancestor::node() instance of element(),
        $x/processing-instruction()/ancestor::node() instance of processing-instruction(),
        $x/processing-instruction()/parent::node() instance of document-node(),
        $x/processing-instruction()/parent::node() instance of element(),
        $x/processing-instruction()/parent::node() instance of processing-instruction()
    )
};

declare
    %test:assertEquals("false", "true", "false", "false", "true", "false", "false", "true", "false")
function rt:memtree-text-in-element() {
  let $x := element e1 {
    text { "t1" }
  }
  return
    (
        root($x/text()) instance of document-node(),
        root($x/text()) instance of element(),
        root($x/text()) instance of text(),
        $x/text()/ancestor::node() instance of document-node(),
        $x/text()/ancestor::node() instance of element(),
        $x/text()/ancestor::node() instance of text(),
        $x/text()/parent::node() instance of document-node(),
        $x/text()/parent::node() instance of element(),
        $x/text()/parent::node() instance of text()
    )
};