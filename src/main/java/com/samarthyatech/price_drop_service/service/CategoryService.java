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

    public CategoryResult detectFromKeywords(String title) {

        if (title == null) return new CategoryResult("general", "general");

        String lower = title.toLowerCase();

        Map<String, Map<String, Map<String, List<String>>>> config = configService.getConfig();

        Map<String, Map<String, List<String>>> breadcrumbMap = config.get("breadcrumbMapping");
        Map<String, Map<String, List<String>>> keywordMap = config.get("keywordMapping");

        for (String category : breadcrumbMap.keySet()) {

            Map<String, List<String>> subMap = breadcrumbMap.get(category);

            for (String sub : subMap.keySet()) {

                List<String> keywords = subMap.get(sub);

                for (String keyword : keywords) {

                    if (lower.contains(keyword)) {
                        return new CategoryResult(category, sub);
                    }
                }
            }
        }

        return new CategoryResult("general", "general");
    }

    public CategoryResult detect(String title, List<String> breadcrumb) {

        // 1. Try breadcrumb (BEST)
        CategoryResult fromBreadcrumb = detectFromBreadcrumb(breadcrumb);
        if (fromBreadcrumb != null) return fromBreadcrumb;

        // 2. Fallback → keyword config
        return detectFromKeywords(title);
    }

    public CategoryResult detectFromBreadcrumb(List<String> breadcrumb) {

        if (breadcrumb == null || breadcrumb.isEmpty()) {
            return null;
        }

        String category = breadcrumb.get(0); // broad
        String subCategory = breadcrumb.get(breadcrumb.size() - 1); // most specific

        return new CategoryResult(category, subCategory);
    }
}
