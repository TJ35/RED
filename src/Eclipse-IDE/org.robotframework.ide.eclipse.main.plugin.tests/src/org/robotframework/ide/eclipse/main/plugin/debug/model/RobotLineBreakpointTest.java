/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.debug.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robotframework.red.junit.jupiter.ProjectExtension.createFile;
import static org.robotframework.red.junit.jupiter.ProjectExtension.getFile;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.robotframework.red.junit.jupiter.Project;
import org.robotframework.red.junit.jupiter.ProjectExtension;

@ExtendWith(ProjectExtension.class)
public class RobotLineBreakpointTest {

    @Project
    static IProject project;

    @BeforeAll
    public static void beforeSuite() throws Exception {
        createFile(project, "suite.robot",
                "*** Test Cases ***",
                "case",
                "  Log  10");
    }

    @AfterEach
    public void after() throws CoreException {
        getFile(project, "suite.robot").deleteMarkers(RobotLineBreakpoint.MARKER_ID, true, 1);
    }

    @Test
    public void checkPropertiesOfNewlyCreatedBreakpoint() throws CoreException {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);

        assertThat(breakpoint.isEnabled()).isTrue();
        assertThat(breakpoint.getLocation()).isEqualTo("suite.robot");
        assertThat(breakpoint.getLineNumber()).isEqualTo(3);
        assertThat(breakpoint.isHitCountEnabled()).isFalse();
        assertThat(breakpoint.getHitCount()).isEqualTo(1);
        assertThat(breakpoint.isConditionEnabled()).isFalse();
        assertThat(breakpoint.getConditionExpression()).isEmpty();
    }

    @Test
    public void idOfBreakpointIsEqualToRobotDebugModelId() throws CoreException {
        assertThat(new RobotLineBreakpoint().getModelIdentifier()).isEqualTo(RobotDebugElement.DEBUG_MODEL_ID);
    }

    @Test
    public void locationIsTakenFromMarker() throws CoreException {
        final IMarker markerWithLocation = mock(IMarker.class);
        when(markerWithLocation.getAttribute(IMarker.LOCATION, "")).thenReturn("location");

        assertThat(new RobotLineBreakpoint(null).getLocation()).isEmpty();
        assertThat(new RobotLineBreakpoint(markerWithLocation).getLocation()).isEqualTo("location");
    }

    @Test
    public void isHitCountEnabledIsTakenFromMarker() throws CoreException {
        final IMarker markerWithHitCount = mock(IMarker.class);
        when(markerWithHitCount.getAttribute(RobotLineBreakpoint.HIT_COUNT_ENABLED_ATTRIBUTE, false))
                .thenReturn(Boolean.TRUE);

        assertThat(new RobotLineBreakpoint(null).isHitCountEnabled()).isFalse();
        assertThat(new RobotLineBreakpoint(markerWithHitCount).isHitCountEnabled()).isTrue();
    }

    @Test
    public void isHitCountEnabledIsSetProperly() throws CoreException {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);

        breakpoint.setHitCountEnabled(false);
        assertThat(breakpoint.isHitCountEnabled()).isFalse();
        breakpoint.setHitCountEnabled(true);
        assertThat(breakpoint.isHitCountEnabled()).isTrue();
        breakpoint.setHitCountEnabled(false);
        assertThat(breakpoint.isHitCountEnabled()).isFalse();
    }

    @Test
    public void hitCountIsTakenFromMarker() throws CoreException {
        final IMarker markerWithHitCount = mock(IMarker.class);
        when(markerWithHitCount.getAttribute(RobotLineBreakpoint.HIT_COUNT_ATTRIBUTE, 1)).thenReturn(42);

        assertThat(new RobotLineBreakpoint(null).getHitCount()).isEqualTo(1);
        assertThat(new RobotLineBreakpoint(markerWithHitCount).getHitCount()).isEqualTo(42);
    }

    @Test
    public void hitCountIsSetProperly() throws CoreException {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);

        breakpoint.setHitCount(10);
        assertThat(breakpoint.getHitCount()).isEqualTo(10);

        breakpoint.setHitCount(42);
        assertThat(breakpoint.getHitCount()).isEqualTo(42);

        breakpoint.setHitCount(42);
        assertThat(breakpoint.getHitCount()).isEqualTo(42);
    }

    @Test
    public void isConditionEnabledIsTakenFromMarker() throws CoreException {
        final IMarker markerWithCondition = mock(IMarker.class);
        when(markerWithCondition.getAttribute(RobotLineBreakpoint.CONDITION_ENABLED_ATTRIBUTE, false))
                .thenReturn(Boolean.TRUE);

        assertThat(new RobotLineBreakpoint(null).isConditionEnabled()).isFalse();
        assertThat(new RobotLineBreakpoint(markerWithCondition).isConditionEnabled()).isTrue();
    }

    @Test
    public void isConditionEnabledIsSetProperly() throws CoreException {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);

        breakpoint.setConditionEnabled(false);
        assertThat(breakpoint.isConditionEnabled()).isFalse();
        breakpoint.setConditionEnabled(true);
        assertThat(breakpoint.isConditionEnabled()).isTrue();
        breakpoint.setConditionEnabled(false);
        assertThat(breakpoint.isConditionEnabled()).isFalse();
    }

    @Test
    public void conditionIsTakenFromMarker() throws CoreException {
        final IMarker markerWithCondition = mock(IMarker.class);
        when(markerWithCondition.getAttribute(RobotLineBreakpoint.CONDITION_ATTRIBUTE, "")).thenReturn("condition");

        assertThat(new RobotLineBreakpoint(null).getConditionExpression()).isEqualTo("");
        assertThat(new RobotLineBreakpoint(markerWithCondition).getConditionExpression()).isEqualTo("condition");
    }

    @Test
    public void conditionIsSetProperly() throws CoreException {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);

        breakpoint.setCondition("cond");
        assertThat(breakpoint.getConditionExpression()).isEqualTo("cond");

        breakpoint.setCondition("other cond");
        assertThat(breakpoint.getConditionExpression()).isEqualTo("other cond");

        breakpoint.setCondition("other cond");
        assertThat(breakpoint.getConditionExpression()).isEqualTo("other cond");
    }

    @Test
    public void breakpointLabelTest() throws CoreException {
        final IFile file = getFile(project, "suite.robot");

        final RobotLineBreakpoint bp1 = new RobotLineBreakpoint(file, 3);
        assertThat(bp1.getLabel()).isEqualTo("suite.robot [line: 3]");

        final RobotLineBreakpoint bp2 = new RobotLineBreakpoint(file, 3);
        bp2.setHitCountEnabled(true);
        assertThat(bp2.getLabel()).isEqualTo("suite.robot [line: 3] [hit count: 1]");

        final RobotLineBreakpoint bp3 = new RobotLineBreakpoint(file, 3);
        bp3.setHitCountEnabled(true);
        bp3.setHitCount(15);
        assertThat(bp3.getLabel()).isEqualTo("suite.robot [line: 3] [hit count: 15]");

        final RobotLineBreakpoint bp4 = new RobotLineBreakpoint(file, 3);
        bp4.setConditionEnabled(true);
        assertThat(bp4.getLabel()).isEqualTo("suite.robot [line: 3] [conditional]");

        final RobotLineBreakpoint bp5 = new RobotLineBreakpoint(file, 3);
        bp5.setConditionEnabled(true);
        bp5.setCondition("condition");
        assertThat(bp5.getLabel()).isEqualTo("suite.robot [line: 3] [conditional]");

        final RobotLineBreakpoint bp6 = new RobotLineBreakpoint(file, 3);
        bp6.setHitCountEnabled(true);
        bp6.setHitCount(42);
        bp6.setConditionEnabled(true);
        bp6.setCondition("condition");
        assertThat(bp6.getLabel()).isEqualTo("suite.robot [line: 3] [hit count: 42] [conditional]");
    }

    @Test
    public void hitCountIsAlwaysSatisfied_whenHitCountIsDisabled() throws Exception {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);
        breakpoint.setHitCountEnabled(false);

        for (int i = 0; i < 20; i++) {
            assertThat(breakpoint.evaluateHitCount()).isTrue();
        }
    }

    @Test
    public void hitCountIsSatisfied_whenItIsBeingHitForTheGivenTime() throws Exception {
        final RobotLineBreakpoint breakpoint = new RobotLineBreakpoint(getFile(project, "suite.robot"), 3);
        breakpoint.setHitCountEnabled(true);
        breakpoint.setHitCount(10);

        for (int i = 0; i < 9; i++) {
            assertThat(breakpoint.evaluateHitCount()).isFalse();
            assertThat(breakpoint.isEnabled()).isTrue();
        }
        assertThat(breakpoint.evaluateHitCount()).isTrue();
        assertThat(breakpoint.isEnabled()).isFalse();
    }

    @Test
    public void breakpointDisabledBySatisfiedCountAreReenabled() throws Exception {
        final IFile file = getFile(project, "suite.robot");

        final RobotLineBreakpoint breakpoint1 = new RobotLineBreakpoint(file, 2);
        breakpoint1.setHitCountEnabled(true);
        breakpoint1.setHitCount(10);
        final RobotLineBreakpoint breakpoint2 = new RobotLineBreakpoint(file, 3);
        breakpoint2.setEnabled(false);

        for (int i = 0; i < 10; i++) {
            breakpoint1.evaluateHitCount();
        }
        assertThat(breakpoint1.isEnabled()).isFalse();
        assertThat(breakpoint2.isEnabled()).isFalse();

        breakpoint1.enableIfDisabledByHitCounter();
        breakpoint2.enableIfDisabledByHitCounter();

        assertThat(breakpoint1.isEnabled()).isTrue();
        assertThat(breakpoint2.isEnabled()).isFalse();
    }
}
