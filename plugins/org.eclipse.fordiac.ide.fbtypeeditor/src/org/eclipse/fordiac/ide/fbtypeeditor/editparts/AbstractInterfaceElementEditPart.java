/*******************************************************************************
 * Copyright (c) 2011 - 2017 Profactor GmbH, fortiss GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Gerhard Ebenhofer, Alois Zoitl, Monika Wenger
 *     - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.fbtypeeditor.editparts;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fordiac.ide.gef.editparts.AbstractDirectEditableEditPart;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.FunctionFBType;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;

abstract class AbstractInterfaceElementEditPart extends AbstractDirectEditableEditPart {
	public abstract IInterfaceElement getCastedModel();

	private Adapter adapter;

	private Adapter getAdapter() {
		if (adapter == null) {
			adapter = createAdapter();
		}
		return adapter;
	}

	protected Adapter createAdapter() {
		return new AdapterImpl() {
			@Override
			public void notifyChanged(final Notification notification) {
				super.notifyChanged(notification);
				refresh();
			}
		};
	}

	@Override
	public void activate() {
		super.activate();
		getCastedModel().eAdapters().add(getAdapter());
	}

	@Override
	public void deactivate() {
		super.deactivate();
		getCastedModel().eAdapters().remove(getAdapter());
	}

	public boolean isInput() {
		return getCastedModel().isIsInput();
	}

	public boolean isEvent() {
		return getCastedModel() instanceof Event;
	}

	public boolean isVariable() {
		return getCastedModel() instanceof VarDeclaration;
	}

	public boolean isAdapter() {
		return getCastedModel() instanceof AdapterDeclaration;
	}

	@Override
	public boolean isConnectable() {
		return isInterfaceEditable();
	}

	@Override
	public boolean isDirectEditable() {
		return isInterfaceEditable();
	}

	public boolean isInterfaceEditable() {
		return !(EcoreUtil.getRootContainer(getCastedModel()) instanceof FunctionFBType);
	}
}
