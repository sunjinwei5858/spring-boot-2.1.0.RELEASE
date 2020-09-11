package sample.tomcat.condition;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 自定义mac condition  如果我的是mac环境 那么返回true
 */
public class MyMacCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        System.out.println("=======environment======"+environment.toString());
        String osName = environment.getProperty("os.name");
        System.out.println("==========osname========= " + osName);
        boolean mac = osName.contains("Mac");
        return new ConditionOutcome(mac, "ok");
    }
}
