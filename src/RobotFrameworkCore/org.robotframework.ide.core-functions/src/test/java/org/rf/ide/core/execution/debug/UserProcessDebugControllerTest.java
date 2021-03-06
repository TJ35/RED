/*
* Copyright 2017 Nokia Solutions and Networks
* Licensed under the Apache License, Version 2.0,
* see license.txt file for details.
*/
package org.rf.ide.core.execution.debug;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.FutureTask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.rf.ide.core.execution.agent.PausingPoint;
import org.rf.ide.core.execution.agent.event.ConditionEvaluatedEvent;
import org.rf.ide.core.execution.debug.StackFrame.FrameCategory;
import org.rf.ide.core.execution.debug.UserProcessController.ResponseWithCallback;
import org.rf.ide.core.execution.debug.UserProcessDebugController.DebuggerPreferences;
import org.rf.ide.core.execution.debug.UserProcessDebugController.PauseReasonListener;
import org.rf.ide.core.execution.debug.UserProcessDebugController.SteppingMode;
import org.rf.ide.core.execution.debug.UserProcessDebugController.SuspendReason;
import org.rf.ide.core.execution.debug.UserProcessDebugController.SuspensionData;
import org.rf.ide.core.execution.debug.contexts.KeywordContext;
import org.rf.ide.core.execution.server.response.ChangeVariable;
import org.rf.ide.core.execution.server.response.DisconnectExecution;
import org.rf.ide.core.execution.server.response.EvaluateCondition;
import org.rf.ide.core.execution.server.response.EvaluateExpression;
import org.rf.ide.core.execution.server.response.PauseExecution;
import org.rf.ide.core.execution.server.response.ResumeExecution;
import org.rf.ide.core.execution.server.response.ServerResponse;
import org.rf.ide.core.execution.server.response.TerminateExecution;
import org.rf.ide.core.testdata.model.table.keywords.names.QualifiedKeywordName;
import org.rf.ide.core.testdata.model.table.variables.AVariable.VariableScope;

public class UserProcessDebugControllerTest {

