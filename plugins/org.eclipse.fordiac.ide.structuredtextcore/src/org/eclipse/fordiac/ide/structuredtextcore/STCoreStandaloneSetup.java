/*******************************************************************************
 * Copyright (c) 2021 Primetals Technologies GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Martin Melik Merkumians
 *       - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.structuredtextcore;

import org.eclipse.fordiac.ide.structuredtextcore.stcore.STCorePackage;

import com.google.inject.Injector;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class STCoreStandaloneSetup extends STCoreStandaloneSetupGenerated {

	public static void doSetup() {
		new STCoreStandaloneSetup().createInjectorAndDoEMFRegistration();
	}

	@Override
	public void register(final Injector injector) {
		STCorePackage.eINSTANCE.eClass();
		super.register(injector);
	}
}
