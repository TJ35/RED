/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.model;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Position;
import org.eclipse.ui.IWorkbenchPage;
import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.ICommentHolder;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.presenter.CommentServiceHandler;
import org.rf.ide.core.testdata.model.presenter.CommentServiceHandler.ETokenSeparator;
import org.rf.ide.core.testdata.model.table.RobotExecutableRow;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.robotframework.ide.eclipse.main.plugin.RedImages;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

public class RobotKeywordCall implements RobotFileInternalElement, Serializable {

    private static final long serialVersionUID = 1L;
    
    private transient IRobotCodeHoldingElement parent;

    private final AModelElement<?> linkedElement;

    protected transient List<String> arguments;

    private transient String comment;

    public RobotKeywordCall(final IRobotCodeHoldingElement parent, final AModelElement<?> linkedElement) {
        this.parent = parent;
        this.linkedElement = linkedElement;
    }

    @Override
    public AModelElement<?> getLinkedElement() {
        return linkedElement;
    }

    @Override
    public String getName() {
        return linkedElement.getDeclaration().getText();
    }

    public String getLabel() {
        if (linkedElement.getModelType() == ModelType.TEST_CASE_EXECUTABLE_ROW
                || linkedElement.getModelType() == ModelType.USER_KEYWORD_EXECUTABLE_ROW) {
            final RobotExecutableRow<?> row = (RobotExecutableRow<?>) linkedElement;
            return row.buildLineDescription().getAction().getToken().getText();
        } else {
            return linkedElement.getElementTokens().get(0).getText();
        }
    }

    @Override
    public IRobotCodeHoldingElement getParent() {
        return parent;
    }

    public void setParent(final IRobotCodeHoldingElement parent) {
        this.parent = parent;
    }

    @Override
    public RobotSuiteFile getSuiteFile() {
        return parent.getSuiteFile();
    }

    @Override
    public List<RobotElement> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public int getIndex() {
        return parent == null ? -1 : parent.getChildren().indexOf(this);
    }

    public boolean isExecutable() {
        return linkedElement.getModelType() == ModelType.TEST_CASE_EXECUTABLE_ROW
                || linkedElement.getModelType() == ModelType.USER_KEYWORD_EXECUTABLE_ROW;
    }

    @Override
    public ImageDescriptor getImage() {
        return RedImages.getKeywordImage();
    }

    public List<String> getArguments() {
        if (arguments == null) {
            final List<RobotToken> allTokens = linkedElement.getElementTokens();
            final Iterable<RobotToken> tokensWithoutComments = filter(allTokens, new Predicate<RobotToken>() {

                @Override
                public boolean apply(final RobotToken token) {
                    return !token.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            && !token.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)
                            && !token.getTypes().contains(RobotTokenType.KEYWORD_ACTION_NAME)
                            && !token.getTypes().contains(RobotTokenType.TEST_CASE_ACTION_NAME);
                }
            });
            arguments = newArrayList(transform(tokensWithoutComments, TokenFunctions.tokenToString()));
        }
        return arguments;
    }

    public void resetStored() {
        arguments = null;
        comment = null;
    }
    
    @Override
    public String getComment() {
        if (comment == null) {
            comment = CommentServiceHandler.consolidate((ICommentHolder) linkedElement,
                    ETokenSeparator.PIPE_WRAPPED_WITH_SPACE);
        }
        return comment;
    }

    public RobotSuiteFileSection getSection() {
        RobotElement current = this;
        while (current != null && !(current instanceof RobotSuiteFileSection)) {
            current = current.getParent();
        }
        return (RobotSuiteFileSection) current;
    }

    @Override
    public Position getPosition() {
        return new Position(0);
    }

    @Override
    public DefinitionPosition getDefinitionPosition() {
        RobotToken token;
        if (linkedElement instanceof RobotExecutableRow<?>) {
            final RobotExecutableRow<?> row = (RobotExecutableRow<?>) linkedElement;
            token = row.buildLineDescription().getAction().getToken();
        } else {
            token = linkedElement.getElementTokens().get(0);
        }

        if (token.getFilePosition().isNotSet()) {
            token = linkedElement.getElementTokens().get(0);
        }

        return new DefinitionPosition(token.getFilePosition(), token.getText().length());
    }

    @Override
    public Optional<? extends RobotElement> findElement(final int offset) {
        if (!linkedElement.getBeginPosition().isNotSet() && linkedElement.getBeginPosition().getOffset() <= offset
                && offset <= linkedElement.getEndPosition().getOffset()) {
            return Optional.of(this);
        }
        return Optional.absent();
    }

    @Override
    public OpenStrategy getOpenRobotEditorStrategy(final IWorkbenchPage page) {
        return new PageActivatingOpeningStrategy(page, getSuiteFile().getFile(), getSection(), this);
    }

    @Override
    public String toString() {
        // for debugging purposes only
        return getName();
    }
}
