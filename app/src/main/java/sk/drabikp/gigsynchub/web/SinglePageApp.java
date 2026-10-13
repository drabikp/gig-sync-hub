package sk.drabikp.gigsynchub.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Serves the page (the frontend module's build, {@code classpath:/static/}): a file when it
 * exists, otherwise {@code index.html}, so the page's own addresses ({@code /gigs/…},
 * {@code /calendar}) load it too. {@code /api/…} is never answered with the page.
 */
@Configuration
class SinglePageApp implements WebMvcConfigurer {

    /** What is a file the page asked for, not one of the page's own addresses (which may hold dots: event ids). */
    private static final Pattern ASSET = Pattern.compile(
            "\\.(js|mjs|css|map|png|jpe?g|gif|svg|ico|webp|woff2?|ttf|json|webmanifest|txt|xml|html)$");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    /**
                     * The page's own addresses answer with {@code index.html}; a file answers with
                     * itself — and it is {@code super} that decides whether what the path resolved
                     * to is really UNDER the location. This used to call
                     * {@code location.createRelative(path)} and return whatever existed, which is
                     * the one method whose whole job is that check; {@code ../} in a classpath
                     * resource reaches other classpath entries, and what is on this classpath
                     * includes {@code application.properties} and every migration. Nothing was
                     * reachable in practice — the handler cleans and rejects traversal before the
                     * resolver chain ever runs — but a layer that exists for exactly this had been
                     * taken out, leaving one guard whose behaviour is Spring's to change.
                     */
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = super.getResource(path, location);
                        if (file != null) {
                            return file;
                        }
                        if (path.startsWith("api/") || path.startsWith("actuator/") || ASSET.matcher(path).find()) {
                            return null;                  // a missing file stays missing
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
