package com.github.yash777.apirequestlogging.demo.condition;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.github.yash777.apirequestlogging.demo.DemoApplication;

/**
 * <h2>DemoEnvironmentCondition</h2>
 * <p>Checks both the Spring Environment and the JVM System properties
 * to ensure the 'non-consumer' flag is captured.</p>
 */
@AutoConfigureAfter(com.github.yash777.apirequestlogging.demo.DemoApplication.class) // Crucial: Wait for Main class beans
public class DemoEnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    	System.err.println("----- DemoEnvironmentCondition implements Condition ::"+ DemoApplication.getNonConsumer());
    	
    	Environment env = context.getEnvironment();

//        // 1. Manual Class Check (replaces @ConditionalOnClass)
//        boolean classExists = ClassUtils.isPresent(
//            "com.github.yash777.apirequestlogging.demo.DemoApplication", 
//            context.getClassLoader()
//        );
        
        // 1. Check property from pom.xml / application.properties
        String liveDemo = env.getProperty("api.request.logging.live-demo");
        
        // 2. Check property from Main method (Try Environment first, then direct System)
        String isNonConsumer = env.getProperty("internal.app.non-consumer");
        if (isNonConsumer == null) {
            isNonConsumer = System.getProperty("internal.app.non-consumer");
        }

        boolean match = "true".equalsIgnoreCase(liveDemo) && "true".equalsIgnoreCase(isNonConsumer);

        // This will print in your console during startup so you can debug!
        System.err.println("[Condition Check] live-demo: " + liveDemo + 
                           " | non-consumer: " + isNonConsumer + 
                           " | non-consumer Main: " + DemoApplication.getNonConsumer() +
                           " | Result: " + match);
        //return mathch;
        return DemoApplication.getNonConsumer();
    }
}
