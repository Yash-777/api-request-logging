package com.github.yash777.apirequestlogging.demo.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Custom condition to manually verify the existence of 'dummyBean'.
 * This is more reliable for class-level filtering when beans are 
 * defined in the Main class.
 */
public class DummyBeanCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // Access the bean factory and check if the bean definition exists
        return context.getBeanFactory().containsBean("dummyBean");
    }
}