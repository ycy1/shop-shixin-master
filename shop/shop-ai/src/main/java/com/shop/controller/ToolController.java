package com.shop.controller;

import com.shop.common.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/sys/tool")
@Api(tags = "AI-工具管理")
@RequiredArgsConstructor
public class ToolController {

    @GetMapping("/list")
    @ApiOperation(value = "获取可用工具列表")
    public Result<List<ToolInfo>> list() {
        List<ToolInfo> tools = new ArrayList<>();

        // 动态扫描 com.shop.tools 包下所有 @Tool 注解类的 class
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(dev.langchain4j.agent.tool.Tool.class));

        // 先扫描所有类，再过滤出 tools 包下的
        var allCandidates = new java.util.HashSet<String>();
        try {
            // 通过包名直接扫描类
            String packagePath = "com/mojian/tools/";
            var resources = getClass().getClassLoader().getResources(packagePath);
            while (resources.hasMoreElements()) {
                var resource = resources.nextElement();
                if (resource.getProtocol().equals("file")) {
                    java.io.File dir = new java.io.File(resource.toURI());
                    if (dir.exists()) {
                        for (java.io.File file : dir.listFiles()) {
                            if (file.getName().endsWith(".class")) {
                                String className = "com.shop.tools." + file.getName().replace(".class", "");
                                allCandidates.add(className);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        for (String className : allCandidates) {
            try {
                Class<?> clazz = Class.forName(className);
                var methods = clazz.getDeclaredMethods();
                List<String> descriptions = new ArrayList<>();
                boolean hasToolMethod = false;
                for (var method : methods) {
                    if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                        hasToolMethod = true;
                        String[] vals = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class).value();
                        descriptions.addAll(java.util.Arrays.asList(vals));
                    }
                }
                if (hasToolMethod) {
                    tools.add(new ToolInfo(clazz.getSimpleName(), descriptions));
                }
            } catch (Exception ignored) {}
        }

        return Result.success(tools);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ToolInfo {
        private String name;
        private List<String> descriptions;
    }
}
