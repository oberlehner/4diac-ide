/*******************************************************************************
 * Copyright (c) 2020 Johannes Kepler University Linz
 * 				 2021 Primetals Technologies Austria GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 *   Ernst Blecha - initial API and implementation and/or initial documentation
 *   Martin Melik Merkumians - fixes partial index validator for primary variables
 * 		and adds one for adapter variables
 *******************************************************************************/

/*
 * generated by Xtext 2.20.0
 */
package org.eclipse.fordiac.ide.model.structuredtext.validation

import org.eclipse.fordiac.ide.model.structuredtext.structuredText.PartialAccess
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.StructuredTextPackage
import org.eclipse.xtext.validation.Check
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.PrimaryVariable
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.AdapterVariable
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.Variable
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.LocalVariable
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.TimeLiteral
import org.eclipse.fordiac.ide.model.FordiacKeywords
import org.eclipse.fordiac.ide.model.structuredtext.structuredText.AdapterRoot
import org.eclipse.fordiac.ide.model.datatype.helper.IecTypes.ElementaryTypes
import org.eclipse.fordiac.ide.model.data.DataType

/**
 * This class contains custom validation rules.
 *
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
class StructuredTextValidator extends AbstractStructuredTextValidator {
	
	def private isPartialIndexInDataType(Variable variable) {
		val varBitSize = variable.BitSize
		val partSize = variable.part.BitSize
		val index = variable.part.index
		val startIndex = index * partSize
		val endIndex = startIndex + partSize
		endIndex > varBitSize
	}

	@Check
	def checkPartialAccess(PrimaryVariable v) {
		if(v.part !== null) {
			if(v.isPartialIndexInDataType) {
				error("Incorrect partial access: index not within limits.", StructuredTextPackage.Literals.PRIMARY_VARIABLE__VAR)
			}
		}
	}
	
	@Check
	def checkPartialAccess(AdapterVariable v) {
		if(v.part !== null) {			
			if(v.isPartialIndexInDataType) {
				error("Incorrect partial access: index not within limits.", StructuredTextPackage.Literals.ADAPTER_VARIABLE__VAR)
			}
		}
	}
	
	def private dispatch int BitSize(PartialAccess part) {
		if(part !== null) {
			if(part.bitaccess)			ElementaryTypes.BOOL.BitSize
			else if(part.byteaccess)	ElementaryTypes.BYTE.BitSize
			else if(part.wordaccess)	ElementaryTypes.WORD.BitSize
			else if(part.dwordaccess)	ElementaryTypes.DWORD.BitSize
			else					    0
		} else 0
	}
	
	def private dispatch int BitSize(PrimaryVariable variable) {
		variable.^var.type.BitSize
	}
	
	def private dispatch int BitSize(AdapterVariable variable) {
		variable.^var.type.BitSize
	}
	
	def private dispatch int BitSize(DataType type) {
		switch(type) {
			case ElementaryTypes.LWORD: 64
			case ElementaryTypes.DWORD: 32
			case ElementaryTypes.WORD: 16
			case ElementaryTypes.BYTE: 8
			case ElementaryTypes.BOOL: 1
			default: 0
		}
	}
	
	def private dispatch int BitSize(VarDeclaration v) { BitSize(v.extractTypeInformation)	}
	
	def private dispatch int BitSize(LocalVariable v) { BitSize(v.extractTypeInformation) }
	
	def private dispatch int BitSize(String str) {
		switch (str) {
			case str.equals(FordiacKeywords.LWORD): 64
			case str.equals(FordiacKeywords.DWORD): 32
			case str.equals(FordiacKeywords.WORD):  16
			case str.equals(FordiacKeywords.BYTE):   8
			case str.equals(FordiacKeywords.BOOL):   1
			default:                   0
		}
	}

	def private dispatch String extractTypeInformation(PartialAccess part, String DataType) {
		if (null !== part) {
			if (part.bitaccess)        FordiacKeywords.BOOL
			else if (part.byteaccess)  FordiacKeywords.BYTE
			else if (part.wordaccess)  FordiacKeywords.WORD
			else if (part.dwordaccess) FordiacKeywords.DWORD
			else                       ""
		} else                         DataType
	}

	def private dispatch String extractTypeInformation(PrimaryVariable variable, String DataType) {
		if (null !== variable && null !== variable.part) variable.part.extractTypeInformation
		else                                             variable.^var.type.name
	}

	def private dispatch String extractTypeInformation(Variable variable, String DataType) {
		if (null !== variable && null !== variable.part) variable.part.extractTypeInformation
		else                                             ""
	}

	def protected dispatch String extractTypeInformation(PrimaryVariable variable) {
		variable.extractTypeInformation(variable.^var.extractTypeInformation)
	}

	def protected dispatch String extractTypeInformation(VarDeclaration variable) {	variable.type.name }
	
	def protected dispatch String extractTypeInformation(AdapterVariable variable) { 
		val head = variable.curr;
        switch (head) {
        	AdapterRoot: head.adapter.type.name
        	AdapterVariable: head.^var.type.name
        	default: ""
        }
	}

	@Check
	def checkLocalVariable(LocalVariable v) {
		if (v.located && v.initalized) {
			error("Located variables can not be initialized.", StructuredTextPackage.Literals.LOCAL_VARIABLE__INITIAL_VALUE);
		} else if (v.array && v.initalized) {
			error("Local arrays can not be initialized.", StructuredTextPackage.Literals.LOCAL_VARIABLE__INITIAL_VALUE);		
		}
	}
	
	@Check
	def checkArray(LocalVariable v) {
		if (v.array) {
			if (v.arrayStart != 0) error("Only arrays with a start index of 0 are supported.", StructuredTextPackage.Literals.LOCAL_VARIABLE__ARRAY);
			if (v.arrayStart >= v.arrayStop) error("Only arrays with incrementing index are supported.", StructuredTextPackage.Literals.LOCAL_VARIABLE__ARRAY);
		}
	}
	
	def private extractArraySize(VarDeclaration v) {
		if (v instanceof LocalVariable)
			return v.arrayStop - v.arrayStart + 1
		else
			return v.arraySize
	}
	
	@Check
	def checkAtLocation(LocalVariable v) {
		if (v.located && null !== v.location) {
			if ((v.location.BitSize == 0 || v.BitSize == 0) && v.array)
				error("Piecewise located variables are allowed only for variables of type ANY_BIT", StructuredTextPackage.Literals.LOCAL_VARIABLE__LOCATED)
			if (v.location.BitSize > 0 && v.BitSize > 0 && v.array && v.extractArraySize * v.BitSize > v.location.BitSize) 
				error("Piecewise located variables cannot access more bits than are available in the destination", StructuredTextPackage.Literals.LOCAL_VARIABLE__LOCATED)
			if (v.BitSize == 0 && v.location.BitSize == 0 && !(v.location.extractTypeInformation(v.location.extractTypeInformation) == v.extractTypeInformation))
				error("General located variables must have matching types", StructuredTextPackage.Literals.LOCAL_VARIABLE__LOCATED)
		}
	}
	
	@Check 
	def validateTimeLiteral(TimeLiteral expr){
		val literal = new DatetimeLiteral(expr.literal)
		if (!literal.isValid()) {
			error("Invalid Literal", StructuredTextPackage.Literals.TIME_LITERAL__LITERAL);
		}
	}

}