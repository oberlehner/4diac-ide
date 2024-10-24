/**
 * *******************************************************************************
 * Copyright (c) 2022 Primetals Technologies GmbH,
 *               2022 Martin Erich Jobst
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *    Martin Jobst, Martin Melik Merkumians
 *      - initial API and implementation and/or initial documentation
 * *******************************************************************************
 */
package org.eclipse.fordiac.ide.structuredtextcore.stcore;

import org.eclipse.fordiac.ide.model.libraryElement.INamedElement;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>ST Unary Expression</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryExpression#getOp <em>Op</em>}</li>
 *   <li>{@link org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryExpression#getExpression <em>Expression</em>}</li>
 * </ul>
 *
 * @see org.eclipse.fordiac.ide.structuredtextcore.stcore.STCorePackage#getSTUnaryExpression()
 * @model
 * @generated
 */
public interface STUnaryExpression extends STExpression {
	/**
	 * Returns the value of the '<em><b>Op</b></em>' attribute.
	 * The literals are from the enumeration {@link org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryOperator}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Op</em>' attribute.
	 * @see org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryOperator
	 * @see #setOp(STUnaryOperator)
	 * @see org.eclipse.fordiac.ide.structuredtextcore.stcore.STCorePackage#getSTUnaryExpression_Op()
	 * @model
	 * @generated
	 */
	STUnaryOperator getOp();

	/**
	 * Sets the value of the '{@link org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryExpression#getOp <em>Op</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Op</em>' attribute.
	 * @see org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryOperator
	 * @see #getOp()
	 * @generated
	 */
	void setOp(STUnaryOperator value);

	/**
	 * Returns the value of the '<em><b>Expression</b></em>' containment reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Expression</em>' containment reference.
	 * @see #setExpression(STExpression)
	 * @see org.eclipse.fordiac.ide.structuredtextcore.stcore.STCorePackage#getSTUnaryExpression_Expression()
	 * @model containment="true"
	 * @generated
	 */
	STExpression getExpression();

	/**
	 * Sets the value of the '{@link org.eclipse.fordiac.ide.structuredtextcore.stcore.STUnaryExpression#getExpression <em>Expression</em>}' containment reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Expression</em>' containment reference.
	 * @see #getExpression()
	 * @generated
	 */
	void setExpression(STExpression value);

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @model kind="operation"
	 * @generated
	 */
	INamedElement getResultType();

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @model kind="operation"
	 * @generated
	 */
	INamedElement getDeclaredResultType();

} // STUnaryExpression
