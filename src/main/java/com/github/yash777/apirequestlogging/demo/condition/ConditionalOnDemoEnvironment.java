package com.github.yash777.apirequestlogging.demo.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * <h2>ConditionalOnDemoEnvironment</h2>
 * <p>A composed annotation that triggers a bean only if the following conditions are met:</p>
 * <ul>
 * <li><b>Guard 1:</b> The {@code DemoApplication} class must be present in the classpath.</li>
 * <li><b>Guard 2:</b> The property {@code api.request.logging.live-demo} must be set to {@code true}.</li>
 * <li><b>Guard 3:</b> A bean named {@code dummyBean} must exist in the Spring Context.</li>
 * </ul>
 *
 * @author Yash
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
// Guard 1: Class existence check
@ConditionalOnClass(name = "com.github.yash777.apirequestlogging.demo.DemoApplication")
// Guard 2: JVM/Property check
//@ConditionalOnProperty(
//    prefix = "api.request.logging",
//    name = "live-demo",
//    havingValue = "true",
//    matchIfMissing = false
//)
//// Guard 3: Contextual bean check
//@ConditionalOnBean(name = "dummyBean")
////Use our custom logic class here instead of the standard annotation
////@Conditional(DummyBeanCondition.class)
/**
 * <h2>ConditionalOnDemoEnvironment</h2>
 * <p>Activates only if the Main class sets the internal flag AND pom.xml property is true.</p>
 * <ul>
 * <li><b>Guard 1:</b> DemoApplication class must be in classpath.</li>
 * <li><b>Guard 2:</b> 'api.request.logging.live-demo' must be 'true' (from pom/yaml).</li>
 * <li><b>Guard 3:</b> 'internal.app.non-consumer' must be 'true' (set in main method).</li>
 * </ul>
 */
// Duplicate annotation of non-repeatable type @ConditionalOnProperty. Only annotation types marked @Repeatable can be used multiple times at one target.
//@ConditionalOnProperty(
//	    name = "internal.app.non-consumer",
//	    havingValue = "true"
//	)
//Logic: Both properties must be true (handled by our custom class)
@Conditional(DemoEnvironmentCondition.class)
@AutoConfigureAfter(com.github.yash777.apirequestlogging.demo.DemoApplication.class) // Crucial: Wait for Main class beans
public @interface ConditionalOnDemoEnvironment {
}