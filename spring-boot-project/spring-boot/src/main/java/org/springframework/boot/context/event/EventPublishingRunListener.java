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

package org.springframework.boot.context.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * SpringApplicationRunListener仅有一个实现类: EventPublishingRunListener, 需要在spring.factories文件配置
 * 核心点：
 * 1.EventPublishingRunListener是一个事件发布器, 在启动过程的不同阶段 它构造出不同的Event, 通过initialMulticaster广播出去,
 * 2.SpringApplicationRunListeners与SpringApplicationRunListener生命周期相同，
 * 3.调用每个周期的各个SpringApplicationRunListener。
 * 4.然后广播相应的事件到Spring框架的ApplicationListener。
 *
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 */
public class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

    private final SpringApplication application;

    private final String[] args;

    private final SimpleApplicationEventMulticaster initialMulticaster;

	/**
	 * 构造函数做的事情就是将spring.factories文件的ApplicationListener监听器添加到多播器中
	 * 这里就将SpringApplicationRunListener监听器和spring的ApplicationListener关联起来了
	 *
	 * @param application
	 * @param args
	 */
	public EventPublishingRunListener(SpringApplication application, String[] args) {
        this.application = application;
        this.args = args;
		/**
		 * 重要1：这个多播器就是ApplicationEventMulticaster，会在spring的refresh方法中
		 */
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
		/**
		 * 重要2：这里会spring.factories的ApplicationListener添加到初始化多播器中
		 */
        for (ApplicationListener<?> listener : application.getListeners()) {
            this.initialMulticaster.addApplicationListener(listener);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }

	/**
	 * 1发布一个ApplicationStartingEvent的event事件
	 */
	@Override
    public void starting() {
        this.initialMulticaster.multicastEvent(new ApplicationStartingEvent(this.application, this.args));
    }

    /**
	 * 2发布一个ApplicationEnvironmentPreparedEvent的event事件
     * @param environment the environment
     */
    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        /**
         * 这里包装了一个ApplicationEnvironmentPreparedEvent事件，并通过广播的方式广播给监听该事件的监听器，
         * 到这个时候才触发了ConfigFileApplicationListener
         */
        ApplicationEnvironmentPreparedEvent event = new ApplicationEnvironmentPreparedEvent(this.application, this.args, environment);
        this.initialMulticaster.multicastEvent(event);
    }

	/**
	 * 3发布一个ApplicationContextInitializedEvent事件
	 * @param context the application context
	 */
	@Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        this.initialMulticaster.multicastEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
    }

	/**
	 * 4 发布一个ApplicationPreparedEvent的event事件
	 * @param context the application context
	 */
	@Override
    public void contextLoaded(ConfigurableApplicationContext context) {
        for (ApplicationListener<?> listener : this.application.getListeners()) {
            if (listener instanceof ApplicationContextAware) {
                ((ApplicationContextAware) listener).setApplicationContext(context);
            }
            context.addApplicationListener(listener);
        }
        this.initialMulticaster.multicastEvent(new ApplicationPreparedEvent(this.application, this.args, context));
    }

	/**
	 * 5 发布一个ApplicationStartedEvent事件
	 * @param context the application context.
	 */
	@Override
    public void started(ConfigurableApplicationContext context) {
        context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context));
    }

	/**
	 * 6 发布一个ApplicationReadyEvent事件
	 * @param context the application context.
	 */
	@Override
    public void running(ConfigurableApplicationContext context) {
        context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context));
    }

	/**
	 * 7 发布ApplicationFailedEvent事件
	 * @param context   the application context or {@code null} if a failure occurred before
	 *                  the context was created
	 * @param exception the failure
	 */
	@Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
        if (context != null && context.isActive()) {
            // Listeners have been registered to the application context so we should
            // use it at this point if we can
            context.publishEvent(event);
        } else {
            // An inactive context may not have a multicaster so we use our multicaster to
            // call all of the context's listeners instead
            if (context instanceof AbstractApplicationContext) {
                for (ApplicationListener<?> listener : ((AbstractApplicationContext) context)
                        .getApplicationListeners()) {
                    this.initialMulticaster.addApplicationListener(listener);
                }
            }
            this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
            this.initialMulticaster.multicastEvent(event);
        }
    }

    private static class LoggingErrorHandler implements ErrorHandler {

        private static Log logger = LogFactory.getLog(EventPublishingRunListener.class);

        @Override
        public void handleError(Throwable throwable) {
            logger.warn("Error calling ApplicationEventListener", throwable);
        }

    }

}
