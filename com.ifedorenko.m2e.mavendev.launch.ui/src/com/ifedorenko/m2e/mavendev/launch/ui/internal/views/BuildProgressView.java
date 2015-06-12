package com.ifedorenko.m2e.mavendev.launch.ui.internal.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import com.ifedorenko.m2e.mavendev.launch.ui.internal.BuildProgressActivator;
import com.ifedorenko.m2e.mavendev.launch.ui.internal.BuildProgressImages;
import com.ifedorenko.m2e.mavendev.launch.ui.internal.model.BuildStatus;
import com.ifedorenko.m2e.mavendev.launch.ui.internal.model.IBuildProgressListener;
import com.ifedorenko.m2e.mavendev.launch.ui.internal.model.Launch;
import com.ifedorenko.m2e.mavendev.launch.ui.internal.model.MojoExecution;
import com.ifedorenko.m2e.mavendev.launch.ui.internal.model.Project;

public class BuildProgressView extends ViewPart {

  public static final String ID = "com.ifedorenko.m2e.mavendev.launch.ui.views.SampleView";

  private static final BuildProgressActivator CORE = BuildProgressActivator.getInstance();

  private TreeViewer viewer;

  private final List<Object> refreshQueue = new ArrayList<>();

  private UIJob refreshJob;

  private final IBuildProgressListener buildListener = new IBuildProgressListener() {
    @Override
    public void onUpdate(Object source) {
      synchronized (refreshQueue) {
        refreshQueue.add(source);
      }
      refreshJob.schedule(300L);
    }
  };

  private GreenRedProgressBar progressBar;

  public BuildProgressView() {
    CORE.addListener(buildListener);
  }

  public void createPartControl(Composite parent) {
    parent.setLayout(new GridLayout(1, false));

    progressBar = new GreenRedProgressBar(parent);
    progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

    viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    Tree tree = viewer.getTree();
    tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
    viewer.setContentProvider(new ITreeContentProvider() {

      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

      @Override
      public void dispose() {}

      @Override
      public boolean hasChildren(Object element) {
        return getChildren(element) != null;
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        if (inputElement instanceof Launch) {
          return ((Launch) inputElement).getProjects().toArray();
        }
        return null;
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof Project) {
          return ((Project) parentElement).getExecutions().toArray();
        }
        return null;
      }
    });
    viewer.setLabelProvider(new ILabelProvider() {

      @Override
      public void removeListener(ILabelProviderListener listener) {}

      @Override
      public boolean isLabelProperty(Object element, String property) {
        return false;
      }

      @Override
      public void dispose() {}

      @Override
      public void addListener(ILabelProviderListener listener) {}

      @Override
      public String getText(Object element) {
        if (element instanceof Project) {
          return ((Project) element).getId();
        }
        if (element instanceof MojoExecution) {
          return ((MojoExecution) element).getId();
        }
        return null;
      }

      @Override
      public Image getImage(Object element) {
        if (element instanceof Project) {
          switch (((Project) element).getStatus()) {
            case inprogress:
              return BuildProgressImages.PROJECT_INPROGRESS.get();
            case succeeded:
              return BuildProgressImages.PROJECT_SUCCESS.get();
            case failed:
              return BuildProgressImages.PROJECT_FAILURE.get();
            case skipped:
              return BuildProgressImages.PROJECT_SKIPPED.get();
            default:
              return BuildProgressImages.PROJECT.get();
          }
        }
        return null;
      }
    });
    // viewer.setInput(getViewSite());
    // getSite().setSelectionProvider(viewer);

    IActionBars actionBars = getViewSite().getActionBars();
    IToolBarManager toolBar = actionBars.getToolBarManager();
    IMenuManager viewMenu = actionBars.getMenuManager();

    Action action = new Action("Test") {};
    toolBar.add(action);

    toolBar.add(new Separator());
    actionBars.updateActionBars();

  }

  public void setFocus() {
    viewer.getControl().setFocus();
  }

  @Override
  public void dispose() {
    CORE.removeListener(buildListener);
    refreshJob.cancel();

    super.dispose();
  }

  @Override
  protected void setSite(IWorkbenchPartSite site) {
    super.setSite(site);
    refreshJob = new UIJob(site.getShell().getDisplay(), "Maven Build Progress View Refresh Job") {
      @Override
      public IStatus runInUIThread(IProgressMonitor monitor) {
        return BuildProgressView.this.runInUIThread(monitor);
      }
    };
    refreshJob.setUser(false);
  }


  protected IStatus runInUIThread(IProgressMonitor monitor) {
    List<Object> queue;
    synchronized (refreshQueue) {
      queue = new ArrayList<>(refreshQueue);
      refreshQueue.clear();
    }

    viewer.getTree().setRedraw(false);
    try {
      for (Object object : queue) {
        if (object instanceof Launch) {
          viewer.setInput(object);
          BuildStatus status = ((Launch) object).getStatus();
          progressBar.reset(status.hasFailures(), false /* stopped */,
              status.getCompleted() /* tickDone */, status.getTotal() /* maximum */);
        } else if (object instanceof Project) {
          viewer.refresh(object, true);

          Launch launch = (Launch) viewer.getInput();
          BuildStatus status = launch.getStatus();
          progressBar.reset(status.hasFailures(), false /* stopped */,
              status.getCompleted() /* tickDone */, status.getTotal() /* maximum */);
        }
      }
    } finally {
      viewer.getTree().setRedraw(true);
    }
    return Status.OK_STATUS;
  }
}