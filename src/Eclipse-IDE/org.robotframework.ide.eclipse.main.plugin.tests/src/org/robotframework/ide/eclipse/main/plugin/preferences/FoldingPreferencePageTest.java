/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.preferences;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.robotframework.ide.eclipse.main.plugin.RedPreferences;
import org.robotframework.red.junit.jupiter.FreshShell;
import org.robotframework.red.junit.jupiter.FreshShellExtension;

@ExtendWith(FreshShellExtension.class)
public class FoldingPreferencePageTest {

    @FreshShell
    Shell shell;

    @Test
    public void initDoesNothing() {
        final IWorkbench workbench = mock(IWorkbench.class);

        final FoldingPreferencePage page = new FoldingPreferencePage();
        page.init(workbench);

        verifyNoInteractions(workbench);
    }

    @Test
    public void thereAreBooleanEditorsForEachFoldableElementAndIntegerEditorForLinespan() throws Exception {
        final FoldingPreferencePage page = new FoldingPreferencePage();
        page.createControl(shell);

        final List<FieldEditor> editors = FieldEditorPreferencePageHelper.getEditors(page);
        assertThat(editors).hasSize(6);

        final Map<Class<?>, List<String>> namesGroupedByType = editors.stream()
                .collect(groupingBy(FieldEditor::getClass, mapping(FieldEditor::getPreferenceName, toList())));
        assertThat(namesGroupedByType).hasEntrySatisfying(BooleanFieldEditor.class,
                names -> assertThat(names).containsOnly(RedPreferences.FOLDABLE_SECTIONS, RedPreferences.FOLDABLE_CASES,
                        RedPreferences.FOLDABLE_TASKS, RedPreferences.FOLDABLE_KEYWORDS,
                        RedPreferences.FOLDABLE_DOCUMENTATION));
        assertThat(namesGroupedByType).hasEntrySatisfying(IntegerFieldEditor.class,
                names -> assertThat(names).containsOnly(RedPreferences.FOLDING_LINE_LIMIT));
    }
}
