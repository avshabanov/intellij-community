/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class RenameLibraryHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.actions.RenameModuleHandler");

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Library library = LangDataKeys.LIBRARY.getData(dataContext);
    return library != null;
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.assertTrue(false);
  }

  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, @NotNull DataContext dataContext) {
    final Library library = LangDataKeys.LIBRARY.getData(dataContext);
    LOG.assertTrue(library != null);
    Messages.showInputDialog(project,
                             IdeBundle.message("prompt.enter.new.library.name"),
                             IdeBundle.message("title.rename.library"),
                             Messages.getQuestionIcon(),
                             library.getName(),
                             new MyInputValidator(project, library));
  }

  public String getActionTitle() {
    return IdeBundle.message("title.rename.library");
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final Library myLibrary;
    public MyInputValidator(Project project, Library library) {
      myProject = project;
      myLibrary = library;
    }

    public boolean checkInput(String inputString) {
      return inputString != null && inputString.length() > 0 && myLibrary.getTable().getLibraryByName(inputString) == null;
    }

    public boolean canClose(final String inputString) {
      final String oldName = myLibrary.getName();
      final Library.ModifiableModel modifiableModel = renameLibrary(inputString);
      if (modifiableModel == null) return false;
      final Ref<Boolean> success = Ref.create(Boolean.TRUE);
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          UndoableAction action = new BasicUndoableAction() {
            public void undo() throws UnexpectedUndoException {
              final Library.ModifiableModel modifiableModel = renameLibrary(oldName);
              if (modifiableModel != null) {
                modifiableModel.commit();
              }
            }

            @Override
            public void redo() throws UnexpectedUndoException {
              final Library.ModifiableModel modifiableModel = renameLibrary(inputString);
              if (modifiableModel != null) {
                modifiableModel.commit();
              }
            }
          };
          UndoManager.getInstance(myProject).undoableActionPerformed(action);
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              modifiableModel.commit();
            }
          });
        }
      }, IdeBundle.message("command.renaming.module", oldName), null);
      return success.get().booleanValue();
    }

    @Nullable
    private Library.ModifiableModel renameLibrary(String inputString) {
      final Library.ModifiableModel modifiableModel = myLibrary.getModifiableModel();
      modifiableModel.setName(inputString);
      return modifiableModel;
    }
  }

}
