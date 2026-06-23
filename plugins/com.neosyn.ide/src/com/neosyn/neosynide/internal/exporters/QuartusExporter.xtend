/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.neosynide.internal.exporters

import com.neosyn.core.IExporter
import com.neosyn.models.dpn.Entity
import com.neosyn.neosynide.ExportHelper
import java.io.File
import org.eclipse.core.runtime.Path
import org.w3c.dom.Document
import org.eclipse.core.resources.IProject
import com.neosyn.neosynide.NeosynIde
import static com.neosyn.neosynide.preferences.IPreferenceConstants.PREF_QUARTUS_BIN
import com.neosyn.core.util.CoreUtil

/**
 * This class defines a generator of Quartus II project file.
 * 

 * 
 */
class QuartusExporter implements IExporter {

	override buildProject(Entity entity, Document xmlDoc) {
		val project = entity.file.project
		val name = entity.simpleName

		runQuartus(project, name)
	}
	
	/**
	 * Exports to Altera .qpf and .qsf files.
	 */
	override createProject(Entity entity, Document xmlDoc) {
		val name = entity.simpleName
		val path = new Path(FOLDER_PROJECTS).append(name).toString
		val helper = new ExportHelper(entity, path, "other")

		helper.write(path + File.separator + name + ".qpf", printQpf(helper, name))
		helper.write(path + File.separator + name + ".qsf", printQsf(helper, name))

		return null
	}

	def private printQpf(ExportHelper helper, String name) '''
		QUARTUS_VERSION = "11.1"
		DATE = "«helper.date»"
		
		# Revisions
		
		PROJECT_REVISION = "«name»"
	'''

	def private printQsf(ExportHelper helper, String name) {
		'''
			# Generated from «name»
			set_global_assignment -name FAMILY "Cyclone IV GX"
			set_global_assignment -name DEVICE AUTO
			set_global_assignment -name TOP_LEVEL_ENTITY «name»
			set_global_assignment -name ORIGINAL_QUARTUS_VERSION 12.0
			set_global_assignment -name PROJECT_CREATION_TIME_DATE "«helper.date»"
			set_global_assignment -name LAST_QUARTUS_VERSION 12.0
			
			# Source files
			«FOR path : helper.computePathList()»
				«printAssignment(helper.language, path)»
			«ENDFOR»
			
			set_global_assignment -name SEARCH_PATH "«helper.includePath.join(File.pathSeparator)»"
		'''
	}

	def private printAssignment(String language, String path) '''
		set_global_assignment -name «language.toUpperCase»_FILE «path»
	'''

	override exportPR(Entity entity, Entity variant1, Entity variant2, Document xmlDoc) {
		return null
	}

	override setupPR(Entity entity) {
		return null
	}
	
	def private runQuartus(IProject project, String name) {
		val bin = NeosynIde.getPreference(PREF_QUARTUS_BIN, "")
		if (bin.isEmpty()) {
			return null
		}
		
		var quartus = CoreUtil.getExecutable(bin)	

		if (System.getProperty("os.name").startsWith("Windows")) {
			quartus = quartus + File.separator + "quartus"
		} else {
			quartus = quartus + File.separator + "quartus_sh"			
		}

		val quartusProject = name + ".qpf"
		val workingDir = project.getFolder("projects").getLocation().toString() + File.separator + name
		new ProcessBuilder(quartus, "--flow", "compile", quartusProject).directory(new File(workingDir))
	}

}
