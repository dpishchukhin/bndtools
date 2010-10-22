/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools.wizards.project;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.wizards.JavaProjectWizard;
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageTwo;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.Document;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

import aQute.bnd.build.Project;
import bndtools.Plugin;
import bndtools.api.IProjectTemplate;
import bndtools.editor.model.BndEditModel;
import bndtools.editor.model.BndProject;

class NewBndProjectWizard extends JavaProjectWizard {

	private final NewBndProjectWizardPageOne pageOne;
	private final NewJavaProjectWizardPageTwo pageTwo;
	private final TemplateSelectionWizardPage templatePage = new TemplateSelectionWizardPage();

	NewBndProjectWizard(NewBndProjectWizardPageOne pageOne, NewJavaProjectWizardPageTwo pageTwo) {
		super(pageOne, pageTwo);
		setWindowTitle("New Bnd OSGi Project");
		setNeedsProgressMonitor(true);

		this.pageOne = pageOne;
		this.pageTwo = pageTwo;
	}

	@Override
	public void addPages() {
		addPage(pageOne);
		addPage(templatePage);
		addPage(pageTwo);
	}

	/**
	 * Generate the new Bnd model for the project. This implementation simply returns an empty Bnd model.
	 * @param monitor
	 */
	protected BndEditModel generateBndModel(IProgressMonitor monitor) {
	    BndEditModel model = new BndEditModel();

	    IProjectTemplate template = templatePage.getTemplate();
	    if (template != null) {
	        template.modifyInitialBndModel(model);
	    }

	    return model;
	}

    /**
     * Allows for an IProjectTemplate to modify the new Bnd project
     * @param monitor
     */
    protected BndProject generateBndProject(IProgressMonitor monitor) {
        BndProject proj = new BndProject();

        IProjectTemplate template = templatePage.getTemplate();
        if (template != null) {
            template.modifyInitialBndProject(proj);
        }

        return proj;
    }
    /**
     * Modify the newly generated Java project; this method is executed from
     * within a workspace operation so is free to make workspace resource
     * modifications.
     *
     * @throws CoreException
     */
    protected void processGeneratedProject(BndEditModel bndModel, IProject project, IProgressMonitor monitor) throws CoreException {
        SubMonitor progress = SubMonitor.convert(monitor, 3);

        Document document = new Document();
        bndModel.saveChangesTo(document);
        progress.worked(1);

        ByteArrayInputStream bndInput = new ByteArrayInputStream(document.get().getBytes());
        IFile bndBndFile = project.getFile(Project.BNDFILE);
        if (bndBndFile.exists()) {
            bndBndFile.setContents(bndInput, false, false, progress.newChild(1));
        } else {
            bndBndFile.create(bndInput, false, progress.newChild(1));
        }


        IFile buildXmlFile = project.getFile("build.xml");
        InputStream buildXmlInput = getClass().getResourceAsStream("template_bnd_build.xml");
        if (buildXmlFile.exists()) {
            buildXmlFile.setContents(buildXmlInput, false, false, progress.newChild(1));
        } else {
            buildXmlFile.create(buildXmlInput, false, progress.newChild(1));
        }
        
        BndProject proj = generateBndProject(progress.newChild(1));
        for (Map.Entry<String, URL> resource : proj.getResources().entrySet()) {
            importResource(project, resource.getKey(), resource.getValue(), progress.newChild(1));
        }
        
    }

    protected IFile importResource(IProject project, String fullPath, URL url, IProgressMonitor monitor) throws CoreException {
        
        IFile p = project.getFile(fullPath);
        File target = p.getLocation().toFile();
        
        InputStream is = null;
        OutputStream os = null;

        try {
            is = url.openStream();
            os = new FileOutputStream(target);
 
            byte[] b = new byte[1024];
            int len;
            
            while ((len = is.read(b)) > -1) {
                os.write(b, 0, len);
            }
            os.flush();
        } catch (Exception e) {
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, e.getMessage(), e));
        } finally {
            if (is != null) {
                try{is.close();}catch(Exception e){}
            }
            if (os != null) {
                try{os.close();}catch(Exception e){}
            }
        }
        
        try {
            p.refreshLocal(IResource.DEPTH_ZERO, null);
        } catch (CoreException e) {
            // Do nothing
        }
        
        if (monitor != null) {
            monitor.done();
        }
        return p;
    }

    @Override
    public boolean performFinish() {
        boolean result = super.performFinish();
        if (result) {
            final IJavaProject javaProj = (IJavaProject) getCreatedElement();
            try {
                // Run using the progress bar from the wizard dialog
                getContainer().run(false, false, new IRunnableWithProgress() {
                    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                        try {
                            SubMonitor progress = SubMonitor.convert(monitor, 3);

                            // Generate the Bnd model
                            final BndEditModel bndModel = generateBndModel(progress.newChild(1));

                            // Make changes to the project
                            final IWorkspaceRunnable op = new IWorkspaceRunnable() {
                                public void run(IProgressMonitor monitor) throws CoreException {
                                    processGeneratedProject(bndModel, javaProj.getProject(), monitor);
                                }
                            };
                            javaProj.getProject().getWorkspace().run(op, progress.newChild(2));
                        } catch (CoreException e) {
                            throw new InvocationTargetException(e);
                        }
                    }
                });
                result = true;
            } catch (InvocationTargetException e) {
                ErrorDialog.openError(
                        getShell(),
                        "Error",
                        "",
                        new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, MessageFormat.format("Error creating Bnd project descriptor file ({0}).",
                                Project.BNDFILE), e.getTargetException()));
                result = false;
            } catch (InterruptedException e) {
                // Shouldn't happen
            }

            // Open the bnd.bnd file in the editor
            IFile bndFile = javaProj.getProject().getFile(Project.BNDFILE);
            try {
                IDE.openEditor(getWorkbench().getActiveWorkbenchWindow().getActivePage(), bndFile);
            } catch (PartInitException e) {
                ErrorDialog.openError(
                        getShell(),
                        "Error",
                        null,
                        new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, MessageFormat.format("Failed to open project descriptor file {0} in the editor.",
                                bndFile.getFullPath().toString()), e));
            }
        }
        return result;
    }
}
