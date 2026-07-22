package com.employeetracker.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.root-dir:uploads}")
    private String uploadRootDir;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
        // Serves the Password Setup page at the clean URL used in the welcome
        // email link: ${app.base-url}/setup-password?token=<token>
        registry.addViewController("/setup-password").setViewName("forward:/setup-password.html");
    }

    /**
     * Serves uploaded employee profile photos (saved to disk by
     * FileStorageService) back out at /uploads/** so the Employee List and
     * View/Edit modals can render them via a plain <img src="..."> - the
     * same relative URL that's stored in Users.PhotoUrl.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadRootDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // The SW script itself must never be cached by the browser's HTTP cache or an
        // intermediary proxy/CDN - otherwise a stale service-worker.js can keep being
        // served for up to 24h (the browser's default heuristic) regardless of the
        // no-store fetch options used inside the worker's own fetch handler.
        //
        // Every /api/** response gets the same treatment. These endpoints always
        // return data scoped to the caller's authenticated session (see
        // AuthService#getCurrentUserEntity), so a response is only ever valid
        // for whichever user was authenticated when it was generated. If a
        // response like GET /api/location/distance or /api/location/current is
        // ever cached (by the browser's HTTP cache, or an intermediary proxy/
        // CDN sitting in front of the app) and later replayed without hitting
        // the server, a different employee who logs in afterwards on the same
        // browser/device can be served the previous employee's data (e.g. a
        // stale "Today's Distance") purely from cache, even though the
        // backend query itself is correctly scoped by userId. Setting these
        // headers explicitly here - rather than relying solely on Spring
        // Security's default header writer - guarantees this protection
        // holds regardless of future security config changes.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                      HttpServletResponse response, Object handler) {
                if (request.getRequestURI().equals("/service-worker.js")
                        || request.getRequestURI().startsWith("/api/")) {
                    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                }
                return true;
            }
        });
    }
}