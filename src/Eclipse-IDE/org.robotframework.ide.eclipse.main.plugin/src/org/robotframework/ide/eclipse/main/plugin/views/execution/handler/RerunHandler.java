/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.views.execution.handler;

import static org.robotframework.ide.eclipse.main.plugin.RedPlugin.newCoreException;

import java.util.Map;

import javax.inject.Named;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.rf.ide.core.execution.agent.event.SuiteStartedEvent.ExecutionMode;
import org.robotframework.ide.eclipse.main.plugin.launch.RobotTestExecutionService.RobotTestsLaunch;
import org.robotframework.ide.eclipse.main.plugin.views.execution.ExecutionViewWrapper;
import org.robotframework.ide.eclipse.main.plugin.views.execution.handler.RerunHandler.E4ShowFailedOnlyHandler;
import org.robotframework.red.commands.DIParameterizedHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;

public class RerunHandler extends DIParameterizedHandler<E4ShowFailedOnlyHandler> implements IElementUpdater {

    public static final String COMMAND_ID = "org.robotframework.red.view.execution.rerunTests";

    public RerunHandler() {
        super(E4ShowFailedOnlyHandler.class);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void updateElement(final UIElement element, final Map parameters) {
        final IWorkbenchWindow activeWindow = (IWorkbenchWindow) element.getServiceLocator();
        final ExecutionMode execMode = ExecutionViewPropertyTester.getExecutionMode(activeWindow);

        setTooltip(element, execMode);
    }

    @VisibleForTesting
    void setTooltip(final UIElement element, final ExecutionMode execMode) {
        element.setTooltip("Rerun " + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, execMode.name()));
    }

    public static class E4ShowFailedOnlyHandler {

        @Execute
        public void toggleShowFailedOnly(@Named(ISources.ACTIVE_PART_NAME) final ExecutionViewWrapper view) {
            view.getComponent().getCurrentlyShownLaunch().ifPresent(launch -> {
                final WorkspaceJob job = new WorkspaceJob("Launching Robot Tests") {

                    @Override
                    public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException {
                        final ILaunchConfiguration launchConfig = getConfig(launch);
                        launchConfig.launch(ILaunchManager.RUN_MODE, monitor);
                        return Status.OK_STATUS;
                    }
                };
                job.setUser(false);
                job.schedule();
            });
        }

        @VisibleForTesting
        static ILaunchConfiguration getConfig(final RobotTestsLaunch launch) throws CoreException {
            final ILaunchConfiguration launchConfig = launch.getLaunchConfiguration();
            if (launchConfig != null && launchConfig.exists()) {
                return launchConfig;
            } else {
                throw newCoreException("Launch configuration does not exist");
            }
        }
    }
}
