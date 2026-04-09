package com.samarthyatech.price_drop_service.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Getter
@Service
public class CategoryConfigService {

    private Map<String, Map<String, List<String>>> categoryMap;

    @PostConstruct
    public void loadConfig() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("category-config.json");

        categoryMap = mapper.readValue(is, Map.class);
    }

}
