package org.robotframework.ide.eclipse.main.plugin.model.cmd.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.junit.Test;
import org.robotframework.ide.eclipse.main.plugin.mockeclipse.ContextInjector;
import org.robotframework.ide.eclipse.main.plugin.mockmodel.RobotSuiteFileCreator;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCasesSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotModelEvents;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;

public class SetCaseNameCommandTest {

    @Test
    public void nothingHappens_whenNewNameIsEqualToOldOne() {
        final RobotCase testCase = createTestCase("case 1");

        final IEventBroker eventBroker = mock(IEventBroker.class);
        final SetCaseNameCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new SetCaseNameCommand(testCase, "case 1"));
        command.execute();

        assertThat(testCase.getName()).isEqualTo("case 1");

        verifyZeroInteractions(eventBroker);
    }

    @Test
    public void nameIsProperlyChanged_whenNewNameIsDifferentThanOldOne() {
        final RobotCase testCase = createTestCase("case 1");

        final IEventBroker eventBroker = mock(IEventBroker.class);
        final SetCaseNameCommand command = ContextInjector.prepareContext()
                .inWhich(eventBroker)
                .isInjectedInto(new SetCaseNameCommand(testCase, "new case"));
        command.execute();

        assertThat(testCase.getName()).isEqualTo("new case");

        verify(eventBroker, times(1)).send(RobotModelEvents.ROBOT_CASE_NAME_CHANGE, testCase);
    }

    private static RobotCase createTestCase(final String caseName) {
        final RobotSuiteFile model = new RobotSuiteFileCreator().appendLine("*** Test Cases ***")
                .appendLine(caseName)
                .appendLine("  Log  10")
                .build();
        final RobotCasesSection section = model.findSection(RobotCasesSection.class).get();
        return section.getChildren().get(0);
    }
}
