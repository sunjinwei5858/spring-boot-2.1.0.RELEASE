package sample.tomcat.listener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 自定义springboot的监听器 可以扩展springboot的run方法
 */
public class HelloApplicationRunListener implements SpringApplicationRunListener {

	public HelloApplicationRunListener(SpringApplication application, String[] args){
		System.out.println("监听器 构造");
	}


	@Override
	public void starting() {
		System.out.println("自定义开启springboot的监听器--starting");
	}

	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		System.out.println("自定义开启springboot的监听器--environmentPrepared");
	}

	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		System.out.println("自定义开启springboot的监听器--contextPrepared");
	}

	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {
		System.out.println("自定义开启springboot的监听器--contextLoaded");
	}

	@Override
	public void started(ConfigurableApplicationContext context) {
		System.out.println("自定义开启springboot的监听器--started");
	}

	@Override
	public void running(ConfigurableApplicationContext context) {
		System.out.println("自定义开启springboot的监听器--running");
	}

	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		System.out.println("自定义开启springboot的监听器--failed");
	}
}
