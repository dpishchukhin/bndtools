/*******************************************************************************
 * Copyright (c) 2012 Per Kr. Soreide.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Per Kr. Soreide - initial API and implementation
 *******************************************************************************/
package bndtools.release;

import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;

import aQute.bnd.service.RepositoryPlugin;
import bndtools.release.api.ReleaseContext;
import bndtools.release.nl.Messages;

public class WorkspaceReleaseJob extends Job {

	private List<ProjectDiff> projectDiffs;
	private boolean updateOnly;
	private final boolean showMessage;

	public WorkspaceReleaseJob(List<ProjectDiff> projectDiffs, boolean updateOnly, boolean showMessage) {
		super(Messages.workspaceReleaseJob2);
		this.projectDiffs = projectDiffs;
		this.updateOnly = updateOnly;
		this.showMessage = showMessage;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {

		monitor.beginTask(Messages.releasingProjects, projectDiffs.size());
		for (ProjectDiff projectDiff : projectDiffs) {
			if (projectDiff.isRelease()) {

				RepositoryPlugin release = null;
				if (projectDiff.getReleaseRepository() != null) {
					release = Activator.getRepositoryPlugin(projectDiff.getReleaseRepository());
				}

				ReleaseContext context = new ReleaseContext(projectDiff.getProject(), projectDiff.getBaselines(), release, updateOnly);
				ReleaseJob job = new ReleaseJob(context, showMessage);
				job.setRule(ResourcesPlugin.getWorkspace().getRoot());
				job.run(new SubProgressMonitor(monitor, 1));
			}
			monitor.worked(1);
		}
		monitor.done();

		return Status.OK_STATUS;
	}

}
