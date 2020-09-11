package sample.tomcat.condition;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;
import java.util.Objects;

/**
 * 自定义一个condition
 */
public class MyConditional extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 获取注解的属性
        Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(MyConditionalAnnotation.class.getName());
        // 获取属性值
        Object key = annotationAttributes.get("key");
        Object value = annotationAttributes.get("value");
        if (Objects.isNull(key) || Objects.isNull(value)) {
            return new ConditionOutcome(false, "error....");
        }
        // 获取environment的值
        String property = context.getEnvironment().getProperty(key.toString());
        if (property.equals(value)) {
            //如果environment中的值与指定 的value一致，则返回true
            return new ConditionOutcome(true, "ok");
        }
        return new ConditionOutcome(false, "error....");
    }
}
