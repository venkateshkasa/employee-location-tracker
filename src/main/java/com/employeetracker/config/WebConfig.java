package com.employeetracker.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // The SW script itself must never be cached by the browser's HTTP cache or an
        // intermediary proxy/CDN - otherwise a stale service-worker.js can keep being
        // served for up to 24h (the browser's default heuristic) regardless of the
        // no-store fetch options used inside the worker's own fetch handler.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                      HttpServletResponse response, Object handler) {
                if (request.getRequestURI().equals("/service-worker.js")) {
                    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                }
                return true;
            }
        });
    }
}