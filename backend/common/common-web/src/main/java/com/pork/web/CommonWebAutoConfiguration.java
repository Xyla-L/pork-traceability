package com.pork.web;

import com.pork.web.config.MybatisPlusConfig;
import com.pork.web.config.JacksonConfig;
import com.pork.web.config.SourcePolicyProperties;
import com.pork.web.config.TimezoneConfig;
import com.pork.web.config.WebMvcConfig;
import com.pork.web.handler.GlobalExceptionHandler;
import com.pork.web.filter.TraceIdFilter;
import com.pork.web.interceptor.OperationAuditInterceptor;
import com.pork.web.source.SourceGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(SourcePolicyProperties.class)
@Import({TimezoneConfig.class, WebMvcConfig.class, MybatisPlusConfig.class, JacksonConfig.class, GlobalExceptionHandler.class,
        TraceIdFilter.class, OperationAuditInterceptor.class, SourceGuard.class})
public class CommonWebAutoConfiguration { }
