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

package org.springframework.boot.web.embedded.tomcat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Set;

/**
 * TomcatStarter实现了ServletContainerInitializer，
 * 重写的onstartup方法，这里会启动ServletContextInitializer的onStartup方法.
 * <p>
 * 内嵌的tomcat不会以spi方式加载ServletContainerInitializer，
 * 而是用TomcatStarter的onStartup，间接启动ServletContextInitializers，来达到ServletContainerInitializer的效果。
 * <p>
 * 类似SpringServletContainerInitializer老大哥带WebApplicationInitializer的套路，
 * <p>
 * {@link ServletContainerInitializer} used to trigger {@link ServletContextInitializer
 * ServletContextInitializers} and track startup errors.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class TomcatStarter implements ServletContainerInitializer {

    private static final Log logger = LogFactory.getLog(TomcatStarter.class);

    private final ServletContextInitializer[] initializers;

    private volatile Exception startUpException;

    TomcatStarter(ServletContextInitializer[] initializers) {
        this.initializers = initializers;
    }

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext)
            throws ServletException {
        try {
            /**
             * 内嵌tomcat如何启动ServletContextInitializer的onStartUp方法
             */
            for (ServletContextInitializer initializer : this.initializers) {
                initializer.onStartup(servletContext);
            }
        } catch (Exception ex) {
            this.startUpException = ex;
            // Prevent Tomcat from logging and re-throwing when we know we can
            // deal with it in the main thread, but log for information here.
            if (logger.isErrorEnabled()) {
                logger.error("Error starting Tomcat context. Exception: "
                        + ex.getClass().getName() + ". Message: " + ex.getMessage());
            }
        }
    }

    public Exception getStartUpException() {
        return this.startUpException;
    }

}
