/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.model.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.robotframework.ide.eclipse.main.plugin.mockeclipse.ContextInjector;
import org.robotframework.ide.eclipse.main.plugin.mockmodel.RobotSuiteFileCreator;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCasesSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordsSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotModelEvents;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSettingsSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.ide.eclipse.main.plugin.model.RobotVariablesSection;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.EditorCommand;

public class CreateFreshSectionCommandTest {

    private IEventBroker eventBroker;

    @BeforeEach
    public void beforeTest() {
        eventBroker = mock(IEventBroker.class);
    }

    @Test
    public void exceptionIsThrown_whenTryingToCreateUnknownSection() {
        final RobotSuiteFile model = new RobotSuiteFileCreator().build();

        assertThatIllegalStateException().isThrownBy(() -> ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new CreateFreshSectionCommand(model, "invalid"))
                .execute());

        verifyNoInteractions(eventBroker);
    }

    @Test
    public void nothingIsCreated_whenSectionAlreadyExist() {
        final RobotSuiteFile model = new RobotSuiteFileCreator().appendLine("*** Variables ***").build();
        final RobotVariablesSection currentSection = model.findSection(RobotVariablesSection.class).get();

        final CreateFreshSectionCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new CreateFreshSectionCommand(model, RobotVariablesSection.SECTION_NAME));
        command.execute();

        assertThat(model.getChildren()).hasSize(1);
        assertThat(model.getChildren().get(0)).isSameAs(currentSection);

        for (final EditorCommand undo : command.getUndoCommands()) {
            undo.execute();
        }

        assertThat(model.getChildren()).hasSize(1);
        assertThat(model.getChildren().get(0)).isSameAs(currentSection);

        verifyNoInteractions(eventBroker);
    }

    @Test
    public void variablesSectionIsProperlyCreated() {
        final RobotSuiteFile model = new RobotSuiteFileCreator().build();
        final CreateFreshSectionCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new CreateFreshSectionCommand(model, RobotVariablesSection.SECTION_NAME));
        command.execute();

        assertThat(model.getChildren()).hasSize(1);
        assertThat(model.getChildren().get(0)).isInstanceOf(RobotVariablesSection.class);
        assertThat(model.getChildren().get(0).getParent()).isSameAs(model);
        assertThat(model.getLinkedElement().getVariableTable().isPresent()).isTrue();
        assertThat(model.getLinkedElement().getVariableTable().getParent()).isSameAs(model.getLinkedElement());

        for (final EditorCommand undo : command.getUndoCommands()) {
            undo.execute();
        }

        assertThat(model.getChildren()).isEmpty();
        assertThat(model.getLinkedElement().getVariableTable().isPresent()).isFalse();

        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_ADDED, model);
        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_REMOVED, model);
        verifyNoMoreInteractions(eventBroker);
    }

    @Test
    public void settingsSectionIsProperlyCreated() {
        final RobotSuiteFile model = new RobotSuiteFileCreator().build();
        final CreateFreshSectionCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new CreateFreshSectionCommand(model, RobotSettingsSection.SECTION_NAME));
        command.execute();

        assertThat(model.getChildren()).hasSize(1);
        assertThat(model.getChildren().get(0)).isInstanceOf(RobotSettingsSection.class);
        assertThat(model.getChildren().get(0).getParent()).isSameAs(model);
        assertThat(model.getLinkedElement().getSettingTable().isPresent()).isTrue();
        assertThat(model.getLinkedElement().getSettingTable().getParent()).isSameAs(model.getLinkedElement());

        for (final EditorCommand undo : command.getUndoCommands()) {
            undo.execute();
        }

        assertThat(model.getChildren()).isEmpty();
        assertThat(model.getLinkedElement().getSettingTable().isPresent()).isFalse();

        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_ADDED, model);
        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_REMOVED, model);
        verifyNoMoreInteractions(eventBroker);
    }

    @Test
    public void keywordsSectionIsProperlyCreated() {
        final RobotSuiteFile model = new RobotSuiteFileCreator().build();
        final CreateFreshSectionCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new CreateFreshSectionCommand(model, RobotKeywordsSection.SECTION_NAME));
        command.execute();

        assertThat(model.getChildren()).hasSize(1);
        assertThat(model.getChildren().get(0)).isInstanceOf(RobotKeywordsSection.class);
        assertThat(model.getChildren().get(0).getParent()).isSameAs(model);
        assertThat(model.getLinkedElement().getKeywordTable().isPresent()).isTrue();
        assertThat(model.getLinkedElement().getKeywordTable().getParent()).isSameAs(model.getLinkedElement());

        for (final EditorCommand undo : command.getUndoCommands()) {
            undo.execute();
        }

        assertThat(model.getChildren()).isEmpty();
        assertThat(model.getLinkedElement().getKeywordTable().isPresent()).isFalse();

        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_ADDED, model);
        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_REMOVED, model);
        verifyNoMoreInteractions(eventBroker);
    }

    @Test
    public void testCasesSectionIsProperlyCreated() {
        final RobotSuiteFile model = new RobotSuiteFileCreator().build();
        final CreateFreshSectionCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new CreateFreshSectionCommand(model, RobotCasesSection.SECTION_NAME));
        command.execute();

        assertThat(model.getChildren()).hasSize(1);
        assertThat(model.getChildren().get(0)).isInstanceOf(RobotCasesSection.class);
        assertThat(model.getChildren().get(0).getParent()).isSameAs(model);
        assertThat(model.getLinkedElement().getTestCaseTable().isPresent()).isTrue();
        assertThat(model.getLinkedElement().getTestCaseTable().getParent()).isSameAs(model.getLinkedElement());

        for (final EditorCommand undo : command.getUndoCommands()) {
            undo.execute();
        }

        assertThat(model.getChildren()).isEmpty();
        assertThat(model.getLinkedElement().getTestCaseTable().isPresent()).isFalse();

        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_ADDED, model);
        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_SUITE_SECTION_REMOVED, model);
        verifyNoMoreInteractions(eventBroker);
    }

}
