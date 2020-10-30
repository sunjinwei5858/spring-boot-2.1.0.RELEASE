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

package org.springframework.boot.autoconfigure.condition;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends FilteringSpringBootCondition {

    /**
     * 主要的设计思路，自己第一次见加入线程去处理的场景，需要学习!!!!
     * 1。考虑到解析自动配置类是否满足条件是一项比较耗时的操作，这里开启了两个线程去处理，
     * 为什么是两个线程，是经过测试的，2个性能是最好的，大于2个线程去跑性能反而不好。
     * <p>
     * 2。使用了Thread.join()的方式，保证两个线程处理完毕再进入合并的方法!!!
     * join()括号里面如果不指定数字，那么就是无限等待，直到该线程处理完毕
     *
     * @param autoConfigurationClasses
     * @param autoConfigurationMetadata
     * @return
     */
    @Override
    protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
                                                   AutoConfigurationMetadata autoConfigurationMetadata) {
        // Split the work and perform half in a background thread. Using a single
        // additional thread seems to offer the best performance. More threads make
        // things worse
        int split = autoConfigurationClasses.length / 2;
        /**
         * firstHalfResolver:ThreadedOutcomesResolver，新线程校验前一半的自动配置类，
         * 这一步已经创建线程进行resolveOutcomes，启动start
         */
        OutcomesResolver firstHalfResolver = createOutcomesResolver(autoConfigurationClasses, 0, split, autoConfigurationMetadata);
        /**
         * secondHalfResolver: StandardOutcomesResolver，主线程校验剩下的一半自动配置类
         */
        OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(autoConfigurationClasses, split, autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
        // 先让主线程去执行解析一半自动配置类是否匹配条件
        ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();
        /**
         * !!!!! Thread.join的使用场景：
         * 为了防止主线程执行过快结束，resolveOutcomes方法里面调用了thread.join()，
         * 这里的作用：让主线程等待新线程执行结束，然后再进行合并两个线程的解析结果。
         * jdk规定，join(0)的意思不是A线程等待B线程0秒，而是A线程等待B线程无限时间，直到B线程执行完毕，即join(0)等价于join()。
         */
        ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();
        // 创建一个新的数组
        ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
        // 拷贝新线程解析的结果
        System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
        // 拷贝主线程解析的结果
        System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);
        return outcomes;
    }

    /**
     * 返回的类型是线程封装的ThreadedOutcomesResolver
     *
     * @param autoConfigurationClasses
     * @param start
     * @param end
     * @param autoConfigurationMetadata
     * @return
     */
    private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses,
                                                    int start, int end, AutoConfigurationMetadata autoConfigurationMetadata) {
        OutcomesResolver outcomesResolver = new StandardOutcomesResolver(
                autoConfigurationClasses,
                start,
                end,
                autoConfigurationMetadata,
                getBeanClassLoader());
        try {
            /**
             * ThreadedOutcomesResolver 这是个线程对象，封装了线程属性
             */
            return new ThreadedOutcomesResolver(outcomesResolver);
        } catch (AccessControlException ex) {
            //若上面开启的线程抛出AccessControlException异常，则返回StandardOutcomesResolver对象
            return outcomesResolver;
        }
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context,
                                            AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        ConditionMessage matchMessage = ConditionMessage.empty();
        List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
        if (onClasses != null) {
            List<String> missing = filter(onClasses, ClassNameFilter.MISSING,
                    classLoader);
            if (!missing.isEmpty()) {
                return ConditionOutcome
                        .noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
                                .didNotFind("required class", "required classes")
                                .items(Style.QUOTE, missing));
            }
            matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
                    .found("required class", "required classes").items(Style.QUOTE,
                            filter(onClasses, ClassNameFilter.PRESENT, classLoader));
        }
        List<String> onMissingClasses = getCandidates(metadata,
                ConditionalOnMissingClass.class);
        if (onMissingClasses != null) {
            List<String> present = filter(onMissingClasses, ClassNameFilter.PRESENT,
                    classLoader);
            if (!present.isEmpty()) {
                return ConditionOutcome.noMatch(
                        ConditionMessage.forCondition(ConditionalOnMissingClass.class)
                                .found("unwanted class", "unwanted classes")
                                .items(Style.QUOTE, present));
            }
            matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
                    .didNotFind("unwanted class", "unwanted classes")
                    .items(Style.QUOTE, filter(onMissingClasses, ClassNameFilter.MISSING,
                            classLoader));
        }
        return ConditionOutcome.match(matchMessage);
    }

    private List<String> getCandidates(AnnotatedTypeMetadata metadata,
                                       Class<?> annotationType) {
        MultiValueMap<String, Object> attributes = metadata
                .getAllAnnotationAttributes(annotationType.getName(), true);
        if (attributes == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        addAll(candidates, attributes.get("value"));
        addAll(candidates, attributes.get("name"));
        return candidates;
    }

    private void addAll(List<String> list, List<Object> itemsToAdd) {
        if (itemsToAdd != null) {
            for (Object item : itemsToAdd) {
                Collections.addAll(list, (String[]) item);
            }
        }
    }

    private interface OutcomesResolver {

        ConditionOutcome[] resolveOutcomes();

    }

    private static final class ThreadedOutcomesResolver implements OutcomesResolver {

        private final Thread thread;

        private volatile ConditionOutcome[] outcomes;


        /**
         * 构造函数：【其实已经在进行StandardOutcomesResolver的resolveOutcomes方法了】
         * 1。创建新的线程去调用StandardOutcomesResolver的resolveOutcomes方法
         * 2。线程启动
         *
         * @param outcomesResolver StandardOutcomesResolver
         */
        private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
            // 这里开启一个新的线程，这个线程其实还是利用StandardOutcomesResolver的resolveOutcomes方法
            // 对自动配置类进行解析判断是否匹配
            this.thread = new Thread(
                    () -> this.outcomes = outcomesResolver.resolveOutcomes());
            // 开启线程
            this.thread.start();
        }

        /**
         * ThreadedOutcomesResolver重写了resolveOutcomes方法
         * Thread类中的join方法的主要作用就是同步，它可以使得线程之间的并行执行变为串行执行。
         * jdk规定，join(0)的意思不是A线程等待B线程0秒，而是A线程等待B线程无限时间，直到B线程执行完毕，即join(0)等价于join()。
         *
         * @return
         */
        @Override
        public ConditionOutcome[] resolveOutcomes() {
            try {
                // jdk规定，join(0)的意思不是A线程等待B线程0秒，而是A线程等待B线程无限时间，直到B线程执行完毕，即join(0)等价于join()。
                this.thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return this.outcomes;
        }

    }

    private final class StandardOutcomesResolver implements OutcomesResolver {

        private final String[] autoConfigurationClasses;

        private final int start;

        private final int end;

        private final AutoConfigurationMetadata autoConfigurationMetadata;

        private final ClassLoader beanClassLoader;

        private StandardOutcomesResolver(String[] autoConfigurationClasses, int start,
                                         int end, AutoConfigurationMetadata autoConfigurationMetadata,
                                         ClassLoader beanClassLoader) {
            this.autoConfigurationClasses = autoConfigurationClasses;
            this.start = start;
            this.end = end;
            this.autoConfigurationMetadata = autoConfigurationMetadata;
            this.beanClassLoader = beanClassLoader;
        }

        @Override
        public ConditionOutcome[] resolveOutcomes() {
            return getOutcomes(this.autoConfigurationClasses, this.start, this.end, this.autoConfigurationMetadata);
        }

        private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
                                               int start, int end, AutoConfigurationMetadata autoConfigurationMetadata) {
            ConditionOutcome[] outcomes = new ConditionOutcome[end - start];
            for (int i = start; i < end; i++) {
                String autoConfigurationClass = autoConfigurationClasses[i];
                if (autoConfigurationClass != null) {
                    String candidates = autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnClass");
                    if (candidates != null) {
                        outcomes[i - start] = getOutcome(candidates);
                    }
                }
            }
            return outcomes;
        }

        private ConditionOutcome getOutcome(String candidates) {
            try {
                if (!candidates.contains(",")) {
                    return getOutcome(candidates, ClassNameFilter.MISSING,
                            this.beanClassLoader);
                }
                for (String candidate : StringUtils.commaDelimitedListToStringArray(candidates)) {
                    ConditionOutcome outcome = getOutcome(candidate, ClassNameFilter.MISSING, this.beanClassLoader);
                    if (outcome != null) {
                        return outcome;
                    }
                }
            } catch (Exception ex) {
                // We'll get another chance later
            }
            return null;
        }

        private ConditionOutcome getOutcome(String className,
                                            ClassNameFilter classNameFilter, ClassLoader classLoader) {
            if (classNameFilter.matches(className, classLoader)) {
                return ConditionOutcome
                        .noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
                                .didNotFind("required class").items(Style.QUOTE, className));
            }
            return null;
        }

    }

}
