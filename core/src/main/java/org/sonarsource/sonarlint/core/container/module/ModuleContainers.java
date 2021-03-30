/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.container.module;

import java.util.HashMap;
import java.util.Map;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileWalker;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.ModulesProvider;
import org.sonarsource.sonarlint.core.container.ComponentContainer;

public class ModuleContainers {
  private static final Logger LOG = Loggers.get(ModuleContainers.class);

  private final Map<Object, ModuleContainer> modules = new HashMap<>();
  private final ComponentContainer parent;

  public ModuleContainers(ComponentContainer parent, ModulesProvider<?> modulesProvider) {
    this.parent = parent;
    if (modulesProvider != null) {
      modulesProvider.getModules().forEach(this::createContainer);
    }
  }

  public void createContainer(ModuleInfo module) {
    if (modules.containsKey(module.key())) {
      // can this happen ?
      LOG.info("Module container already started with key=" + module.key());
    }
    LOG.info("Creating container for module with key=" + module.key());
    ModuleContainer moduleContainer = new ModuleContainer(parent);
    ClientFileWalker clientFileWalker = module.fileWalker();
    if (clientFileWalker != null) {
      moduleContainer.add(clientFileWalker);
    }
    moduleContainer.startComponents();
    modules.put(module.key(), moduleContainer);
  }

  public void stopContainer(ModuleInfo module) {
    if (!modules.containsKey(module.key())) {
      // can this happen ?
      return;
    }
    ModuleContainer moduleContainer = modules.remove(module.key());
    moduleContainer.stopComponents();
  }

  public void stopAll() {
    modules.values().forEach(ComponentContainer::stopComponents);
    modules.clear();
  }

  public ComponentContainer getContainerFor(Object moduleKey) {
    ModuleContainer moduleContainer = modules.get(moduleKey);
    if (moduleContainer == null) {
      // XXX can we have no module at all ?
      return parent;
    }
    return moduleContainer;
  }
}
