package sample.tomcat.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * 自定义condition注解
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(MyConditional.class)
public @interface MyConditionalAnnotation {

    String key();

    String value();

}
