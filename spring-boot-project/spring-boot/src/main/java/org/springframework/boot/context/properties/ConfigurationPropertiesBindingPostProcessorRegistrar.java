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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 该类的作用是 注册：属性绑定的两个后置处理器
 * 主要用来注册外部配置属性绑定相关的后置处理器即ConfigurationBeanFactoryMetadata和ConfigurationPropertiesBindingPostProcessor。
 * <p>
 * {@link ImportBeanDefinitionRegistrar} for binding externalized application properties
 * to {@link ConfigurationProperties} beans.
 *
 * @author Dave Syer
 * @author Phillip Webb
 */
public class ConfigurationPropertiesBindingPostProcessorRegistrar
        implements ImportBeanDefinitionRegistrar { // ImportBeanDefinitionRegistrar

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        // 若容器中没有注册ConfigurationPropertiesBindingPostProcessor这个处理属性绑定的后置处理器，
        if (!registry.containsBeanDefinition(
                ConfigurationPropertiesBindingPostProcessor.BEAN_NAME)) {
            // 那么将注册ConfigurationPropertiesBindingPostProcessor bean
            registerConfigurationPropertiesBindingPostProcessor(registry);
            // 注册ConfigurationBeanFactoryMetadata bean
            registerConfigurationBeanFactoryMetadata(registry);
        }
    }

    /**
     * bean1注册 注册ConfigurationPropertiesBindingPostProcessor后置处理器
     *
     * @param registry
     */
    private void registerConfigurationPropertiesBindingPostProcessor(
            BeanDefinitionRegistry registry) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ConfigurationPropertiesBindingPostProcessor.class);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(
                ConfigurationPropertiesBindingPostProcessor.BEAN_NAME, definition);

    }

    /**
     * bean2注册 注册ConfigurationBeanFactoryMetadata后置处理器
     *
     * @param registry
     */
    private void registerConfigurationBeanFactoryMetadata(
            BeanDefinitionRegistry registry) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ConfigurationBeanFactoryMetadata.class);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(ConfigurationBeanFactoryMetadata.BEAN_NAME,
                definition);
    }

}
