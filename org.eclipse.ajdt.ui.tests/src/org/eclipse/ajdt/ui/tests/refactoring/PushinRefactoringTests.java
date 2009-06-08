/*******************************************************************************
 * Copyright (c) 2009 SpringSourceand others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.refactoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.List;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.internal.ui.refactoring.PushInRefactoring;
import org.eclipse.ajdt.internal.ui.refactoring.PushInRefactoringAction;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaElementTransfer;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.refactoring.reorg.PasteAction;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.views.log.AbstractEntry;
import org.eclipse.ui.internal.views.log.LogEntry;
import org.eclipse.ui.internal.views.log.LogView;

import sun.security.action.GetLongAction;

/**
 * 
 * @author andrew
 * 
 * Tests pushin refactoring
 * 
 * Bug 274608
 *
 */
public class PushinRefactoringTests extends UITestCase {
    
    // gives us access to the findAllITDs method
    class MockAction extends PushInRefactoringAction {
        protected List findAllITDs(IJavaElement[] selection)
                throws JavaModelException {
            return super.findAllITDs(selection);
        }
    }

    IJavaProject pushinJavaProj;
    IProject pushinProj;
    PushInRefactoring refactoring;
    MockAction action;
    
    protected void setUp() throws Exception {
        super.setUp();
        pushinProj = createPredefinedProject("PushinRefactoringTest");
        pushinJavaProj = JavaCore.create(pushinProj);
        refactoring = new PushInRefactoring();
        action = new MockAction();
    }
    
