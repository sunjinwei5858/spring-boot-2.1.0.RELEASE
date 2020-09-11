package sample.tomcat.condition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * config配置
 */
@Configuration
public class MyConditionalConfig {


    @MyConditionalAnnotation(key = "com.sunjinwei.condition", value = "sjw")
    @Bean
    public MyConditionalService getMyConditionalService() {
        System.out.println("==================MyConditionalService 已经加载");
        return new MyConditionalServiceImpl();
    }


    @Conditional(MyMacCondition.class)
    @Bean
    public MyMacService getMyMacService() {
        System.out.println("mac================MyMacService已经加载=====");
        return new MyMacService(12, "hahahahahha");
    }

}
