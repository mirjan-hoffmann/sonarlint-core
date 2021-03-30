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
package org.sonarsource.sonarlint.core.container.global;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;

public class ExtensionInstaller {

  private static final Logger LOG = Loggers.get(ExtensionInstaller.class);

  private final SonarRuntime sonarRuntime;
  private final PluginRepository pluginRepository;
  private final Configuration bootConfiguration;
  private final PluginVersionChecker pluginVersionChecker;
  private final Set<Language> enabledLanguages;

  public ExtensionInstaller(SonarRuntime sonarRuntime, PluginRepository pluginRepository, Configuration bootConfiguration, PluginVersionChecker pluginVersionChecker,
                            AbstractGlobalConfiguration globalConfig) {
    this.sonarRuntime = sonarRuntime;
    this.pluginRepository = pluginRepository;
    this.bootConfiguration = bootConfiguration;
    this.pluginVersionChecker = pluginVersionChecker;
    this.enabledLanguages = globalConfig.getEnabledLanguages();
  }

  public ExtensionInstaller installEmbeddedOnly(ComponentContainer container, SonarLintSide.Scope scope) {
    Collection<PluginInfo> pluginInfos = pluginRepository.getActivePluginInfos().stream().filter(PluginInfo::isEmbedded).collect(Collectors.toList());
    return install(container, scope, pluginInfos);
  }

  public ExtensionInstaller install(ComponentContainer container, SonarLintSide.Scope scope) {
    return install(container, scope, pluginRepository.getActivePluginInfos());
  }

  private ExtensionInstaller install(ComponentContainer container, SonarLintSide.Scope scope, Collection<PluginInfo> pluginInfos) {
    for (PluginInfo pluginInfo : pluginInfos) {
      Plugin plugin = pluginRepository.getPluginInstance(pluginInfo.getKey());
      Plugin.Context context = new PluginContextImpl.Builder()
        .setSonarRuntime(sonarRuntime)
        .setBootConfiguration(bootConfiguration)
        .build();
      plugin.define(context);
      loadExtensions(container, pluginInfo, context, scope);
    }
    return this;
  }

  private void loadExtensions(ComponentContainer container, PluginInfo pluginInfo, Plugin.Context context, SonarLintSide.Scope scope) {
    Boolean isSlPluginOrNull = pluginInfo.isSonarLintSupported();
    boolean isExplicitlySonarLintCompatible = isSlPluginOrNull != null && isSlPluginOrNull.booleanValue();
    if (scope == SonarLintSide.Scope.INSTANCE && !isExplicitlySonarLintCompatible) {
      // Don't support global extensions for old plugins
      return;
    }
    for (Object extension : context.getExtensions()) {
      if (isExplicitlySonarLintCompatible) {
        // When plugin itself claim to be compatible with SonarLint, only load @SonarLintSide extensions
        // filter out non officially supported Sensors
        if (isSonarLintSide(extension) && (getScope(extension) == scope) && onlySonarSourceSensor(pluginInfo, extension)) {
          container.addExtension(pluginInfo, extension);
        }
      } else {
        LOG.debug("Extension {} was blacklisted as it is not used by SonarLint", className(extension));
      }
    }
  }

  private boolean onlySonarSourceSensor(PluginInfo pluginInfo, Object extension) {
    // SLCORE-259
    if (!enabledLanguages.contains(Language.TS) && className(extension).contains("TypeScriptSensor")) {
      LOG.debug("TypeScript sensor excluded");
      return false;
    }
    return pluginVersionChecker.getMinimumVersion(pluginInfo.getKey()) != null || isNotSensor(extension);
  }

  private static boolean isSonarLintSide(Object extension) {
    return ExtensionUtils.isSonarLintSide(extension);
  }

  /**
   * Experimental. Used by SonarTS and SonarPython
   */
  private static SonarLintSide.Scope getScope(Object extension) {
    SonarLintSide annotation = AnnotationUtils.getAnnotation(extension, SonarLintSide.class);
    if (annotation == null) {
      return null;
    }
    if (SonarLintSide.MULTIPLE_ANALYSES.equals(annotation.lifespan())) {
      return SonarLintSide.Scope.INSTANCE;
    }
    return annotation.scope();
  }

  private static boolean isNotSensor(Object extension) {
    return !ExtensionUtils.isType(extension, Sensor.class);
  }

  private static String className(Object extension) {
    return extension instanceof Class ? ((Class) extension).getName() : extension.getClass().getName();
  }
}
