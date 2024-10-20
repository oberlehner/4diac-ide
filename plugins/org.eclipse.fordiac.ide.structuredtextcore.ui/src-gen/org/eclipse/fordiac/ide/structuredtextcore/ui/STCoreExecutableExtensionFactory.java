/*******************************************************************************
 * Copyright (c) 2021, 2023 Primetals Technologies GmbH, 
 *                          Martin Erich Jobst
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Martin Melik Merkumians, Martin Jobst
 *       - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.structuredtextcore.ui;

import com.google.inject.Injector;
import org.eclipse.fordiac.ide.structuredtextcore.ui.internal.StructuredtextcoreActivator;
import org.eclipse.xtext.ui.guice.AbstractGuiceAwareExecutableExtensionFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * This class was generated. Customizations should only happen in a newly
 * introduced subclass. 
 */
public class STCoreExecutableExtensionFactory extends AbstractGuiceAwareExecutableExtensionFactory {

	@Override
	protected Bundle getBundle() {
		return FrameworkUtil.getBundle(StructuredtextcoreActivator.class);
	}
	
	@Override
	protected Injector getInjector() {
		StructuredtextcoreActivator activator = StructuredtextcoreActivator.getInstance();
		return activator != null ? activator.getInjector(StructuredtextcoreActivator.ORG_ECLIPSE_FORDIAC_IDE_STRUCTUREDTEXTCORE_STCORE) : null;
	}

}