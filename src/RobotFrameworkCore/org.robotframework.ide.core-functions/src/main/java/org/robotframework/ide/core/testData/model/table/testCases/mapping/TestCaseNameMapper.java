/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.core.testData.model.table.testCases.mapping;

import java.util.List;
import java.util.Stack;

import org.robotframework.ide.core.testData.model.FilePosition;
import org.robotframework.ide.core.testData.model.RobotFileOutput;
import org.robotframework.ide.core.testData.model.table.TestCaseTable;
import org.robotframework.ide.core.testData.model.table.mapping.ElementPositionResolver;
import org.robotframework.ide.core.testData.model.table.mapping.ElementPositionResolver.PositionExpected;
import org.robotframework.ide.core.testData.model.table.mapping.IParsingMapper;
import org.robotframework.ide.core.testData.model.table.testCases.TestCase;
import org.robotframework.ide.core.testData.text.read.IRobotTokenType;
import org.robotframework.ide.core.testData.text.read.ParsingState;
import org.robotframework.ide.core.testData.text.read.RobotLine;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotToken;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotTokenType;

import com.google.common.annotations.VisibleForTesting;


public class TestCaseNameMapper implements IParsingMapper {

    private final ElementPositionResolver positionResolver;


    public TestCaseNameMapper() {
        this.positionResolver = new ElementPositionResolver();
    }


    @Override
    public RobotToken map(RobotLine currentLine,
            Stack<ParsingState> processingState,
            RobotFileOutput robotFileOutput, RobotToken rt, FilePosition fp,
            String text) {
        List<IRobotTokenType> types = rt.getTypes();
        types.remove(RobotTokenType.UNKNOWN);
        types.add(0, RobotTokenType.TEST_CASE_NAME);
        rt.setText(new StringBuilder(text));
        rt.setRaw(new StringBuilder(text));

        TestCaseTable testCaseTable = robotFileOutput.getFileModel()
                .getTestCaseTable();
        TestCase testCase = new TestCase(rt);
        testCaseTable.addTest(testCase);
        processingState.push(ParsingState.TEST_CASE_DECLARATION);

        return rt;
    }


    @Override
    public boolean checkIfCanBeMapped(RobotFileOutput robotFileOutput,
            RobotLine currentLine, RobotToken rt, String text,
            Stack<ParsingState> processingState) {
        boolean result = false;
        if (positionResolver.isCorrectPosition(PositionExpected.TEST_CASE_NAME,
                robotFileOutput.getFileModel(), currentLine, rt)) {
            if (isIncludedInTestCaseTable(currentLine, processingState)) {
                result = true;
            } else {
                // FIXME: it is in wrong place means no keyword table
                // declaration
            }
        } else {
            // FIXME: wrong place | | Library or | Library | Library X |
            // case.
        }

        return result;
    }


    @VisibleForTesting
    protected boolean isIncludedInTestCaseTable(final RobotLine line,
            final Stack<ParsingState> processingState) {

        return processingState.contains(ParsingState.TEST_CASE_TABLE_INSIDE);
    }
}
