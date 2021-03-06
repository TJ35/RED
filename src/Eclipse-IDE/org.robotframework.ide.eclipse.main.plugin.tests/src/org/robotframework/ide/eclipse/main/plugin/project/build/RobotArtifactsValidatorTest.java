/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project.build;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.rf.ide.core.environment.NullRuntimeEnvironment;
import org.robotframework.ide.eclipse.main.plugin.RedPreferences;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.ide.eclipse.main.plugin.project.RobotProjectNature;
import org.robotframework.red.junit.jupiter.BooleanPreference;
import org.robotframework.red.junit.jupiter.PreferencesExtension;

@ExtendWith(PreferencesExtension.class)
public class RobotArtifactsValidatorTest {

    @BooleanPreference(key = RedPreferences.TURN_OFF_VALIDATION, value = true)
    @Test
    public void test_noRevalidate_ifValidationTurnedOffInPreferences() throws Exception {
        final RobotSuiteFile suiteModel = mock(RobotSuiteFile.class);

        RobotArtifactsValidator.revalidate(suiteModel);

        verifyNoMoreInteractions(suiteModel);
    }

    @Test
    public void test_revalidate_noRobot_installed() throws Exception {
        // prepare
        final RobotSuiteFile suiteModel = mock(RobotSuiteFile.class);
        final IFile file = mock(IFile.class);
        final IProject project = mock(IProject.class);
        final RobotProject robotProject = mock(RobotProject.class);

        when(suiteModel.getFile()).thenReturn(file);
        when(file.exists()).thenReturn(true);
        when(file.getProject()).thenReturn(project);
        when(project.exists()).thenReturn(true);
        when(project.hasNature(RobotProjectNature.ROBOT_NATURE)).thenReturn(true);
        when(suiteModel.getRobotProject()).thenReturn(robotProject);
        when(robotProject.getRuntimeEnvironment()).thenReturn(new NullRuntimeEnvironment());

        // execute
        RobotArtifactsValidator.revalidate(suiteModel);

        // verify
        final InOrder order = inOrder(suiteModel, file, robotProject);
        order.verify(suiteModel).getFile();
        order.verify(file).exists();
        order.verify(suiteModel).getRobotProject();
        order.verify(robotProject).getRuntimeEnvironment();
        order.verifyNoMoreInteractions();
    }
}
