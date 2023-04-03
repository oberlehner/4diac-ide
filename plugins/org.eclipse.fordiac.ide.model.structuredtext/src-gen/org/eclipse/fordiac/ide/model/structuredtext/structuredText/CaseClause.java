/**
 * generated by Xtext 2.25.0
 */
package org.eclipse.fordiac.ide.model.structuredtext.structuredText;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Case Clause</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.CaseClause#getCase <em>Case</em>}</li>
 *   <li>{@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.CaseClause#getStatements <em>Statements</em>}</li>
 * </ul>
 *
 * @see org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage#getCaseClause()
 * @model
 * @generated
 */
public interface CaseClause extends EObject
{
  /**
	 * Returns the value of the '<em><b>Case</b></em>' containment reference list.
	 * The list contents are of type {@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.Constant}.
	 * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
	 * @return the value of the '<em>Case</em>' containment reference list.
	 * @see org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage#getCaseClause_Case()
	 * @model containment="true"
	 * @generated
	 */
  EList<Constant> getCase();

  /**
	 * Returns the value of the '<em><b>Statements</b></em>' containment reference.
	 * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
	 * @return the value of the '<em>Statements</em>' containment reference.
	 * @see #setStatements(StatementList)
	 * @see org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage#getCaseClause_Statements()
	 * @model containment="true"
	 * @generated
	 */
  StatementList getStatements();

  /**
	 * Sets the value of the '{@link org.eclipse.fordiac.ide.model.structuredtext.structuredText.CaseClause#getStatements <em>Statements</em>}' containment reference.
	 * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Statements</em>' containment reference.
	 * @see #getStatements()
	 * @generated
	 */
  void setStatements(StatementList value);

} // CaseClause