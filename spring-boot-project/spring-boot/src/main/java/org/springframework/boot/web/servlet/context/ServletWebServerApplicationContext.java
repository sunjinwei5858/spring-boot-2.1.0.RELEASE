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

package org.springframework.boot.web.servlet.context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.support.ServletContextAwareProcessor;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.*;
import java.util.*;

/**
 * A {@link WebApplicationContext} that can be used to bootstrap itself from a contained
 * {@link ServletWebServerFactory} bean.
 * <p>
 * This context will create, initialize and run an {@link WebServer} by searching for a
 * single {@link ServletWebServerFactory} bean within the {@link ApplicationContext}
 * itself. The {@link ServletWebServerFactory} is free to use standard Spring concepts
 * (such as dependency injection, lifecycle callbacks and property placeholder variables).
 * <p>
 * In addition, any {@link Servlet} or {@link Filter} beans defined in the context will be
 * automatically registered with the web server. In the case of a single Servlet bean, the
 * '/' mapping will be used. If multiple Servlet beans are found then the lowercase bean
 * name will be used as a mapping prefix. Any Servlet named 'dispatcherServlet' will
 * always be mapped to '/'. Filter beans will be mapped to all URLs ('/*').
 * <p>
 * For more advanced configuration, the context can instead define beans that implement
 * the {@link ServletContextInitializer} interface (most often
 * {@link ServletRegistrationBean}s and/or {@link FilterRegistrationBean}s). To prevent
 * double registration, the use of {@link ServletContextInitializer} beans will disable
 * automatic Servlet and Filter bean registration.
 * <p>
 * Although this context can be used directly, most developers should consider using the
 * {@link AnnotationConfigServletWebServerApplicationContext} or
 * {@link XmlServletWebServerApplicationContext} variants.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @see AnnotationConfigServletWebServerApplicationContext
 * @see XmlServletWebServerApplicationContext
 * @see ServletWebServerFactory
 */
