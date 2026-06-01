package com.example.notifications.config;

import com.example.notifications.backpressure.BackPressureInterceptor;
import com.example.notifications.backpressure.WorkerQueueMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final WorkerQueueMetrics workerQueueMetrics;

    @Bean
    public BackPressureInterceptor backPressureInterceptor() {
        return new BackPressureInterceptor(workerQueueMetrics);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(backPressureInterceptor())
                .addPathPatterns("/campaigns", "/campaigns/**");
    }
}
