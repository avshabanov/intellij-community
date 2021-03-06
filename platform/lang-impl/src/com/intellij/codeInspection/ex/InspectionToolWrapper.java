/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.CustomSuppressableInspectionTool;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;

/**
 * @author Dmitry Avdeev
 *         Date: 9/28/11
 */
public abstract class InspectionToolWrapper<T extends InspectionProfileEntry, E extends InspectionEP> extends DescriptorProviderInspection {
  protected T myTool;
  protected final E myEP;

  protected InspectionToolWrapper(E ep) {
    myTool = null;
    myEP = ep;
  }

  protected InspectionToolWrapper(T tool) {
    myTool = tool;
    myEP = null;
  }

  protected InspectionToolWrapper(@Nullable T tool, @Nullable E ep) {
    myEP = ep;
    myTool = tool;
  }

  /** Copy ctor */
  protected InspectionToolWrapper(InspectionToolWrapper<T, E> other) {
    myEP = other.myEP;
    //noinspection unchecked
    myTool = other.myTool == null ? null : (T)InspectionToolRegistrar.instantiateTool(other.myTool.getClass());
  }

  public abstract InspectionToolWrapper<T, E> createCopy();

  @NotNull
  public T getTool() {
    if (myTool == null) {
      //noinspection unchecked
      myTool = (T)myEP.instantiateTool();
      LOG.assertTrue(myTool.getShortName().equals(myEP.shortName), "myTool: " + myTool.getShortName() + "; ep.shortName " + myEP.shortName);
    }
    return myTool;
  }

  public boolean isInitialized() {
    return myTool != null;
  }

  @Nullable
  public String getLanguage() {
    return myEP == null ? null : myEP.language;
  }

  public boolean applyToDialects() {
    return myEP != null && myEP.applyToDialects;
  }

  @NotNull
  public String getShortName() {
    return myEP == null ? getTool().getShortName() : myEP.shortName;
  }

  @NotNull
  public String getDisplayName() {
    if (myEP == null) {
      return getTool().getDisplayName();
    }
    else {
      String name = myEP.getDisplayName();
      return name == null ? getTool().getDisplayName() : name;
    }
  }

  @NotNull
  public String getGroupDisplayName() {
    if (myEP == null) {
      return getTool().getGroupDisplayName();
    }
    else {
      String groupDisplayName = myEP.getGroupDisplayName();
      return groupDisplayName == null ? getTool().getGroupDisplayName() : groupDisplayName;
    }
  }

  public boolean isEnabledByDefault() {
    return myEP == null ? getTool().isEnabledByDefault() : myEP.enabledByDefault;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return myEP == null ? getTool().getDefaultLevel() : myEP.getDefaultLevel();
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    if (myEP == null) {
      return getTool().getGroupPath();
    }
    else {
      String[] path = myEP.getGroupPath();
      return path == null ? getTool().getGroupPath() : path;
    }
  }

  public void readSettings(Element element) throws InvalidDataException {
    getTool().readSettings(element);
  }

  public void writeSettings(Element element) throws WriteExternalException {
    getTool().writeSettings(element);
  }

  public JComponent createOptionsPanel() {
    return getTool().createOptionsPanel();
  }

  public void projectOpened(Project project) {
    if (myEP == null) {
      getTool().projectOpened(project);
    }
  }

  public void projectClosed(Project project) {
    if (myEP == null) {
      getTool().projectClosed(project);
    }
  }

  @Nullable
  public String getStaticDescription() {
    return myEP == null ? getTool().getStaticDescription() : null;
  }

  @Override
  protected URL getDescriptionUrl() {
    if (myEP == null || ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManager.getApplication().isHeadlessEnvironment()) return super.getDescriptionUrl();
    String fileName = getDescriptionFileName();
    if (fileName == null) return null;
    return myEP.getLoaderForClass().getResource("/inspectionDescriptions/" + fileName);
  }

  @Nullable
  public SuppressIntentionAction[] getSuppressActions() {
    if (getTool() instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)getTool()).getSuppressActions(null);
    }
    return super.getSuppressActions();
  }

  public Class<? extends InspectionProfileEntry> getDescriptionContextClass() {
    return getTool().getClass();
  }

  @Override
  public String toString() {
    return getShortName();
  }

  @Override
  public String getMainToolId() {
    return getTool().getMainToolId();
  }
}
