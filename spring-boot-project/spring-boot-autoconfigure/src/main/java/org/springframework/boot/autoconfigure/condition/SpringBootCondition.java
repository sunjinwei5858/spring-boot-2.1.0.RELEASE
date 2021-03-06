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

package org.springframework.boot.autoconfigure.condition;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Base of all {@link Condition} implementations used with Spring Boot. Provides sensible
 * logging to help the user diagnose what classes are loaded.
 *
 * @author Phillip Webb
 * @author Greg Turnquist
 * <p>
 * SpringBootCondition作为springboot条件注解的基类，实现了condition接口，
 *
 * 然后又有很多具体的子类OnXXXCondition,这些OnXXXCondition其实就是@ConditionOnXXX的条件类。
 *
 * 主要看做了哪些事情？抽象了哪些共有的逻辑？
 * 1.抽象了所有具体实现类的共有信息-condition评估信息打印
 * 2.封装了一个模板方法，getMatchOutcome(context,metadata)，
 * 留给各个OnXXXCondition具体子类去覆盖实现属于自己的判断逻辑，
 * 然后再返回相应的匹配结果给SpringBootCondition用于日志打印。
 *
 * ===》
 * SpringBootCondition其实就是打印condition的评估信息的，
 * 重点是抽象模板方法，getMatchOutcome(context,metadata)
 */
public abstract class SpringBootCondition implements Condition {

    private final Log logger = LogFactory.getLog(getClass());

    /**
     * 因为SpringBootCondition实现了Condition接口，也实现了matches方法，
     * 因此该方法同样也是被ConditionEvaluator的shouldSkip方法中调用，
     * 因此我们就以SpringBootCondition的matches方法为入口去进行分析
     *
     * @param context
     * @param metadata
     * @return
     */
    @Override
    public final boolean matches(ConditionContext context,
                                 AnnotatedTypeMetadata metadata) {
        // 得到metadata的类名或者方法名
        String classOrMethodName = getClassOrMethodName(metadata);
        try {
            // 判断每个配置类的每个条件注解@ConditionalOnXXX是否满足条件，然后记录到ConditionOutcome结果中
            // 注意getOutcome是一个抽象方法，交给OnXXXCondition子类去实现
            ConditionOutcome outcome = getMatchOutcome(context, metadata);
            // 打印condition评估的日志，
            // 哪些条件注解@ConditionOnXXX是满足条件的，哪些是不满足条件的，这些日志都打印出来
            logOutcome(classOrMethodName, outcome);
            // 除了打印日志外，这些是否匹配的信息，还要记录到ConditionEvaluateReport中
            recordEvaluation(context, classOrMethodName, outcome);
            // 最后返回@ConditionOnXXX是否满足条件
            return outcome.isMatch();
        } catch (NoClassDefFoundError ex) {
            throw new IllegalStateException(
                    "Could not evaluate condition on " + classOrMethodName + " due to "
                            + ex.getMessage() + " not "
                            + "found. Make sure your own configuration does not rely on "
                            + "that class. This can also happen if you are "
                            + "@ComponentScanning a springframework package (e.g. if you "
                            + "put a @ComponentScan in the default package by mistake)",
                    ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Error processing condition on " + getName(metadata), ex);
        }
    }

    private String getName(AnnotatedTypeMetadata metadata) {
        if (metadata instanceof AnnotationMetadata) {
            return ((AnnotationMetadata) metadata).getClassName();
        }
        if (metadata instanceof MethodMetadata) {
            MethodMetadata methodMetadata = (MethodMetadata) metadata;
            return methodMetadata.getDeclaringClassName() + "."
                    + methodMetadata.getMethodName();
        }
        return metadata.toString();
    }

    private static String getClassOrMethodName(AnnotatedTypeMetadata metadata) {
        if (metadata instanceof ClassMetadata) {
            ClassMetadata classMetadata = (ClassMetadata) metadata;
            return classMetadata.getClassName();
        }
        MethodMetadata methodMetadata = (MethodMetadata) metadata;
        return methodMetadata.getDeclaringClassName() + "#"
                + methodMetadata.getMethodName();
    }

    protected final void logOutcome(String classOrMethodName, ConditionOutcome outcome) {
        if (this.logger.isTraceEnabled()) {
            this.logger.trace(getLogMessage(classOrMethodName, outcome));
        }
    }

    private StringBuilder getLogMessage(String classOrMethodName,
                                        ConditionOutcome outcome) {
        StringBuilder message = new StringBuilder();
        message.append("Condition ");
        message.append(ClassUtils.getShortName(getClass()));
        message.append(" on ");
        message.append(classOrMethodName);
        message.append(outcome.isMatch() ? " matched" : " did not match");
        if (StringUtils.hasLength(outcome.getMessage())) {
            message.append(" due to ");
            message.append(outcome.getMessage());
        }
        return message;
    }

    private void recordEvaluation(ConditionContext context, String classOrMethodName,
                                  ConditionOutcome outcome) {
        if (context.getBeanFactory() != null) {
            ConditionEvaluationReport.get(context.getBeanFactory())
                    .recordConditionEvaluation(classOrMethodName, this, outcome);
        }
    }

    /**
     * 抽象模板方法，交给OnXXXCondition子类去实现
     * Determine the outcome of the match along with suitable log output.
     *
     * @param context  the condition context
     * @param metadata the annotation metadata
     * @return the condition outcome
     */
    public abstract ConditionOutcome getMatchOutcome(ConditionContext context,
                                                     AnnotatedTypeMetadata metadata);

    /**
     * Return true if any of the specified conditions match.
     *
     * @param context    the context
     * @param metadata   the annotation meta-data
     * @param conditions conditions to test
     * @return {@code true} if any condition matches.
     */
    protected final boolean anyMatches(ConditionContext context,
                                       AnnotatedTypeMetadata metadata, Condition... conditions) {
        for (Condition condition : conditions) {
            if (matches(context, metadata, condition)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if any of the specified condition matches.
     *
     * @param context   the context
     * @param metadata  the annotation meta-data
     * @param condition condition to test
     * @return {@code true} if the condition matches.
     */
    protected final boolean matches(ConditionContext context,
                                    AnnotatedTypeMetadata metadata, Condition condition) {
        if (condition instanceof SpringBootCondition) {
            return ((SpringBootCondition) condition).getMatchOutcome(context, metadata)
                    .isMatch();
        }
        return condition.matches(context, metadata);
    }

}
