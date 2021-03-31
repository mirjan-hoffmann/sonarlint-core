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

import java.util.function.Consumer;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.scanner.fs.ProjectFileWalker;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileWalker;

public class SonarLintFileWalker implements ProjectFileWalker {

  private final ClientFileWalker clientFileWalker;
  private final ModuleInputFileBuilder inputFileBuilder;

  public SonarLintFileWalker(ClientFileWalker clientFileWalker, ModuleInputFileBuilder inputFileBuilder) {
    this.clientFileWalker = clientFileWalker;
    this.inputFileBuilder = inputFileBuilder;
  }

  @Override
  public void walk(String language, InputFile.Type type, Consumer<InputFile> consumer) {
    clientFileWalker.walk(language, type, clientInputFile -> consumer.accept(inputFileBuilder.create(clientInputFile)));
  }
}
