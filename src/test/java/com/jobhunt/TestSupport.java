package com.jobhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.config.JobhuntProperties;
import com.jobhunt.model.Company;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

public final class TestSupport {

    public static final ObjectMapper JSON = new ObjectMapper();

    private TestSupport() {}

    /** The real application.yml rules, bound exactly as Spring would. */
    public static JobhuntProperties props() {
        try {
            PropertySource<?> ps = new YamlPropertySourceLoader()
                    .load("app", new ClassPathResource("application.yml")).get(0);
            MutablePropertySources sources = new MutablePropertySources();
            sources.addFirst(ps);
            return new Binder(ConfigurationPropertySources.from(sources))
                    .bind("jobhunt", JobhuntProperties.class).get();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Company company(String ats, String slug) {
        return new Company(ats, slug, slug, null, null, null, null, null);
    }

    public static com.fasterxml.jackson.databind.JsonNode json(String s) {
        try {
            return JSON.readTree(s);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static InputStream resource(String name) {
        return TestSupport.class.getResourceAsStream(name);
    }
}