    public void testSimpleITDsNoDelete() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin"));
        List itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);
        
        IFile classFile = pushinProj.getFile("src/pushin/OtherClass.java");
        IFile aspectFile = pushinProj.getFile("src/pushin/OtherAspect.aj");
        checkFileForContents(classFile, "void foo(List<Integer> y)");
        checkFileForNoContents(aspectFile, "void OtherClass.foo(List<Integer> y)");
    }
    
    public void testITDsWillDelete() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin2"));
        List itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);
        lookForAJFiles(frag.getResource());
        IFile classFile = pushinProj.getFile("src/pushin2/B.java");
        checkFileForContents(classFile, "int x = 9;");
        checkFileForContents(classFile, "int y = 9;");
    }
    
    public void testDeclareAnnotation() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin3"));
        List itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);
        
        IFile aspectFile = pushinProj.getFile("src/pushin3/A.aj");
        IFile classFile = pushinProj.getFile("src/pushin3/B.java");
        checkFileForContents(classFile, "int y = 9;");
        checkFileForContents(classFile, "@Deprecated");
        checkFileForNoContents(aspectFile, "int B.y = 9;");
        checkFileForNoContents(aspectFile, "@Deprecated");
        checkFileForContents(aspectFile, "int y;");
    }
    
    public void testDeclareAnnotationBig() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin4"));
        List itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);
        IFile aspectFileA = pushinProj.getFile("src/pushin4/A.aj");
        IFile classFileB = pushinProj.getFile("src/pushin4/B.java");
        IFile classFileC = pushinProj.getFile("src/pushin4/C.java");
        IFile aspectFileD = pushinProj.getFile("src/pushin4/D.aj");
        IFile aspectFileE = pushinProj.getFile("src/pushin4/E.aj");
        
        checkFileForContents(aspectFileA, "int y;");
        checkFileForNoContents(aspectFileA, "int B.y = 9;");
        checkFileForNoContents(aspectFileA, "int C.y = 9;");
        checkFileForNoContents(aspectFileA, "@Deprecated");
        
        checkFileForContents(classFileB, "int y = 9;");
        checkFileForContents(classFileB, "int z = 9;");
        checkFileForContents(classFileB, "@Deprecated");
        
        checkFileForContents(classFileC, "int y = 9;");
        checkFileForContents(classFileC, "int z = 9;");
        
        checkFileForContents(aspectFileD, "int y;");
        checkFileForNoContents(aspectFileD, "int B.z = 9;");
        checkFileForNoContents(aspectFileD, "int C.z = 9;");
        checkFileForNoContents(aspectFileD, "@Deprecated");
        
        lookForAJFiles(aspectFileE);
    }
    
    public void testDeclareParents() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin5"));
        List itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);

        IFile aClass = pushinProj.getFile("src/pushin5/AClass.java");
        IFile aClassWithExtends = pushinProj.getFile("src/pushin5/AClassWithExtends.java");
        IFile aClassWithImplements = pushinProj.getFile("src/pushin5/AClassWithImplements.java");
        IFile aClassWithImplementsAndExtends = pushinProj.getFile("src/pushin5/AClassWithImplementsAndExtends.java");
        IFile anInterface = pushinProj.getFile("src/pushin5/AnInterface.java");
        IFile anInterfaceWithExtends = pushinProj.getFile("src/pushin5/AnInterfaceWithExtends.java");
        IFile errorNoImport = pushinProj.getFile("src/pushin5/ErrorNoImport.java");
        IFile noErrorFullyQualified = pushinProj.getFile("src/pushin5/NoErrorFullyQualified.java");

        IFile anAspect = pushinProj.getFile("src/pushin5/AnAspect.aj");

        checkFileForContents(aClass, "I");
        checkFileForContents(aClass, "J");
        checkFileForContents(aClass, "H");
        
        checkFileForContents(aClassWithExtends, "I");
        checkFileForContents(aClassWithExtends, "J");
        
        checkFileForContents(aClassWithImplements, "I");
        checkFileForContents(aClassWithImplements, "J");
        
        checkFileForContents(aClassWithImplementsAndExtends, "I");
        checkFileForContents(aClassWithImplementsAndExtends, "J");
        
        checkFileForContents(anInterface, "I");
        checkFileForContents(anInterface, "J");
        
        checkFileForContents(anInterfaceWithExtends, "I");
        checkFileForContents(anInterfaceWithExtends, "J");

        checkFileForContents(errorNoImport, "OtherClass");
        
        checkFileForContents(noErrorFullyQualified, "Serializable");
        
        checkFileForNoContents(anAspect, "AClass");
        checkFileForNoContents(anAspect, "AnInterface");
        checkFileForNoContents(anAspect, "NoErrorFullyQualified");
        checkFileForNoContents(anAspect, "ErrorNoImport");
    }
    
    public void testPetClinic() throws Exception {
        // test that the test clinic project works with push in
        
        IJavaProject petClinic = JavaCore.create(createPredefinedProject("petclinic2"));
        List itds = action.findAllITDs(new IJavaElement[] { petClinic });
        doRefactoringAndInitialCheck(petClinic, itds);
        
        
        // no aspect files left over
        lookForAJFiles(petClinic.getProject());
    }

    private void lookForAJFiles(IResource resource) throws CoreException {
        if (resource.getType() == IResource.FILE) {
            if (resource.getFileExtension().equals("aj")) {
                assertFalse("Should not have any aj files left after refactoring, but found: " +
                                resource.getFullPath(), resource.exists());
            }
            return;
        }
        
        IResourceVisitor visitor = new IResourceVisitor() {
            public boolean visit(IResource resource) throws CoreException {
                if (resource.getType() == IResource.FILE) {
                    if (resource.getFileExtension().equals("aj")) {
                        fail("Should not have any aj files left after refactoring, but found: " +
                                resource.getFullPath());
                    }
                }
                return false;
            }
        };
        resource.accept(visitor);
    }

    private void doRefactoringAndInitialCheck(IJavaProject jProject, List itds)
            throws PartInitException, Exception, CoreException {
        refactoring.setITDs(itds);
        LogView logView = (LogView) Workbench.getInstance().getActiveWorkbenchWindow().getActivePage().getActivePart().getSite().getPage().showView("org.eclipse.pde.runtime.LogView"); //$NON-NLS-1$
        AbstractEntry[] logs = logView.getElements();
        int numErrors = logs.length;
        
        // do the refactoring
        executeRefactoring(refactoring);
        
        // no errors on log
        logs = logView.getElements();
        if (numErrors != logs.length) {
            StringBuffer sb = new StringBuffer();
            for (int i = numErrors; i < logs.length; i++) {
                LogEntry entry = (LogEntry) logs[i];
                sb.append(entry.getMessage() + "\n" + entry.getStack() + "\n");
            }
            fail("Should not have any extra log entries after refactoring petclinic:\n" + sb.toString());
        }
        
        // no compile errors
        IMarker[] problems = jProject.getProject().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        if (problems.length > 0) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < problems.length; i++) {
                IMarker marker = problems[i];
                String msg = (String) marker.getAttribute(IMarker.MESSAGE);
                
                // ignore missing imports
                // ignore duplicate resource warnings
                if (((Integer) marker.getAttribute(IMarker.SEVERITY)).intValue() >= IMarker.SEVERITY_WARNING 
                            && msg.indexOf("import") == -1 
                            && msg.indexOf("duplicate resource:") == -1) {
                    sb.append(marker.getResource().getFullPath() + ": ");
                    sb.append(msg  + "\n");
                }
            }
            if (sb.length() > 0) {
                fail("Should not have any compile errors after pushin refactoring:\n" + sb.toString());
            }
        }
    }
    
    private void checkFileForContents(IFile file, String toCheck) throws Exception {
        String contents = getContents(file);
        int index = contents.indexOf(toCheck);
        if (index == -1) {
            fail("Should have found '" + toCheck + "' in '" + file.getFullPath() + "'");
        }
    }
    
    private void checkFileForNoContents(IFile file, String toCheck) throws Exception {
        String contents = getContents(file);
        int index = contents.indexOf(toCheck);
        if (index != -1) {
            fail("Should not have found '" + toCheck + "' in '" + file.getFullPath() + "'");
        }
    }
    
    private String getContents(IFile file) throws CoreException, IOException {
        InputStream is = file.getContents();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuffer buffer= new StringBuffer();
        char[] readBuffer= new char[2048];
        int n= br.read(readBuffer);
        while (n > 0) {
            buffer.append(readBuffer, 0, n);
            n= br.read(readBuffer);
        }
        return buffer.toString();
    }

    
    
    // borrowed from JDT Refactoring tests
    protected void executeRefactoring(Refactoring refactoring) throws Exception {
        PerformRefactoringOperation operation= new PerformRefactoringOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
        waitForJobsToComplete();
        // Flush the undo manager to not count any already existing undo objects
        // into the heap consumption
        RefactoringCore.getUndoManager().flush();
        System.gc();
        ResourcesPlugin.getWorkspace().run(operation, null);
        waitForJobsToComplete();
        assertEquals(true, operation.getConditionStatus().getSeverity() == RefactoringStatus.OK);
        assertEquals(true, operation.getValidationStatus().isOK());
        RefactoringCore.getUndoManager().flush();
        System.gc();
    }

}