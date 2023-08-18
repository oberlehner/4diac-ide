/**
 * Copyright (c) 2022 - 2023 Martin Erich Jobst
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *   Martin Jobst - initial API and implementation and/or initial documentation
 */
package org.eclipse.fordiac.ide.export.forte_ng.util

import java.util.regex.Pattern
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.fordiac.ide.model.data.ArrayType
import org.eclipse.fordiac.ide.model.data.DataType
import org.eclipse.fordiac.ide.model.data.DateAndTimeType
import org.eclipse.fordiac.ide.model.data.DateType
import org.eclipse.fordiac.ide.model.data.LdateType
import org.eclipse.fordiac.ide.model.data.LdtType
import org.eclipse.fordiac.ide.model.data.LtimeType
import org.eclipse.fordiac.ide.model.data.LtodType
import org.eclipse.fordiac.ide.model.data.StringType
import org.eclipse.fordiac.ide.model.data.TimeOfDayType
import org.eclipse.fordiac.ide.model.data.TimeType
import org.eclipse.fordiac.ide.model.data.WstringType
import org.eclipse.fordiac.ide.model.datatype.helper.IecTypes.GenericTypes
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration
import org.eclipse.fordiac.ide.model.libraryElement.AdapterFB
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType
import org.eclipse.fordiac.ide.model.libraryElement.BaseFBType
import org.eclipse.fordiac.ide.model.libraryElement.Event
import org.eclipse.fordiac.ide.model.libraryElement.FB
import org.eclipse.fordiac.ide.model.libraryElement.FBType
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement
import org.eclipse.fordiac.ide.model.libraryElement.INamedElement
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElement
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration

import static extension org.eclipse.emf.ecore.util.EcoreUtil.*

final class ForteNgExportUtil {
	public static final CharSequence VARIABLE_EXPORT_PREFIX = "var_"
	public static final CharSequence EVENT_EXPORT_PREFIX = "evt_"
	public static final CharSequence FB_EXPORT_PREFIX = "fb_"

	def static CharSequence generateName(VarDeclaration variable) {
		switch (root : variable.rootContainer) {
			BaseFBType case root.internalConstVars.contains(
				variable): '''«VARIABLE_EXPORT_PREFIX»const_«variable.name»'''
			AdapterType: '''«VARIABLE_EXPORT_PREFIX»«variable.name»()'''
			default: '''«VARIABLE_EXPORT_PREFIX»«variable.name»'''
		}
	}

	def static CharSequence generateName(FB feature) '''«FB_EXPORT_PREFIX»«feature.name»()'''

	def static CharSequence generateName(Event feature) '''«EVENT_EXPORT_PREFIX»«feature.name»'''

	def static CharSequence generateName(AdapterFB feature) '''«VARIABLE_EXPORT_PREFIX»«feature.name»()'''

	def static CharSequence generateName(AdapterDeclaration feature) '''«VARIABLE_EXPORT_PREFIX»«feature.name»()'''

	def static CharSequence generateTypeName(INamedElement type) {
		switch (type) {
			AdapterType: '''FORTE_«type.generateTypeNamePlain»'''
			ArrayType:
				type.subranges.reverseView.fold(type.baseType.generateTypeName) [ result, subrange |
					val fixed = subrange.setLowerLimit && subrange.setUpperLimit
					'''«IF fixed»CIEC_ARRAY_FIXED«ELSE»CIEC_ARRAY_VARIABLE«ENDIF»<«result»«IF fixed», «subrange.lowerLimit», «subrange.upperLimit»«ENDIF»>'''
				].toString
			StringType: '''CIEC_«type.generateTypeNamePlain»«IF type.isSetMaxLength»_FIXED<«type.maxLength»>«ENDIF»'''
			DataType: '''CIEC_«type.generateTypeNamePlain»«IF GenericTypes.isAnyType(type)»_VARIANT«ENDIF»'''
			FBType: '''FORTE_«type.generateTypeNamePlain»'''
			default:
				type.generateTypeNamePlain
		}
	}

