package sk.drabikp.bzscraper.browser;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * A platform's portal client bean that exists only when the platform is configured with that client
 * ({@link PlatformClient#of}): one of the stub, the HTTP client and the Selenium client.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnPlatformClient.Check.class)
public @interface OnPlatformClient {

    /** The platform as in its properties' prefix, e.g. {@code bandzone}. */
    String platform();

    PlatformClient value();

    /** Compares the configured client with the annotation's. */
    final class Check implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> attributes = metadata.getAnnotationAttributes(OnPlatformClient.class.getName());
            if (attributes == null) {
                return false;
            }
            String platform = (String) attributes.get("platform");
            return PlatformClient.of(context.getEnvironment(), platform) == attributes.get("value");
        }
    }
}
