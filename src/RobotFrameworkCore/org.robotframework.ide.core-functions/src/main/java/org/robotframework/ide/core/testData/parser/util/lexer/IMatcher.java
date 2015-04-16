package org.robotframework.ide.core.testData.parser.util.lexer;

/**
 * Take responsibility for perform matching process base on own wrote logic.
 * 
 * @author wypych
 * @serial RobotFramework 2.8.6
 * @serial 1.0
 * 
 */
public interface IMatcher {

    /**
     * check if current data contains data expected
     * 
     * @param dataWithPosition
     *            to check, which contains given information from where we
     *            should start and where last position to check
     * @return
     */
    MatchResult match(final DataMarked dataWithPosition);
}
