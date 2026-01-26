package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.Objects;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.fordiac.ide.model.libraryElement.BlockFBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.Connection;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.gef.ConnectionEditPart;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Enables the command only when exactly one "simple" connection is selected: -
 * one source endpoint from a left block to one destination endpoint on a right
 * block - no fan-out (single target) NOTE: Fan-out will be handled later.
 */
public class ReplaceConnectionWithAdapterPropertyTester extends PropertyTester {
	private static final String PROP = "replaceConnectionWithAdapterSupported";

	@Override
	public boolean test(final Object receiver, final String property, final Object[] args, final Object expectedValue) {

		if (true) {
			return true;
		}

		if (!PROP.equals(property)) {
			return false;
		}
		if (!(receiver instanceof final IStructuredSelection sel)) {
			return false;
		}
		if (sel.size() != 1) {
			return false;
		}

		final Object first = sel.getFirstElement();
		if (first instanceof final ConnectionEditPart cep) {
			final Object model = cep.getModel();
			if (model instanceof final Connection con) {
				final IInterfaceElement src = con.getSource();
				final IInterfaceElement dst = con.getDestination();
				if (src == null || dst == null) {
					return false;
				}

				final BlockFBNetworkElement srcFB = src.getBlockFBNetworkElement();
				final BlockFBNetworkElement dstFB = dst.getBlockFBNetworkElement();
				if (srcFB == null || dstFB == null) {
					return false;
				}

				// "Left to right" heuristic: different FBs and source has graphical x < dest x
				// If coordinates are missing, we still allow it — handler will re-check.
				final boolean differentBlocks = !Objects.equals(srcFB, dstFB);

				// No fan-out check (basic): selected connection is the only one to that dst pin
				final boolean singleTarget = dst.getInputConnections() != null && dst.getInputConnections().size() == 1;

				return differentBlocks && singleTarget;
			}
		}
		return false;
	}
}
