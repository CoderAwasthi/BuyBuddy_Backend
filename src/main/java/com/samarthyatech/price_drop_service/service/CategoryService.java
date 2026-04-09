package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.CategoryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryConfigService configService;

    public CategoryResult detect(String title) {

        if (title == null) return new CategoryResult("general", "general");

        String lower = title.toLowerCase();

        Map<String, Map<String, List<String>>> config = configService.getCategoryMap();

        for (String category : config.keySet()) {

            Map<String, List<String>> subMap = config.get(category);

            for (String sub : subMap.keySet()) {

                for (String keyword : subMap.get(sub)) {

                    if (lower.contains(keyword)) {
                        return new CategoryResult(category, sub);
                    }
                }
            }
        }

        return new CategoryResult("general", "general");
    }
}