    @Test
    public void suspensionDataIsCleared_whenConditionHasBeenEvaluatedToFalse() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.USER_REQUEST));
        assertThat(controller.getSuspensionData()).isNotNull();

        controller.conditionEvaluated(new ConditionEvaluatedEvent(false));

        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void suspensionDataIsNotCleared_whenConditionHasBeenEvaluatedToTrue() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.USER_REQUEST));
        assertThat(controller.getSuspensionData()).isNotNull();

        controller.conditionEvaluated(new ConditionEvaluatedEvent(true));

        assertThat(controller.getSuspensionData()).isNotNull();
    }

    @Test
    public void suspensionDataIsNotCleared_whenConditionWasNotEvaluatedDueToError() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.USER_REQUEST));
        assertThat(controller.getSuspensionData()).isNotNull();

        controller.conditionEvaluated(new ConditionEvaluatedEvent("error"));

        assertThat(controller.getSuspensionData()).isNotNull();
    }

    @Test
    public void whenExecutionPausesForAnyReason_framesAreUnmarkedWithSteppingFlagAndSuspensionDataIsCleared() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("a", FrameCategory.SUITE, 0, mock(StackFrameContext.class)));
        stack.push(new StackFrame("b", FrameCategory.TEST, 1, mock(StackFrameContext.class)));
        stack.push(new StackFrame("c", FrameCategory.KEYWORD, 2, mock(StackFrameContext.class)));

        stack.forEach(f -> f.mark(StackFrameMarker.STEPPING));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(null));

        assertThat(controller.getSuspensionData()).isNotNull();
        assertThat(stack).allMatch(f -> f.isMarkedStepping());

        controller.executionPaused();

        assertThat(controller.getSuspensionData()).isNull();
        assertThat(stack).allMatch(f -> !f.isMarkedStepping());
    }

    @Test
    public void whenExecutionPausesWithControllerBeingInBreakpointState_listenersAreNotified() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final PauseReasonListener listener1 = mock(PauseReasonListener.class);
        final PauseReasonListener listener2 = mock(PauseReasonListener.class);

        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.whenSuspended(listener1);
        controller.whenSuspended(listener2);
        controller.setSuspensionData(new SuspensionData(SuspendReason.BREAKPOINT, breakpoint));

        controller.executionPaused();

        verify(listener1).pausedOnBreakpoint(breakpoint);
        verify(listener2).pausedOnBreakpoint(breakpoint);
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    public void whenExecutionPausesWithControllerBeingInUserRequestState_listenersAreNotified() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final PauseReasonListener listener1 = mock(PauseReasonListener.class);
        final PauseReasonListener listener2 = mock(PauseReasonListener.class);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.whenSuspended(listener1);
        controller.whenSuspended(listener2);
        controller.setSuspensionData(new SuspensionData(SuspendReason.USER_REQUEST));

        controller.executionPaused();

        verify(listener1).pausedByUser();
        verify(listener2).pausedByUser();
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    public void whenExecutionPausesWithControllerBeingInSteppingState_listenersAreNotified() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final PauseReasonListener listener1 = mock(PauseReasonListener.class);
        final PauseReasonListener listener2 = mock(PauseReasonListener.class);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.whenSuspended(listener1);
        controller.whenSuspended(listener2);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING));

        assertThat(controller.isStepping()).isTrue();

        controller.executionPaused();

        assertThat(controller.isStepping()).isFalse();

        verify(listener1).pausedByStepping();
        verify(listener2).pausedByStepping();
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    public void whenExecutionPausesWithControllerBeingInVariableChangeState_listenersAreNotified() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final PauseReasonListener listener1 = mock(PauseReasonListener.class);
        final PauseReasonListener listener2 = mock(PauseReasonListener.class);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.whenSuspended(listener1);
        controller.whenSuspended(listener2);
        controller.setSuspensionData(new SuspensionData(SuspendReason.VARIABLE_CHANGE));

        controller.executionPaused();

        verify(listener1).pausedAfterVariableChange();
        verify(listener2).pausedAfterVariableChange();
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    public void whenExecutionPausesWithControllerBeingErrorDetectedState_listenersAreNotified() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final PauseReasonListener listener1 = mock(PauseReasonListener.class);
        final PauseReasonListener listener2 = mock(PauseReasonListener.class);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.whenSuspended(listener1);
        controller.whenSuspended(listener2);
        controller.setSuspensionData(new SuspensionData(SuspendReason.ERRONEOUS_STATE, "error"));
        controller.executionPaused();

        verify(listener1).pausedOnError("error");
        verify(listener2).pausedOnError("error");
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    public void whenExecutionPausesWithControllerBeingInExpressionEvaluationState_listenersAreNotified() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);

        final PauseReasonListener listener1 = mock(PauseReasonListener.class);
        final PauseReasonListener listener2 = mock(PauseReasonListener.class);

        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.whenSuspended(listener1);
        controller.whenSuspended(listener2);
        controller.setSuspensionData(new SuspensionData(SuspendReason.EXPRESSION_EVALUATED));
        controller.executionPaused();

        verify(listener1).pausedAfterExpressionEvaluated();
        verify(listener2).pausedAfterExpressionEvaluated();
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    public void thereIsNoResponse_forNewlyCreatedController() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);
        assertThat(response).isEmpty();
    }

    @Test
    public void whenDisconnectWasOrdered_callbackRunsAndResponseIsProperlyReturned() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        final Runnable callback = mock(Runnable.class);
        controller.disconnect(callback);

        assertThat(controller.manualUserResponse).hasSize(1);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(DisconnectExecution.class);
        verify(callback).run();
    }

    @Test
    public void whenTerminateWasOrdered_callbackRunsAndResponseIsProperlyReturned() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        final Runnable callback = mock(Runnable.class);
        controller.terminate(callback);

        assertThat(controller.manualUserResponse).hasSize(1);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(TerminateExecution.class);
        verify(callback).run();
    }

    @Test
    public void whenPauseWasOrdered_callbackRunsAndResponseIsProperlyReturned() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        final Runnable callback = mock(Runnable.class);
        controller.pause(callback);

        assertThat(controller.manualUserResponse).hasSize(1);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.USER_REQUEST);
        assertThat(controller.getSuspensionData().data).isEmpty();
        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(PauseExecution.class);
        verify(callback).run();
    }

    @Test
    public void whenResumeWasOrdered_callbackRunsAndResponseIsProperlyReturned() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        final Runnable callback = mock(Runnable.class);
        controller.resume(callback);

        assertThat(controller.manualUserResponse).hasSize(1);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(ResumeExecution.class);
        verify(callback).run();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "START_KEYWORD" })
    public void pauseResponseIsReturned_whenThereIsAnErroneousFrameAndPauseOnErrorIsEnabled(
            final PausingPoint pausingPoint) {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, erroneousContext("error msg")));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> true, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);

        assertThat(response).isNotEmpty().containsInstanceOf(PauseExecution.class);
        assertThat(stack.stream().filter(StackFrame::isErroneous)).allMatch(StackFrame::isMarkedError);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.ERRONEOUS_STATE);
        assertThat(controller.getSuspensionData().data).containsOnly("error msg");
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_END_KEYWORD", "END_KEYWORD" })
    public void noResponseIsReturned_whenThereIsAnErroneousFramePauseOnErrorIsEnabledButPausingPointIsAtTheEnd(
            final PausingPoint pausingPoint) {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, erroneousContext("error msg")));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> true, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);

        assertThat(response).isEmpty();
        assertThat(stack.stream().filter(StackFrame::isErroneous)).allMatch(not(StackFrame::isMarkedError));
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void noResponseIsReturned_whenThereIsAnErroneousFrameButPauseOnErrorIsDisabledAndFrameGetsMarked() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, erroneousContext("error msg")));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(response).isEmpty();
        assertThat(stack.stream().filter(StackFrame::isErroneous)).allMatch(StackFrame::isMarkedError);
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void noResponseIsReturned_whenThereIsAnErroneousFrameAndPauseOnErrorIsEnabledButFrameIsAlreadyMarked() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, erroneousContext("error msg")));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.ERROR);

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> true, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(response).isEmpty();
        assertThat(stack.stream().filter(StackFrame::isErroneous)).allMatch(StackFrame::isMarkedError);
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void pauseResponseIsReturned_whenThereIsABreakpointWithoutConditionWithHitCountFulfilled() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);
        when(breakpoint.isConditionEnabled()).thenReturn(false);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, breakpointContext(breakpoint)));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);

        assertThat(response).isNotEmpty().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.BREAKPOINT);
        assertThat(controller.getSuspensionData().data).containsOnly(breakpoint);
    }

    @Test
    public void evaluateConditionResponseIsReturned_whenThereIsABreakpointWithConditionWithHitCountFulfilled() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);
        when(breakpoint.isConditionEnabled()).thenReturn(true);
        when(breakpoint.getCondition()).thenReturn(newArrayList("Assert Equals", "1", "2"));

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, breakpointContext(breakpoint)));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);

        assertThat(response).isNotEmpty().containsInstanceOf(EvaluateCondition.class);
        assertThat(response.get().toMessage()).isEqualTo("{\"evaluate_condition\":[\"Assert Equals\",\"1\",\"2\"]}");
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.BREAKPOINT);
        assertThat(controller.getSuspensionData().data).containsOnly(breakpoint);
    }

    @Test
    public void noResponseIsReturned_whenThereIsABreakpointButHitCountIsNotFulfilled() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(false);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, breakpointContext(breakpoint)));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void noResponseIsReturned_whenThereIsNoBreakpoint() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD" }, mode = EnumSource.Mode.EXCLUDE)
    public void noResponseIsReturned_whenThereIsABreakpointButPausingPointIsOtherThanPreStart(
            final PausingPoint point) {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);
        when(breakpoint.isConditionEnabled()).thenReturn(false);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, breakpointContext(breakpoint)));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(point, null);

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void pauseResponseIsReturned_whenThereIsAKwFailBreakpointWithoutConditionWithHitCountFulfilledAndMatchingName() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);
        when(breakpoint.isConditionEnabled()).thenReturn(false);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2,
                failBreakpointContext(breakpoint, QualifiedKeywordName.create("kw", "lib"))));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_END_KEYWORD,
                QualifiedKeywordName.create("kw", "lib"));

        assertThat(response).isNotEmpty().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.BREAKPOINT);
        assertThat(controller.getSuspensionData().data).containsOnly(breakpoint);
    }

    @Test
    public void evaluateConditionResponseIsReturned_whenThereIsAKwFailBreakpointWithConditionWithHitCountFulfilledAndMatchingName() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);
        when(breakpoint.isConditionEnabled()).thenReturn(true);
        when(breakpoint.getCondition()).thenReturn(newArrayList("Assert Equals", "1", "2"));

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2,
                failBreakpointContext(breakpoint, QualifiedKeywordName.create("kw", "lib"))));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_END_KEYWORD,
                QualifiedKeywordName.create("kw", "lib"));

        assertThat(response).isNotEmpty().containsInstanceOf(EvaluateCondition.class);
        assertThat(response.get().toMessage()).isEqualTo("{\"evaluate_condition\":[\"Assert Equals\",\"1\",\"2\"]}");
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.BREAKPOINT);
        assertThat(controller.getSuspensionData().data).containsOnly(breakpoint);
    }

    @Test
    public void noResponseIsReturned_whenThereIsAKwFailBreakpointWithMatchingNameButHitCountIsNotFulfilled() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(false);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2,
                failBreakpointContext(breakpoint, QualifiedKeywordName.create("kw", "lib"))));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_END_KEYWORD,
                QualifiedKeywordName.create("kw", "lib"));

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void noResponseIsReturned_whenThereIsAKwFailBreakpointWithSomeDifferentNameOfKeyword() {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2,
                failBreakpointContext(breakpoint, QualifiedKeywordName.create("other kw", "lib"))));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_END_KEYWORD,
                QualifiedKeywordName.create("kw", "lib"));

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @Test
    public void noResponseIsReturned_whenThereIsNoKwFailBreakpoint() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_END_KEYWORD,
                QualifiedKeywordName.create("kw", "lib"));

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_END_KEYWORD" }, mode = EnumSource.Mode.EXCLUDE)
    public void noResponseIsReturned_whenThereIsAKwFailBreakpointButPausingPointIsOtherThanPreEnd(
            final PausingPoint point) {
        final RobotBreakpoint breakpoint = mock(RobotBreakpoint.class);
        when(breakpoint.evaluateHitCount()).thenReturn(true);
        when(breakpoint.isConditionEnabled()).thenReturn(false);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2,
                failBreakpointContext(breakpoint, QualifiedKeywordName.create("kw", "lib"))));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final Optional<ServerResponse> response = controller.takeCurrentResponse(point,
                QualifiedKeywordName.create("kw", "lib"));

        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "END_KEYWORD" }, mode = EnumSource.Mode.EXCLUDE)
    public void pauseResponseIsReturned_whenSteppingIntoAndNotOmittingLibKeywords(final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.INTO, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.INTO, whenSent);
        verify(whenSent).run();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "END_KEYWORD",
            "PRE_END_KEYWORD" }, mode = EnumSource.Mode.EXCLUDE)
    public void pauseResponseIsReturned_whenSteppingIntoAndOmittingLibKeywords(final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.INTO, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.INTO, whenSent);
        verify(whenSent).run();
    }

    @Test
    public void noResponseIsReturned_whenSteppingIntoButPausingPointIsAtEndKeyword() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.INTO, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.END_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.INTO, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_END_KEYWORD", "END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingIntoOmittingLibKeywordsButPausingPointIsAtEndKeyword(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.INTO, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.INTO, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void noResponseIsReturned_whenSteppingIntoOmittingLibKeywordsButPausingPointIsAtEndOfLibraryKeyword() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, libKeywordContext()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.INTO, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.INTO, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void noResponseIsReturned_whenSteppingIntoButForFrameIsOnTopOfTheStack() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.push(new StackFrame(":FOR", FrameCategory.FOR, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.INTO, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.INTO, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void pauseResponseIsReturned_whenSteppingOverOnPreStartKeyword_andTopFrameIsMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verify(whenSent).run();
    }

    @Test
    public void pauseResponseIsReturned_whenSteppingOverOnPreStartKeyword_andThereIsNoFrameMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verify(whenSent).run();
    }

    @Test
    public void noResponseIsReturned_whenSteppingOverOnPreStartKeyword_andSomeInnerFrameIsMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword2", FrameCategory.KEYWORD, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);
        stack.push(new StackFrame("keyword1", FrameCategory.KEYWORD, 3, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void noResponseIsReturned_whenSteppingOverOnPreStartKeyword_andTopForFrameIsMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword2", FrameCategory.KEYWORD, 2, context()));
        stack.push(new StackFrame(":FOR", FrameCategory.FOR, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.PRE_START_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_END_KEYWORD", "END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingOverOnPreStartKeyword_andPausingPointIsOnEnd(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword2", FrameCategory.KEYWORD, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void pauseResponseIsReturned_whenSteppingOverOnStartKeywordWithForItemFrame_andForFrameIsMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.push(new StackFrame(":FOR", FrameCategory.FOR, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);
        stack.push(new StackFrame("${i}=0", FrameCategory.FOR_ITEM, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verify(whenSent).run();
    }

    @Test
    public void pauseResponseIsReturned_whenSteppingOverOnStartKeywordWithForItemFrame_andThereIsNoFrameMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.push(new StackFrame(":FOR", FrameCategory.FOR, 2, context()));
        stack.push(new StackFrame("${i}=0", FrameCategory.FOR_ITEM, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verify(whenSent).run();
    }

    @Test
    public void noResponseIsReturned_whenSteppingOverOnStartKeywordWithForItemFrame_andSomeInnerFrameIsMarkedStepping() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);
        stack.push(new StackFrame(":FOR", FrameCategory.FOR, 2, context()));
        stack.push(new StackFrame("${i}=0", FrameCategory.FOR_ITEM, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void noResponseIsReturned_whenSteppingOverOnStartKeywordWithKeywordFrame() {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);
        stack.push(new StackFrame("keyword2", FrameCategory.KEYWORD, 3, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.OVER, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.OVER, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "PRE_END_KEYWORD" })
    public void pauseResponseIsReturned_whenSteppingReturnAndThereIsNoMarkedFrameOnStack_steppingIntoLibEnabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verify(whenSent).run();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "PRE_END_KEYWORD" })
    public void pauseResponseIsReturned_whenSteppingReturnAndThereIsNoMarkedFrameOnStack_steppingIntoLibDisabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verify(whenSent).run();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "PRE_END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingReturnAndThereIsMarkedFrameOnStack_steppingIntoLibEnabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "PRE_END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingReturnAndThereIsMarkedFrameOnStack_steppingIntoLibDisabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));
        stack.peekCurrentFrame().get().mark(StackFrameMarker.STEPPING);

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "START_KEYWORD", "END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingReturnAtStartOrEndPausingPoint_steppingIntoLibEnabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "START_KEYWORD", "END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingReturnAtStartOrEndPausingPoint_steppingIntoLibDisabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verifyNoInteractions(whenSent);
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "PRE_END_KEYWORD" })
    public void pauseResponseIsReturned_whenSteppingReturnAndThereIsLibraryKeywordFrameOnStack_steppingIntoLibEnabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, libKeywordContext()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isPresent().containsInstanceOf(PauseExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verify(whenSent).run();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "PRE_START_KEYWORD", "PRE_END_KEYWORD" })
    public void noResponseIsReturned_whenSteppingReturnAndThereIsLibraryKeywordFrameOnStack_steppingIntoLibDisabled(
            final PausingPoint pausingPoint) {
        final Runnable whenSent = mock(Runnable.class);

        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, libKeywordContext()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, false);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setSuspensionData(new SuspensionData(SuspendReason.STEPPING, SteppingMode.RETURN, whenSent));

        final Optional<ServerResponse> response = controller.takeCurrentResponse(pausingPoint, null);
        assertThat(response).isEmpty();
        assertThat(controller.getSuspensionData().reason).isEqualByComparingTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsOnly(SteppingMode.RETURN, whenSent);
        verifyNoInteractions(whenSent);
    }

    @Test
    public void whenSteppingIntoIsRequested_resumeResponseIsQueuedAndStateOfControllerChangesToStepping() {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        final Runnable callbackWhenSent = mock(Runnable.class);
        final Runnable callbackForStepEnd = mock(Runnable.class);

        controller.stepInto(callbackWhenSent, callbackForStepEnd);

        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(response).containsInstanceOf(ResumeExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsExactly(SteppingMode.INTO, callbackForStepEnd);
        assertThat(controller.manualUserResponse).isEmpty();
        verify(callbackWhenSent).run();
    }

    @Test
    public void whenSteppingOverIsRequestedAndLastPointWasAtStartKeyword_resumeResponseIsQueuedAndStateOfControllerChangesToStepping() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setLastPausingPoint(PausingPoint.START_KEYWORD);
        final Runnable callbackWhenSent = mock(Runnable.class);
        final Runnable callbackForStepEnd = mock(Runnable.class);

        controller.stepOver(stack.peekCurrentFrame().get(), callbackWhenSent, callbackForStepEnd);

        assertThat(stack.stream().filter(StackFrame::isMarkedStepping)).hasSize(1)
                .containsExactly(stack.getFirstFrameSatisfying(f -> f.hasCategory(FrameCategory.TEST)).get());
        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(response).containsInstanceOf(ResumeExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsExactly(SteppingMode.OVER, callbackForStepEnd);
        assertThat(controller.manualUserResponse).isEmpty();
        verify(callbackWhenSent).run();
    }

    @ParameterizedTest
    @EnumSource(value = PausingPoint.class, names = { "START_KEYWORD" }, mode = EnumSource.Mode.EXCLUDE)
    public void whenSteppingOverIsRequestedAndLastPointWasDifferentThanStartKeyword_resumeResponseIsQueuedAndStateOfControllerChangesToStepping(
            final PausingPoint pausingPoint) {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        controller.setLastPausingPoint(pausingPoint);
        final Runnable callbackWhenSent = mock(Runnable.class);
        final Runnable callbackForStepEnd = mock(Runnable.class);

        controller.stepOver(stack.peekCurrentFrame().get(), callbackWhenSent, callbackForStepEnd);

        assertThat(stack.stream().filter(StackFrame::isMarkedStepping)).hasSize(1)
                .containsExactly(stack.peekCurrentFrame().get());
        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(response).containsInstanceOf(ResumeExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsExactly(SteppingMode.OVER, callbackForStepEnd);
        assertThat(controller.manualUserResponse).isEmpty();
        verify(callbackWhenSent).run();
    }

    @Test
    public void whenSteppingReturnIsRequested_resumeResponseIsQueuedAndStateOfControllerChangesToStepping() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);
        final Runnable callbackWhenSent = mock(Runnable.class);
        final Runnable callbackForStepEnd = mock(Runnable.class);

        controller.stepReturn(stack.peekCurrentFrame().get(), callbackWhenSent, callbackForStepEnd);

        assertThat(stack.stream().filter(StackFrame::isMarkedStepping)).hasSize(1)
                .containsExactly(stack.peekCurrentFrame().get());
        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(response).containsInstanceOf(ResumeExecution.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.STEPPING);
        assertThat(controller.getSuspensionData().data).containsExactly(SteppingMode.RETURN, callbackForStepEnd);
        assertThat(controller.manualUserResponse).isEmpty();
        verify(callbackWhenSent).run();
    }

    @Test
    public void whenVariableChangeIsRequested_properResponseIsQueued() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final StackFrameVariable variable = new StackFrameVariable(VariableScope.TEST_SUITE, true, "var", "int", 42);
        controller.changeVariable(stack.peekCurrentFrame().get(), variable, newArrayList("1", "2"));

        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(ChangeVariable.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.VARIABLE_CHANGE);
        assertThat(controller.getSuspensionData().data).isEmpty();
    }

    @Test
    public void whenVariableInnerValueChangeIsRequested_properResponseIsQueued() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final StackFrameVariable variable = new StackFrameVariable(VariableScope.TEST_SUITE, true, "var", "int", 42);
        controller.changeVariableInnerValue(stack.peekCurrentFrame().get(), variable, newArrayList("0", "key"),
                newArrayList("1", "2"));

        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(ChangeVariable.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.VARIABLE_CHANGE);
        assertThat(controller.getSuspensionData().data).isEmpty();
    }

    @Test
    public void whenRobotExpressionEvaluationIsRequested_properResponseIsQueued() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        controller.evaluateRobotKeywordCall(1, "kw", newArrayList("1", "2"));

        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(EvaluateExpression.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.EXPRESSION_EVALUATED);
        assertThat(controller.getSuspensionData().data).isEmpty();
    }

    @Test
    public void whenRobotVariableEvaluationIsRequested_properResponseIsQueued() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        controller.evaluateRobotVariable(1, "${var}");

        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(EvaluateExpression.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.EXPRESSION_EVALUATED);
        assertThat(controller.getSuspensionData().data).isEmpty();
    }

    @Test
    public void whenPythonExpresionEvaluationIsRequested_properResponseIsQueued() {
        final Stacktrace stack = new Stacktrace();
        stack.push(new StackFrame("Suite", FrameCategory.SUITE, 0, context()));
        stack.push(new StackFrame("Test", FrameCategory.TEST, 1, context()));
        stack.push(new StackFrame("keyword", FrameCategory.KEYWORD, 2, context()));

        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        controller.evaluatePythonExpression(1, "[x for x in range(5)]");

        assertThat(controller.manualUserResponse).hasSize(1);
        final Optional<ServerResponse> response = controller.takeCurrentResponse(PausingPoint.START_KEYWORD, null);

        assertThat(controller.manualUserResponse).isEmpty();
        assertThat(response).containsInstanceOf(EvaluateExpression.class);
        assertThat(controller.getSuspensionData().reason).isEqualTo(SuspendReason.EXPRESSION_EVALUATED);
        assertThat(controller.getSuspensionData().data).isEmpty();
    }

    @Test
    public void whenFutureResponseWasOrdered_itIsReturnedAsFutureTask() throws Exception {
        final Stacktrace stack = new Stacktrace();
        final DebuggerPreferences prefs = new DebuggerPreferences(() -> false, true);
        final UserProcessDebugController controller = new UserProcessDebugController(stack, prefs);

        final ServerResponse response = () -> "response";
        final Runnable callback = mock(Runnable.class);
        controller.manualUserResponse.put(new ResponseWithCallback(response, callback));

        final FutureTask<ServerResponse> futureResponse = controller.takeFutureResponse();

        assertThat(controller.manualUserResponse).hasSize(1);

        futureResponse.run();
        assertThat(controller.manualUserResponse).isEmpty();
        verify(callback).run();
    }

    private static StackFrameContext context() {
        return mock(StackFrameContext.class);
    }

    private static StackFrameContext breakpointContext(final RobotBreakpoint breakpoint) {
        final StackFrameContext context = mock(StackFrameContext.class);
        when(context.getLineBreakpoint()).thenReturn(Optional.of(breakpoint));
        return context;
    }

    private static StackFrameContext failBreakpointContext(final RobotBreakpoint breakpoint,
            final QualifiedKeywordName kwName) {
        final StackFrameContext context = mock(StackFrameContext.class);
        when(context.getKeywordFailBreakpoint(kwName)).thenReturn(Optional.of(breakpoint));
        return context;
    }

    private static StackFrameContext erroneousContext(final String errorMsg) {
        final StackFrameContext context = mock(StackFrameContext.class);
        when(context.isErroneous()).thenReturn(true);
        when(context.getErrorMessage()).thenReturn(Optional.of(errorMsg));
        return context;
    }

    private StackFrameContext libKeywordContext() {
        final KeywordContext context = mock(KeywordContext.class);
        when(context.isLibraryKeywordContext()).thenReturn(true);
        return context;
    }
}
