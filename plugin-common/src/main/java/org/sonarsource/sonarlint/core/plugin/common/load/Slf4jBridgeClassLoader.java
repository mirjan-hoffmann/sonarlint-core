/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common.load;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class Slf4jBridgeClassLoader extends URLClassLoader {

  public Slf4jBridgeClassLoader(ClassLoader parent) {
    super(new URL[0], parent);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    if (name.startsWith("org.slf4j")) {
      String path = name.replace('.', '/').concat(".class");
      try (InputStream is = getParent().getResourceAsStream("/slf4j-sonar-log/" + path)) {
        byte[] classBytes = is.readAllBytes();
        return defineClass(name, classBytes, 0, classBytes.length);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to load class " + name, e);
      }
    }
    return super.findClass(name);
  }

}
