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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.plugin.api.ConfigurationMonitor;

public class GlobalConfigurationMonitor implements ConfigurationMonitor {
  private final Map<String, List<Consumer<String>>> monitoredKeysByConsumer = new HashMap<>();

  @Override
  public void register(String propertyKey, Consumer<String> propertyValueConsumer) {
    List<Consumer<String>> consumers = monitoredKeysByConsumer.getOrDefault(propertyKey, new ArrayList<>());
    consumers.add(propertyValueConsumer);
    monitoredKeysByConsumer.put(propertyKey, consumers);
  }

  public void notify(String propertyKey, String newValue) {
    if (monitoredKeysByConsumer.containsKey(propertyKey)) {
      monitoredKeysByConsumer.get(propertyKey).forEach(stringConsumer -> stringConsumer.accept(newValue));
    }
    // should also notify every m
  }
}
