/*******************************************************************************
 * Copyright (c) 2012 fortiss GmbH
 *               2020 Johannes Kepler University
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Alois Zoitl
 *     - initial API and implementation and/or initial documentation
 *   Ernst Blecha
 *     - fix behaviour on passing null
 *******************************************************************************/
package org.eclipse.fordiac.ide.model.commands.change;

import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.fordiac.ide.model.commands.ScopedCommand;
import org.eclipse.fordiac.ide.model.libraryElement.VersionInfo;
import org.eclipse.gef.commands.Command;

/**
 * The Class ChangeOrganizationCommand.
 */
public class ChangeOrganizationCommand extends Command implements ScopedCommand {

	private final VersionInfo versionInfo;

	/** The new ApplicationDomain value. */
	private final String newOrganization;

	/** The old ApplicationDomain value. */
	private String oldOrganization;

	public ChangeOrganizationCommand(final VersionInfo versionInfo, final String newOrganization) {
		this.versionInfo = Objects.requireNonNull(versionInfo);
		this.newOrganization = (newOrganization == null) ? "" : newOrganization; //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.gef.commands.Command#execute()
	 */
	@Override
	public void execute() {
		oldOrganization = versionInfo.getOrganization();
		redo();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.gef.commands.Command#undo()
	 */
	@Override
	public void undo() {
		versionInfo.setOrganization(oldOrganization);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.gef.commands.Command#redo()
	 */
	@Override
	public void redo() {
		versionInfo.setOrganization(newOrganization);
	}

	@Override
	public Set<EObject> getAffectedObjects() {
		return Set.of(versionInfo);
	}
}
