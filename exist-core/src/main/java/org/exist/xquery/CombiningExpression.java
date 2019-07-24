/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Abstract base class for the XQuery/XPath combining operators "union", "intersect"
 * and "except".
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public abstract class CombiningExpression extends AbstractExpression {

	final protected PathExpr left;
	final protected PathExpr right;

	public CombiningExpression(final XQueryContext context, final PathExpr left, final PathExpr right) {
		super(context);
		this.left = left;
		this.right = right;
	}

	@Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        left.analyze(contextInfo);
        right.analyze(contextInfo);
    }

	@Override
	public final Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);
			context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null) {
				context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
			}
			if (contextItem != null) {
				context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
			}
		}

		final Sequence ls = left.eval(contextSequence, contextItem);
		final Sequence rs = right.eval(contextSequence, contextItem);
		ls.removeDuplicates();
		rs.removeDuplicates();

		final Sequence result = combine(ls, rs);

		if (context.getProfiler().isEnabled()) {
			context.getProfiler().end(this, "", result);
		}

		return result;
	}

	/**
	 * Combine the left and right sequences in some manner
	 *
	 * @param ls Left sequence
	 * @param rs Right sequence
	 *
	 * @throws XPathException in case of a dynamic error
	 * @return The combined result
	 */
	protected abstract Sequence combine(final Sequence ls, final Sequence rs) throws XPathException;

	@Override
	public final void dump(final ExpressionDumper dumper) {
		left.dump(dumper);
		dumper.display(" " + getOperatorName() + " ");
		right.dump(dumper);
	}

	@Override
	public String toString() {
		return left.toString() + " " + getOperatorName() + " " + right.toString();
	}

	/**
	 * Get the Name of the operator
	 *
	 * @return the name of the operator
	 */
	protected abstract String getOperatorName();

	@Override
	public int returnsType() {
		return Type.NODE;
	}

	@Override
	public void setContextDocSet(final DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		left.setContextDocSet(contextSet);
		right.setContextDocSet(contextSet);
	}

	@Override
	public void resetState(final boolean postOptimization) {
		super.resetState(postOptimization);
		left.resetState(postOptimization);
		right.resetState(postOptimization);
	}

	@Override
	public void setPrimaryAxis(final int axis) {
		left.setPrimaryAxis(axis);
		right.setPrimaryAxis(axis);
	}

	@Override
	public int getPrimaryAxis() {
		// just return left axis to indicate that we know the axis
		return left.getPrimaryAxis();
	}

    @Override
    public void accept(final ExpressionVisitor visitor) {
        left.accept(visitor);
        right.accept(visitor);
    }
}
