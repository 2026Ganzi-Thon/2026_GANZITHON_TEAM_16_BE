package com.mowoo.mowoo_backend.global.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DotEnvConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String directory = environment.getProperty("app.dotenv.directory", ".");
        Dotenv dotenv = Dotenv.configure()
                .directory(directory)
                .ignoreIfMissing()
                .load();

        Map<String, Object> values = new HashMap<>();
        dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)
                .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
    }
}