	def static CharSequence generateTypeNameAsParameter(INamedElement type) {
		switch (type) {
			AdapterType: '''FORTE_«type.generateTypeNamePlain»'''
			ArrayType:
				type.subranges.reverseView.fold(type.baseType.generateTypeName) [ result, subrange |
					'''CIEC_ARRAY_COMMON<«result»>'''
				].toString
			StringType: '''CIEC_«type.generateTypeNamePlain»«IF type.isSetMaxLength»_FIXED<«type.maxLength»>«ENDIF»'''
			DataType: '''CIEC_«type.generateTypeNamePlain»'''
			FBType: '''FORTE_«type.generateTypeNamePlain»'''
			default:
				type.generateTypeNamePlain
		}
	}

	def static CharSequence generateTypeSpec(INamedElement type) {
		switch (type) {
			ArrayType:
				type.subranges.reverseView.fold(type.baseType.generateTypeNamePlain.FORTEStringId) [ result, subrange |
					'''«type.generateTypeNamePlain.FORTEStringId», static_cast<CStringDictionary::TStringId>(«subrange.lowerLimit»), static_cast<CStringDictionary::TStringId>(«subrange.upperLimit»), «result»'''
				].toString
			default:
				type.generateTypeNamePlain.FORTEStringId
		}
	}

	def static String generateDefiningInclude(EObject object) {
		switch (object) {
			LibraryElement: object.generateTypeIncludePath
			default: object.eResource?.generateDefiningInclude
		}
	}

	def static String generateDefiningInclude(Resource resource) {
		resource.contents.filter(LibraryElement)?.head?.generateTypeIncludePath ?:
			'''«resource.URI.trimFileExtension.lastSegment».h'''
	}

	def static String generateTypeIncludePath(INamedElement type) {
		switch (path : type.generateTypePath) {
			case !path.empty: '''«path»/«type.generateTypeBasename».h'''
			default: '''«type.generateTypeBasename».h'''
		}
	}

	def static String generateTypeInclude(INamedElement type) '''«type.generateTypeBasename».h'''

	def static String generateTypeSource(INamedElement type) '''«type.generateTypeBasename».cpp'''

	def static String generateTypeBasename(INamedElement type) {
		switch (type) {
			TimeType:
				"forte_time"
			LtimeType:
				"forte_ltime"
			DateType:
				"forte_date"
			LdateType:
				"forte_ldate"
			TimeOfDayType:
				"forte_time_of_day"
			LtodType:
				"forte_ltime_of_day"
			DateAndTimeType:
				"forte_date_and_time"
			LdtType:
				"forte_ldate_and_time"
			StringType case type.isSetMaxLength:
				"forte_string_fixed"
			StringType:
				"forte_string"
			WstringType:
				"forte_wstring"
			AdapterType: type.name
			ArrayType: type.baseType.generateTypeBasename
			DataType case GenericTypes.isAnyType(type): '''forte_«type.generateTypeNamePlain.toLowerCase»_variant'''
			DataType: '''forte_«type.name.toLowerCase»'''
			default:
				type.name
		}
	}

	def static String generateTypePath(INamedElement type) {
		switch (type) {
			LibraryElement:
				type.compilerInfo?.packageName?.replace("::", "/") ?: ""
			default:
				type.name
		}
	}

	def static String generateTypeNamePlain(INamedElement type) {
		switch (type) {
			TimeType:
				"TIME"
			LtimeType:
				"LTIME"
			DateType:
				"DATE"
			LdateType:
				"LDATE"
			TimeOfDayType:
				"TIME_OF_DAY"
			LtodType:
				"LTIME_OF_DAY"
			DateAndTimeType:
				"DATE_AND_TIME"
			LdtType:
				"LDATE_AND_TIME"
			ArrayType:
				"ARRAY"
			StringType:
				"STRING"
			WstringType:
				"WSTRING"
			LibraryElement: '''«type.compilerInfo?.packageName?.replace(':', '_')?.concat("__")»«type.name»'''
			default:
				type.name
		}
	}

	def static CharSequence getFORTEStringId(String s) '''g_nStringId«s»'''
	
	def static int getInterfaceElementIndex(IInterfaceElement element) {
		if (element.eContainer !== null && element.eContainingFeature.many) {
			(element.eContainer.eGet(element.eContainingFeature) as EList<? extends IInterfaceElement>).indexOf(element)
		} else
			0
	}

	static final Pattern END_COMMENT_PATTERN = Pattern.compile("\\*/")

	def static CharSequence escapeMultilineCommentString(CharSequence string) {
		END_COMMENT_PATTERN.matcher(string).replaceAll("* /")
	}

	private new() {
		throw new UnsupportedOperationException
	}
}
