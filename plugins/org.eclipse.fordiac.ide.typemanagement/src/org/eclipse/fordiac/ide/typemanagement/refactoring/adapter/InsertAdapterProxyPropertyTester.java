package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterConnection;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.gef.ConnectionEditPart;
import org.eclipse.jface.viewers.StructuredSelection;

/**
 * Enables "Insert Adapter Proxy" when the (popup) selection is exactly one
 * adapter connection in an FB network – same style as
 * ConnectionsToStructPropertyTester.
 */
public class InsertAdapterProxyPropertyTester extends PropertyTester {

	// plugin.xml uses namespace "org.eclipse.fordiac.ide.typemanagement" and
	// property "insertAdapterProxySupported"
	private static final String PROP = "insertAdapterProxySupported"; //$NON-NLS-1$

	@Override
	public boolean test(final Object receiver, final String property, final Object[] args, final Object expectedValue) {
		if (receiver == null) {
			return false;
		}

		if (receiver instanceof final StructuredSelection sel) {
			final Object firstElement = sel.getFirstElement();

			if (firstElement instanceof final ConnectionEditPart conEditPart) {
				final Object model = conEditPart.getModel();
				if (model instanceof AdapterConnection) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isAdapterDecl(final IInterfaceElement ie) {
		return ie instanceof AdapterDeclaration;
	}

	private static AdapterConnection toAdapterConnection(final Object o) {
		if (o instanceof final AdapterConnection a) {
			return a;
		}
		// common GEF case: EditPart#getModel() returns the EObject
		try {
			final var m = o.getClass().getMethod("getModel"); //$NON-NLS-1$
			final Object mdl = m.invoke(o);
			if (mdl instanceof final AdapterConnection a) {
				return a;
			}
			if (mdl instanceof final EObject eo && eo instanceof final AdapterConnection a2) {
				return a2;
			}
		} catch (final Exception ignore) {
			/* not an edit part */ }
		// IAdaptable fallback
		if (o instanceof final IAdaptable ada) {
			final Object adapted = ada.getAdapter(AdapterConnection.class);
			if (adapted instanceof final AdapterConnection a) {
				return a;
			}
		}
		return null;
	}
}
