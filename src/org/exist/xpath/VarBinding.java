
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

public class VarBinding extends Step {

  protected String name;
  protected Expression binding = null;

  public VarBinding(BrokerPool pool, String name, Expression binding) {
    super(pool, 0, null);
    this.name = name;
    this.binding = binding;
  }

  public VarBinding(BrokerPool pool, String name) {
    super(pool, 0, null);
    this.name = name;
  }

  public String getName() { return name; }

  public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
    if(binding == null)
      throw new IllegalArgumentException("variable " + name + " unbound");
    return binding.eval(docs, context, node);
  }

  public String pprint() {
    return " $" + name;
  }
}
