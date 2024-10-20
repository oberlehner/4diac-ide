/*******************************************************************************
 * Copyright (c) 2023 Martin Erich Jobst
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Martin Jobst - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.globalconstantseditor.ui.document;

import org.eclipse.fordiac.ide.structuredtextcore.ui.document.LibraryElementXtextDocument;
import org.eclipse.xtext.ui.editor.model.DocumentTokenSource;
import org.eclipse.xtext.ui.editor.model.edit.ITextEditComposer;

import com.google.inject.Inject;

public class GlobalConstantsDocument extends LibraryElementXtextDocument {

	@Inject
	public GlobalConstantsDocument(final DocumentTokenSource tokenSource, final ITextEditComposer composer) {
		super(tokenSource, composer);
	}
}