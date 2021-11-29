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
package org.sonarsource.sonarlint.core.plugin.common.pico;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.Property;
import org.sonar.api.config.PropertyDefinitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ComponentContainerTests {

  @Test
  void shouldRegisterItself() {
    ComponentContainer container = new ComponentContainer();
    assertThat(container.getComponentByType(ComponentContainer.class)).isSameAs(container);
  }

  @Test
  void should_start_and_stop() {
    ComponentContainer container = spy(new ComponentContainer());
    container.addSingleton(StartableComponent.class);
    container.startComponents();

    assertThat(container.getComponentByType(StartableComponent.class).started).isTrue();
    assertThat(container.getComponentByType(StartableComponent.class).stopped).isFalse();
    verify(container).doBeforeStart();
    verify(container).doAfterStart();

    container.stopComponents();
    assertThat(container.getComponentByType(StartableComponent.class).stopped).isTrue();
  }

  @Test
  void should_start_and_stop_hierarchy_of_containers() {
    StartableComponent parentComponent = new StartableComponent();
    final StartableComponent childComponent = new StartableComponent();
    ComponentContainer parentContainer = new ComponentContainer() {
      @Override
      public void doAfterStart() {
        ComponentContainer childContainer = new ComponentContainer(this);
        childContainer.add(childComponent);
        childContainer.execute();
      }
    };
    parentContainer.add(parentComponent);
    parentContainer.execute();
    assertThat(parentComponent.started).isTrue();
    assertThat(parentComponent.stopped).isTrue();
    assertThat(childComponent.started).isTrue();
    assertThat(childComponent.stopped).isTrue();
  }

  @Test
  void should_stop_hierarchy_of_containers_on_failure() {
    StartableComponent parentComponent = new StartableComponent();
    final StartableComponent childComponent1 = new StartableComponent();
    final UnstartableComponent childComponent2 = new UnstartableComponent();
    ComponentContainer parentContainer = new ComponentContainer() {
      @Override
      public void doAfterStart() {
        ComponentContainer childContainer = new ComponentContainer(this);
        childContainer.add(childComponent1);
        childContainer.add(childComponent2);
        childContainer.execute();
      }
    };
    parentContainer.add(parentComponent);
    try {
      parentContainer.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(parentComponent.started).isTrue();
      assertThat(parentComponent.stopped).isTrue();
      assertThat(childComponent1.started).isTrue();
      assertThat(childComponent1.stopped).isTrue();
    }
  }

  @Test
  void testChild() {
    ComponentContainer parent = new ComponentContainer();
    parent.startComponents();

    ComponentContainer child = parent.createChild();
    child.addSingleton(StartableComponent.class);
    child.startComponents();

    assertThat(child.getParent()).isSameAs(parent);
    assertThat(child.getComponentByType(ComponentContainer.class)).isSameAs(child);
    assertThat(parent.getComponentByType(ComponentContainer.class)).isSameAs(parent);
    assertThat(child.getComponentByType(StartableComponent.class)).isNotNull();
    assertThat(parent.getComponentByType(StartableComponent.class)).isNull();

    parent.stopComponents();
  }

  @Test
  void shouldForwardStartAndStopToDescendants() {
    ComponentContainer grandParent = new ComponentContainer();
    ComponentContainer parent = grandParent.createChild();
    ComponentContainer child = parent.createChild();
    child.addSingleton(StartableComponent.class);

    grandParent.startComponents();

    StartableComponent component = child.getComponentByType(StartableComponent.class);
    assertThat(component.started).isTrue();

    parent.stopComponents();
    assertThat(component.stopped).isTrue();
  }

  @Test
  void shouldDeclareComponentProperties() {
    ComponentContainer container = new ComponentContainer();
    container.addSingleton(ComponentWithProperty.class);

    PropertyDefinitions propertyDefinitions = container.getComponentByType(PropertyDefinitions.class);
    assertThat(propertyDefinitions.get("foo")).isNotNull();
    assertThat(propertyDefinitions.get("foo").defaultValue()).isEqualTo("bar");
  }

  @Test
  void shouldDeclareExtensionWithoutAddingIt() {
    ComponentContainer container = new ComponentContainer();
    container.declareProperties(ComponentWithProperty.class);

    PropertyDefinitions propertyDefinitions = container.getComponentByType(PropertyDefinitions.class);
    assertThat(propertyDefinitions.get("foo")).isNotNull();
    assertThat(container.getComponentByType(ComponentWithProperty.class)).isNull();
  }

  @Test
  void shouldDeclareExtensionWhenAdding() {
    ComponentContainer container = new ComponentContainer();
    container.addExtension("pluginKey", ComponentWithProperty.class);

    PropertyDefinitions propertyDefinitions = container.getComponentByType(PropertyDefinitions.class);
    assertThat(propertyDefinitions.get("foo")).isNotNull();
    assertThat(container.getComponentByType(ComponentWithProperty.class)).isNotNull();
    assertThat(container.getComponentByKey(ComponentWithProperty.class)).isNotNull();
  }

  @Test
  void test_add_class() {
    ComponentContainer container = new ComponentContainer();
    container.add(ComponentWithProperty.class, SimpleComponent.class);
    assertThat(container.getComponentByType(ComponentWithProperty.class)).isNotNull();
    assertThat(container.getComponentByType(SimpleComponent.class)).isNotNull();
  }

  @Test
  void test_add_collection() {
    ComponentContainer container = new ComponentContainer();
    container.add(Arrays.asList(ComponentWithProperty.class, SimpleComponent.class));
    assertThat(container.getComponentByType(ComponentWithProperty.class)).isNotNull();
    assertThat(container.getComponentByType(SimpleComponent.class)).isNotNull();
  }

  @Test
  void test_add_adapter() {
    ComponentContainer container = new ComponentContainer();
    container.add(new SimpleComponentProvider());
    assertThat(container.getComponentByType(SimpleComponent.class)).isNotNull();
  }

  @Test
  void should_sanitize_pico_exception_on_start_failure() {
    ComponentContainer container = new ComponentContainer();
    container.add(UnstartableComponent.class);

    // do not expect a PicoException
    assertThrows(IllegalStateException.class, () -> container.startComponents());
  }

  @Test
  void display_plugin_name_when_failing_to_add_extension() {
    ComponentContainer container = new ComponentContainer();

    container.startComponents();

    IllegalStateException expected = assertThrows(IllegalStateException.class, () -> container.addExtension("myPlugin", UnstartableComponent.class));
    assertThat(expected.getMessage())
      .isEqualTo(
        "Unable to register extension org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainerTests$UnstartableComponent from plugin 'myPlugin'");
  }

  @Test
  void test_start_failure() {
    ComponentContainer container = new ComponentContainer();
    StartableComponent startable = new StartableComponent();
    container.add(startable, UnstartableComponent.class);

    try {
      container.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(startable.started).isTrue();

      // container stops the components that have already been started
      assertThat(startable.stopped).isTrue();
    }
  }

  @Test
  void test_stop_failure() {
    ComponentContainer container = new ComponentContainer();
    StartableComponent startable = new StartableComponent();
    container.add(startable, UnstoppableComponent.class);

    try {
      container.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(startable.started).isTrue();

      // container should stop the components that have already been started
      // ... but that's not the case
    }
  }

  @Test
  void stop_exception_should_not_hide_start_exception() {
    ComponentContainer container = new ComponentContainer();
    container.add(UnstartableComponent.class, UnstoppableComponent.class);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> container.execute());
    assertThat(ex).hasRootCauseMessage("Fail to start");
  }

  @Test
  void should_execute_components() {
    ComponentContainer container = new ComponentContainer();
    StartableComponent component = new StartableComponent();
    container.add(component);

    container.execute();

    assertThat(component.started).isTrue();
    assertThat(component.stopped).isTrue();
  }

  /**
   * Method close() must be called even if the methods start() or stop()
   * are not defined.
   */
  @Test
  void should_close_components_without_lifecycle() {
    ComponentContainer container = new ComponentContainer();
    CloseableComponent component = new CloseableComponent();
    container.add(component);

    container.execute();

    assertThat(component.isClosed).isTrue();
  }

  /**
   * Method close() must be executed after stop()
   */
  @Test
  void should_close_components_with_lifecycle() {
    ComponentContainer container = new ComponentContainer();
    StartableCloseableComponent component = new StartableCloseableComponent();
    container.add(component);

    container.execute();

    assertThat(component.isStopped).isTrue();
    assertThat(component.isClosed).isTrue();
    assertThat(component.isClosedAfterStop).isTrue();
  }

  public static class StartableComponent {
    public boolean started = false;
    public boolean stopped = false;

    public void start() {
      started = true;
    }

    public void stop() {
      stopped = true;
    }
  }

  public static class UnstartableComponent {
    public void start() {
      throw new IllegalStateException("Fail to start");
    }

    public void stop() {

    }
  }

  public static class UnstoppableComponent {
    public void start() {
    }

    public void stop() {
      throw new IllegalStateException("Fail to stop");
    }
  }

  @Property(key = "foo", defaultValue = "bar", name = "Foo")
  public static class ComponentWithProperty {

  }

  public static class SimpleComponent {

  }

  public static class SimpleComponentProvider extends ProviderAdapter {
    public SimpleComponent provide() {
      return new SimpleComponent();
    }
  }

  public static class CloseableComponent implements AutoCloseable {
    public boolean isClosed = false;

    @Override
    public void close() throws Exception {
      isClosed = true;
    }
  }

  public static class StartableCloseableComponent implements AutoCloseable {
    public boolean isClosed = false;
    public boolean isStopped = false;
    public boolean isClosedAfterStop = false;

    public void stop() {
      isStopped = true;
    }

    @Override
    public void close() throws Exception {
      isClosed = true;
      isClosedAfterStop = isStopped;
    }
  }
}
