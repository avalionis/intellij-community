/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.List;

/**
 * Base class that simplifies external system settings management.
 * <p/>
 * The general idea is to provide a control which looks like below:
 * <pre>
 *    ----------------------------------------------
 *   |   linked external projects list              |
 *   |----------------------------------------------
 *   |   linked project-specific settings           |
 *   |----------------------------------------------
 *   |   external system-wide settings (optional)   |
      ----------------------------------------------
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 4/30/13 12:50 PM
 */
public abstract class AbstractExternalSystemConfigurable<
  ProjectSettings extends ExternalProjectSettings,
  L extends ExternalSystemSettingsListener<ProjectSettings>,
  SystemSettings extends AbstractExternalSystemSettings<ProjectSettings, L>
  > implements SearchableConfigurable, Configurable.NoScroll
{

  @NotNull private final List<ExternalSettingsControl<ProjectSettings>> myProjectSettingsControls = ContainerUtilRt.newArrayList();

  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final Project         myProject;

  @Nullable private ExternalSettingsControl<SystemSettings> mySystemSettingsControl;
  @Nullable private ExternalSettingsControl<ProjectSettings> myActiveProjectSettingsControl;

  private PaintAwarePanel  myComponent;
  private JBList           myProjectsList;
  private DefaultListModel myProjectsModel;

  protected AbstractExternalSystemConfigurable(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    myProject = project;
    myExternalSystemId = externalSystemId;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return ExternalSystemApiUtil.toReadableName(myExternalSystemId);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new PaintAwarePanel(new GridBagLayout());
      SystemSettings settings = getSettings();
      prepareProjectSettings(settings);
      prepareSystemSettings(settings);
      ExternalSystemUiUtil.fillBottom(myComponent);
    }
    return myComponent;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private SystemSettings getSettings() {
    ExternalSystemManager<ProjectSettings, L, SystemSettings, ?, ?> manager
      = (ExternalSystemManager<ProjectSettings, L, SystemSettings, ?, ?>)ExternalSystemApiUtil.getManager(myExternalSystemId);
    assert manager != null;
    return manager.getSettingsProvider().fun(myProject);
  }

  @SuppressWarnings("unchecked")
  private void prepareProjectSettings(@NotNull SystemSettings s) {
    myProjectsModel = new DefaultListModel();
    myProjectsList = new JBList(myProjectsModel);
    myProjectsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    String externalSystemName = ExternalSystemApiUtil.toReadableName(myExternalSystemId);
    addTitle(ExternalSystemBundle.message("settings.title.linked.projects", externalSystemName));
    myComponent.add(new JBScrollPane(myProjectsList), ExternalSystemUiUtil.getFillLineConstraints(1));
    
    addTitle(ExternalSystemBundle.message("settings.title.project.settings"));
    List<ProjectSettings> settings = ContainerUtilRt.newArrayList(s.getLinkedProjectsSettings());
    myProjectsList.setVisibleRowCount(Math.max(3, Math.min(5, settings.size())));
    ContainerUtil.sort(settings, new Comparator<ProjectSettings>() {
      @Override
      public int compare(ProjectSettings s1, ProjectSettings s2) {
        return getProjectName(s1.getExternalProjectPath()).compareTo(getProjectName(s2.getExternalProjectPath()));
      }
    });

    myProjectSettingsControls.clear();
    for (ProjectSettings setting : settings) {
      ExternalSettingsControl<ProjectSettings> control = createProjectSettingsControl(setting);
      control.fillUi(myComponent, 1);
      myProjectsModel.addElement(getProjectName(setting.getExternalProjectPath()));
      myProjectSettingsControls.add(control);
      control.showUi(false);
    }

    myProjectsList.addListSelectionListener(new ListSelectionListener() {
      @SuppressWarnings("unchecked")
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        int i = myProjectsList.getSelectedIndex();
        if (i < 0) {
          return;
        }
        if (myActiveProjectSettingsControl != null) {
          myActiveProjectSettingsControl.showUi(false);
        }
        myActiveProjectSettingsControl = myProjectSettingsControls.get(i);
        myActiveProjectSettingsControl.showUi(true);
      }
    });

    
    if (!myProjectsModel.isEmpty()) {
      addTitle(ExternalSystemBundle.message("settings.title.system.settings", externalSystemName));
      myProjectsList.setSelectedIndex(0);
    }
  }
  
  private void addTitle(@NotNull String title) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(title, false, new Insets(ExternalSystemUiUtil.INSETS, 0, 0, 0)));
    myComponent.add(panel, ExternalSystemUiUtil.getFillLineConstraints(0));
  }

  /**
   * Creates a control for managing given project settings.
   * 
   * @param settings  target external project settings
   * @return          control for managing given project settings
   */
  @NotNull
  protected abstract ExternalSettingsControl<ProjectSettings> createProjectSettingsControl(@NotNull ProjectSettings settings);
  
  @NotNull
  protected String getProjectName(@NotNull String path) {
    return new File(path).getParentFile().getName();
  }

  private void prepareSystemSettings(@NotNull SystemSettings s) {
    mySystemSettingsControl = createSystemSettingsControl(s);
    if (mySystemSettingsControl != null) {
      mySystemSettingsControl.fillUi(myComponent, 1);
    }
  }

  /**
   * Creates a control for managing given system-level settings (if any).
   * 
   * @param settings  target system settings
   * @return          a control for managing given system-level settings;
   *                  <code>null</code> if current external system doesn't have system-level settings (only project-level settings)
   */
  @Nullable
  protected abstract ExternalSettingsControl<SystemSettings> createSystemSettingsControl(@NotNull SystemSettings settings); 

  @Override
  public boolean isModified() {
    for (ExternalSettingsControl<ProjectSettings> control : myProjectSettingsControls) {
      if (control.isModified()) {
        return true;
      }
    }
    return mySystemSettingsControl != null && mySystemSettingsControl.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    SystemSettings systemSettings = getSettings();
    L publisher = systemSettings.getPublisher();
    publisher.onBulkChangeStart();
    try {
      List<ProjectSettings> projectSettings = ContainerUtilRt.newArrayList();
      for (ExternalSettingsControl<ProjectSettings> control : myProjectSettingsControls) {
        ProjectSettings s = newProjectSettings();
        control.apply(s);
        projectSettings.add(s);
      }
      systemSettings.setLinkedProjectsSettings(projectSettings);
      if (mySystemSettingsControl != null) {
        mySystemSettingsControl.apply(systemSettings);
      }
    }
    finally {
      publisher.onBulkChangeEnd();
    }
  }

  /**
   * @return    new empty project-level settings object
   */
  @NotNull
  protected abstract ProjectSettings newProjectSettings();

  @Override
  public void reset() {
    for (ExternalSettingsControl<ProjectSettings> control : myProjectSettingsControls) {
      control.reset();
    }
    if (mySystemSettingsControl != null) {
      mySystemSettingsControl.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    for (ExternalSettingsControl<ProjectSettings> control : myProjectSettingsControls) {
      control.disposeUIResources();
    }
    myProjectSettingsControls.clear();
    myComponent = null;
    myProjectsList = null;
    myProjectsModel = null;
    mySystemSettingsControl = null;
  }
}