public class ServletWebServerApplicationContext extends GenericWebApplicationContext
        implements ConfigurableWebServerApplicationContext {

    private static final Log logger = LogFactory
            .getLog(ServletWebServerApplicationContext.class);

    /**
     * Constant value for the DispatcherServlet bean name. A Servlet bean with this name
     * is deemed to be the "main" servlet and is automatically given a mapping of "/" by
     * default. To change the default behavior you can use a
     * {@link ServletRegistrationBean} or a different bean name.
     */
    public static final String DISPATCHER_SERVLET_NAME = "dispatcherServlet";

    private volatile WebServer webServer;

    private ServletConfig servletConfig;

    private String serverNamespace;

    /**
     * Create a new {@link ServletWebServerApplicationContext}.
     */
    public ServletWebServerApplicationContext() {
    }

    /**
     * Create a new {@link ServletWebServerApplicationContext} with the given
     * {@code DefaultListableBeanFactory}.
     *
     * @param beanFactory the DefaultListableBeanFactory instance to use for this context
     */
    public ServletWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
    }

    /**
     * Register ServletContextAwareProcessor.
     *
     * @see ServletContextAwareProcessor
     */
    @Override
    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        beanFactory.addBeanPostProcessor(
                new WebApplicationContextServletContextAwareProcessor(this));
        beanFactory.ignoreDependencyInterface(ServletContextAware.class);
    }

    @Override
    public final void refresh() throws BeansException, IllegalStateException {
        try {
            super.refresh();
        } catch (RuntimeException ex) {
            stopAndReleaseWebServer();
            throw ex;
        }
    }

    /**
     * 应用上下文刷新，springboot提供的ServletWebServerApplicationContext重写了onFresh方法，
     * 准确来说 应该扩展，因为父类AbstractApplicationContext的onRefresh就是为了留给子类扩展的，
     * 创建WebServer对象
     */
    @Override
    protected void onRefresh() {
        super.onRefresh();
        try {
            /**
             * ServletWebServerApplicationContext这个子类实现onRefresh方法，启动tomcat
             */
            createWebServer();
        } catch (Throwable ex) {
            throw new ApplicationContextException("Unable to start web server", ex);
        }
    }

    /**
     * start()何时调用？
     * spring容器的refresh方法中的最后一步finishRefresh方法，该类重写了finishRefresh方法
     * 启动Web服务：真正完成springboot启动的方法，依然是由TomcatWebServer这个类完成的
     */
    @Override
    protected void finishRefresh() {
        super.finishRefresh();
        /**
         * 启动web服务
         */
        WebServer webServer = startWebServer();
        if (webServer != null) {
            publishEvent(new ServletWebServerInitializedEvent(webServer, this));
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
        stopAndReleaseWebServer();
    }

    private void createWebServer() {

        WebServer webServer = this.webServer;

        ServletContext servletContext = getServletContext();

        /**
         * 如果没有外部容器启动 那么创建一个内部容器
         */
        if (webServer == null && servletContext == null) {

            /**
             * 获取servlet容器工厂 使用工厂来创建tomcat容器
             */
            ServletWebServerFactory factory = getWebServerFactory();

            /**
             * 获取实现了servletContextInitializers接口实现类
             */
            ServletContextInitializer servletContextInitializer = getSelfInitializer();

            /**
             * 创建和启动tomcat容器，传入servletContextInitializers
             */

            this.webServer = factory.getWebServer(servletContextInitializer);

        } else if (servletContext != null) {
            // 如果servletContext不为空 说明有外部容器 那么直接回调ServletContextInitializer的onStartup()方法
            try {
                ServletContextInitializer servletContextInitializer = getSelfInitializer();
                servletContextInitializer.onStartup(servletContext);
            } catch (ServletException ex) {
                throw new ApplicationContextException("Cannot initialize servlet context",
                        ex);
            }
        }
        initPropertySources();
    }

    /**
     * Returns the {@link ServletWebServerFactory} that should be used to create the
     * embedded {@link WebServer}. By default this method searches for a suitable bean in
     * the context itself.
     *
     * @return a {@link ServletWebServerFactory} (never {@code null})
     */
    protected ServletWebServerFactory getWebServerFactory() {
        // Use bean names so that we don't consider the hierarchy
        String[] beanNames = getBeanFactory()
                .getBeanNamesForType(ServletWebServerFactory.class);
        if (beanNames.length == 0) {
            throw new ApplicationContextException(
                    "Unable to start ServletWebServerApplicationContext due to missing "
                            + "ServletWebServerFactory bean.");
        }
        if (beanNames.length > 1) {
            throw new ApplicationContextException(
                    "Unable to start ServletWebServerApplicationContext due to multiple "
                            + "ServletWebServerFactory beans : "
                            + StringUtils.arrayToCommaDelimitedString(beanNames));
        }
        return getBeanFactory().getBean(beanNames[0], ServletWebServerFactory.class);
    }

    /**
     * Returns the {@link ServletContextInitializer} that will be used to complete the
     * setup of this {@link WebApplicationContext}.
     *
     * @return the self initializer
     * @see #prepareWebApplicationContext(ServletContext)
     */
    private org.springframework.boot.web.servlet.ServletContextInitializer getSelfInitializer() {
        return this::selfInitialize;
    }

    /**
     * 启动ServletContextInitializer
     *
     * @param servletContext
     * @throws ServletException
     */
    private void selfInitialize(ServletContext servletContext) throws ServletException {
        prepareWebApplicationContext(servletContext);
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        ExistingWebApplicationScopes existingScopes = new ExistingWebApplicationScopes(beanFactory);
        WebApplicationContextUtils.registerWebApplicationScopes(beanFactory, getServletContext());
        existingScopes.restore();
        WebApplicationContextUtils.registerEnvironmentBeans(beanFactory, getServletContext());
        /**
         * 获取ServletContextInitializer集合
         */
        Collection<ServletContextInitializer> servletContextInitializerBeans = getServletContextInitializerBeans();
        /**
         * 启动ServletContextInitializer的onStartup方法
         */
        for (ServletContextInitializer beans : servletContextInitializerBeans) {
            beans.onStartup(servletContext);
        }
    }

    /**
     * Returns {@link ServletContextInitializer}s that should be used with the embedded
     * web server. By default this method will first attempt to find
     * {@link ServletContextInitializer}, {@link Servlet}, {@link Filter} and certain
     * {@link EventListener} beans.
     *
     * @return the servlet initializer beans
     */
    protected Collection<ServletContextInitializer> getServletContextInitializerBeans() {
        return new ServletContextInitializerBeans(getBeanFactory());
    }

    /**
     * Prepare the {@link WebApplicationContext} with the given fully loaded
     * {@link ServletContext}. This method is usually called from
     * {@link ServletContextInitializer#onStartup(ServletContext)} and is similar to the
     * functionality usually provided by a {@link ContextLoaderListener}.
     *
     * @param servletContext the operational servlet context
     */
    protected void prepareWebApplicationContext(ServletContext servletContext) {
        Object rootContext = servletContext.getAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        if (rootContext != null) {
            if (rootContext == this) {
                throw new IllegalStateException(
                        "Cannot initialize context because there is already a root application context present - "
                                + "check whether you have multiple ServletContextInitializers!");
            }
            return;
        }
        Log logger = LogFactory.getLog(ContextLoader.class);
        servletContext.log("Initializing Spring embedded WebApplicationContext");
        try {
            servletContext.setAttribute(
                    WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this);
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Published root WebApplicationContext as ServletContext attribute with name ["
                                + WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE
                                + "]");
            }
            setServletContext(servletContext);
            if (logger.isInfoEnabled()) {
                long elapsedTime = System.currentTimeMillis() - getStartupDate();
                logger.info("Root WebApplicationContext: initialization completed in "
                        + elapsedTime + " ms");
            }
        } catch (RuntimeException | Error ex) {
            logger.error("Context initialization failed", ex);
            servletContext.setAttribute(
                    WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ex);
            throw ex;
        }
    }

    /**
     * 仍然是TomcatWebServer调用start进行启动的
     *
     * @return
     */
    private WebServer startWebServer() {
        WebServer webServer = this.webServer;
        if (webServer != null) {
            webServer.start();
        }
        return webServer;
    }

    private void stopAndReleaseWebServer() {
        WebServer webServer = this.webServer;
        if (webServer != null) {
            try {
                webServer.stop();
                this.webServer = null;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @Override
    protected Resource getResourceByPath(String path) {
        if (getServletContext() == null) {
            return new ClassPathContextResource(path, getClassLoader());
        }
        return new ServletContextResource(getServletContext(), path);
    }

    @Override
    public String getServerNamespace() {
        return this.serverNamespace;
    }

    @Override
    public void setServerNamespace(String serverNamespace) {
        this.serverNamespace = serverNamespace;
    }

    @Override
    public void setServletConfig(ServletConfig servletConfig) {
        this.servletConfig = servletConfig;
    }

    @Override
    public ServletConfig getServletConfig() {
        return this.servletConfig;
    }

    /**
     * Returns the {@link WebServer} that was created by the context or {@code null} if
     * the server has not yet been created.
     *
     * @return the embedded web server
     */
    @Override
    public WebServer getWebServer() {
        return this.webServer;
    }

    /**
     * Utility class to store and restore any user defined scopes. This allow scopes to be
     * registered in an ApplicationContextInitializer in the same way as they would in a
     * classic non-embedded web application context.
     */
    public static class ExistingWebApplicationScopes {

        private static final Set<String> SCOPES;

        static {
            Set<String> scopes = new LinkedHashSet<>();
            scopes.add(WebApplicationContext.SCOPE_REQUEST);
            scopes.add(WebApplicationContext.SCOPE_SESSION);
            SCOPES = Collections.unmodifiableSet(scopes);
        }

        private final ConfigurableListableBeanFactory beanFactory;

        private final Map<String, Scope> scopes = new HashMap<>();

        public ExistingWebApplicationScopes(ConfigurableListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
            for (String scopeName : SCOPES) {
                Scope scope = beanFactory.getRegisteredScope(scopeName);
                if (scope != null) {
                    this.scopes.put(scopeName, scope);
                }
            }
        }

        public void restore() {
            this.scopes.forEach((key, value) -> {
                if (logger.isInfoEnabled()) {
                    logger.info("Restoring user defined scope " + key);
                }
                this.beanFactory.registerScope(key, value);
            });
        }

    }

}
