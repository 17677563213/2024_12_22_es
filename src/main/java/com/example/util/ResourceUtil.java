package com.example.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author wxy
 */
@Slf4j
public class ResourceUtil {
    
    public static String readResourceFile(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read resource file: " + resourcePath, e);
            throw new RuntimeException("Failed to read resource file: " + resourcePath, e);
        }
    }
}
