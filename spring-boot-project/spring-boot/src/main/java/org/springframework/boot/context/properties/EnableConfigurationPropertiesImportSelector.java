/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EnableConfigurationPropertiesImportSelector是如何承担将外部配置属性值绑定到@ConfigurationProperties标注的类的属性中的。
 * <p>
 * Import selector that sets up binding of external properties to configuration classes
 * (see {@link ConfigurationProperties}). It either registers a
 * {@link ConfigurationProperties} bean or not, depending on whether the enclosing
 * {@link EnableConfigurationProperties} explicitly declares one. If none is declared then
 * a bean post processor will still kick in for any beans annotated as external
 * configuration. If one is declared then it a bean definition is registered with id equal
 * to the class name (thus an application context usually only contains one
 * {@link ConfigurationProperties} bean of each unique type).
 *
 * @author Dave Syer
 * @author Christian Dupuis
 * @author Stephane Nicoll
 */
class EnableConfigurationPropertiesImportSelector implements ImportSelector { // 该类实现了ImportSelector接口

    /**
     * 该IMPORTS数组就是需要向Spring注册的bean，返回的是全限定名
     */
    private static final String[] IMPORTS = {
            // 注册@EnableConfigurationProperties注解的value值的
            ConfigurationPropertiesBeanRegistrar.class.getName(),
            //
            ConfigurationPropertiesBindingPostProcessorRegistrar.class.getName()
    };

    /**
     * 实现ImportSelector接口的selectImports方法可以向容器中注册bean。
     *
     * @param metadata
     * @return
     */
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        // ConfigurationPropertiesBeanRegistrar + ConfigurationPropertiesBindingPostProcessorRegistrar
        // 这两个类会被注册到spring容器中
        return IMPORTS;
    }

    /**
     * 内部实现类 该静态内部类实现了ImportBeanDefinitionRegistrar，重写了registerBeanDefinitions方法
     * {@link ImportBeanDefinitionRegistrar} for configuration properties support.
     */
    public static class ConfigurationPropertiesBeanRegistrar
            implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata,
                                            BeanDefinitionRegistry registry) {
            // 1调用getTypes方法获取@EnableConfigurationProperties注解的属性值XxxProperties；
            // 2此时再遍历将XxxProperties逐个注册进Spring容器中，调用register方法将获取的属性值XxxProperties注册到Spring容器中，用于以后和外部属性绑定时使用。
            getTypes(metadata).forEach((type) -> register(registry,
                    (ConfigurableListableBeanFactory) registry, type));
        }

        /**
         * 获取@EnableConfigurationProperties这个注解所有的属性值
         * 比如@EnableConfigurationProperties(ServerProperties.class),那么得到的值是ServerProperties.class
         * 将属性值取出装进List集合并返回
         *
         * @param metadata
         * @return
         */
        private List<Class<?>> getTypes(AnnotationMetadata metadata) {
            MultiValueMap<String, Object> attributes = metadata
                    .getAllAnnotationAttributes(
                            EnableConfigurationProperties.class.getName(), false);
            return collectClasses((attributes != null) ? attributes.get("value")
                    : Collections.emptyList());
        }

        private List<Class<?>> collectClasses(List<?> values) {
            return values.stream().flatMap((value) -> Arrays.stream((Object[]) value))
                    .map((o) -> (Class<?>) o).filter((type) -> void.class != type)
                    .collect(Collectors.toList());
        }

        /**
         * 将注解中的XxxProperties注册到spring容器
         * 比如ServerProperties.class
         *
         * @param registry
         * @param beanFactory
         * @param type
         */
        private void register(BeanDefinitionRegistry registry,
                              ConfigurableListableBeanFactory beanFactory, Class<?> type) {
            // 得到type的名字，一般用类的全限定名作为bean name
            String name = getName(type);
            // 根据bean name判断beanFactory容器中是否包含该bean
            if (!containsBeanDefinition(beanFactory, name)) {
                // 若不包含，那么注册bean definition
                registerBeanDefinition(registry, name, type);
            }
        }

        private String getName(Class<?> type) {
            ConfigurationProperties annotation = AnnotationUtils.findAnnotation(type,
                    ConfigurationProperties.class);
            String prefix = (annotation != null) ? annotation.prefix() : "";
            return (StringUtils.hasText(prefix) ? prefix + "-" + type.getName()
                    : type.getName());
        }

        private boolean containsBeanDefinition(
                ConfigurableListableBeanFactory beanFactory, String name) {
            if (beanFactory.containsBeanDefinition(name)) {
                return true;
            }
            BeanFactory parent = beanFactory.getParentBeanFactory();
            if (parent instanceof ConfigurableListableBeanFactory) {
                return containsBeanDefinition((ConfigurableListableBeanFactory) parent,
                        name);
            }
            return false;
        }

        private void registerBeanDefinition(BeanDefinitionRegistry registry, String name,
                                            Class<?> type) {
            assertHasAnnotation(type);
            GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClass(type);
            registry.registerBeanDefinition(name, definition);
        }

        private void assertHasAnnotation(Class<?> type) {
            Assert.notNull(
                    AnnotationUtils.findAnnotation(type, ConfigurationProperties.class),
                    () -> "No " + ConfigurationProperties.class.getSimpleName()
                            + " annotation found on  '" + type.getName() + "'.");
        }

    }

}
