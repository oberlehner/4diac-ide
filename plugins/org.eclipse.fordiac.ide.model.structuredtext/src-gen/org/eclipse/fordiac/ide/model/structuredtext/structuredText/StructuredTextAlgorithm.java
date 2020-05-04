/**
 * generated by Xtext 2.21.0
 */
package org.eclipse.fordiac.ide.model.structuredtext.structuredText;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Algorithm</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextAlgorithm#getLocalVariables <em>Local Variables</em>}</li>
 *   <li>{@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextAlgorithm#getStatements <em>Statements</em>}</li>
 * </ul>
 *
 * @see org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage#getStructuredTextAlgorithm()
 * @model
 * @generated
 */
public interface StructuredTextAlgorithm extends EObject
{
  /**
   * Returns the value of the '<em><b>Local Variables</b></em>' containment reference list.
   * The list contents are of type {@link org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration}.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return the value of the '<em>Local Variables</em>' containment reference list.
   * @see org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage#getStructuredTextAlgorithm_LocalVariables()
   * @model containment="true"
   * @generated
   */
  EList<VarDeclaration> getLocalVariables();

  /**
   * Returns the value of the '<em><b>Statements</b></em>' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return the value of the '<em>Statements</em>' containment reference.
   * @see #setStatements(StatementList)
   * @see org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage#getStructuredTextAlgorithm_Statements()
   * @model containment="true"
   * @generated
   */
  StatementList getStatements();

  /**
   * Sets the value of the '{@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextAlgorithm#getStatements <em>Statements</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Statements</em>' containment reference.
   * @see #getStatements()
   * @generated
   */
  void setStatements(StatementList value);

} // StructuredTextAlgorithm
