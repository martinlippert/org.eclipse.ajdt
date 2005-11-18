/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: Sian January - initial version
 * ... 
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.actions;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aspectj.ajde.Ajde;
import org.aspectj.ajde.ProjectPropertiesAdapter;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.builder.CoreProjectProperties;
import org.eclipse.ajdt.exports.AJModelBuildScriptGenerator;
import org.eclipse.ajdt.internal.buildconfig.BuildConfiguration;
import org.eclipse.ajdt.internal.buildconfig.BuildConfigurator;
import org.eclipse.ajdt.internal.buildconfig.ProjectBuildConfigurator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.build.AbstractScriptGenerator;
import org.eclipse.pde.internal.build.builder.DevClassPathHelper;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.TargetPlatform;
import org.eclipse.pde.internal.ui.build.BaseBuildAction;
import org.eclipse.pde.internal.ui.build.ClasspathHelper;

/**
 * 
 */
public class BuildPluginAction extends BaseBuildAction {

	protected void makeScripts(IProgressMonitor monitor)
		throws InvocationTargetException, CoreException {
	
		AJModelBuildScriptGenerator generator = new AJModelBuildScriptGenerator();
		AJModelBuildScriptGenerator.setOutputFormat(AbstractScriptGenerator.getDefaultOutputFormat());
		AJModelBuildScriptGenerator.setEmbeddedSource(AbstractScriptGenerator.getDefaultEmbeddedSource());
		AJModelBuildScriptGenerator.setForceUpdateJar(AbstractScriptGenerator.getForceUpdateJarFormat());
		AJModelBuildScriptGenerator.setConfigInfo(AbstractScriptGenerator.getDefaultConfigInfos());
		
		IProject project = fManifestFile.getProject();
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator().getProjectBuildConfigurator(project);
		if (pbc != null) {
			BuildConfiguration bc = pbc.getActiveBuildConfiguration();
			if (bc != null) {
				IFile configFile = bc.getFile();
				generator.setBuildConfig(configFile.getName());
			}
		}
		List inpath = getInpath(project);
		List aspectpath = getAspectpath(project);
		generator.setInpath(inpath);
		generator.setAspectpath(aspectpath);
		
		generator.setWorkingDirectory(project.getLocation().toOSString());
		String url = ClasspathHelper.getDevEntriesProperties(project.getLocation().addTrailingSeparator().toString() + "dev.properties", false); //$NON-NLS-1$
		generator.setDevEntries(new DevClassPathHelper(url));
		generator.setPluginPath(TargetPlatform.createPluginPath());
		generator.setBuildingOSGi(PDECore.getDefault().getModelManager().isOSGiRuntime());
		try {
			IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(project);
			if (model != null) {
				generator.setModelId(model.getPluginBase().getId());
				generator.generate();
			}
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		}
	}

	private List getAspectpath(IProject project) {
		String[] v = AspectJCorePreferences.getProjectAspectPath(project);

		// need to expand any variables on the path
		ProjectPropertiesAdapter adapter = Ajde.getDefault().getProjectProperties();
		if (adapter instanceof CoreProjectProperties) {
			String aspectPath = ((CoreProjectProperties)adapter).expandVariables(v[0], v[2]);

			// Ensure that every entry in the list is a fully qualified one.
			aspectPath = ((CoreProjectProperties)adapter).fullyQualifyPathEntries(aspectPath);
	
			if(aspectPath.length() > 0) {
				String[] entries = aspectPath.split(File.pathSeparator);
				List entryList = new ArrayList(Arrays.asList(entries));
				return entryList;
			}
		}
		return null;		
	}

	private List getInpath(IProject project) {
		String[] v = AspectJCorePreferences.getProjectInPath(project);

		// need to expand any variables on the path
		ProjectPropertiesAdapter adapter = Ajde.getDefault().getProjectProperties();
		if (adapter instanceof CoreProjectProperties) {
			String inPath = ((CoreProjectProperties)adapter).expandVariables(v[0], v[2]);

			// Ensure that every entry in the list is a fully qualified one.
			inPath = ((CoreProjectProperties)adapter).fullyQualifyPathEntries(inPath);
	
			if(inPath.length() > 0) {
				String[] entries = inPath.split(File.pathSeparator);
				List entryList = new ArrayList(Arrays.asList(entries));
				return entryList;
			}
		}
		return null;	
	}

}
