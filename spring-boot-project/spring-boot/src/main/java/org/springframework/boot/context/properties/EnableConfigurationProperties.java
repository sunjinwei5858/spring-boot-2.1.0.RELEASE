/*
 * Copyright 2012-2017 the original author or authors.
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 该注解的主要作用就是为@ConfigurationProperties注解标注的类提供支持
 * <p>
 * Enable support for {@link ConfigurationProperties} annotated beans.
 * {@link ConfigurationProperties} beans can be registered in the standard way (for
 * example using {@link Bean @Bean} methods) or, for convenience, can be specified
 * directly on this annotation.
 *
 * @author Dave Syer
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EnableConfigurationPropertiesImportSelector.class) // 这个注解是如何对属性进行绑定的呢 关键就是EnableConfigurationPropertiesImportSelector
public @interface EnableConfigurationProperties {

    /**
     * 这个值制定的类就是 @ConfigurationProperties 标注的类，其将会被注册到spring容器当中
     * Convenient way to quickly register {@link ConfigurationProperties} annotated beans
     * with Spring. Standard Spring Beans will also be scanned regardless of this value.
     *
     * @return {@link ConfigurationProperties} annotated beans to register
     */
    Class<?>[] value() default {};

}
