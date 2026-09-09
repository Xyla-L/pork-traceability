package com.pork.web;

import com.pork.web.config.MybatisPlusConfig;
import com.pork.web.config.JacksonConfig;
import com.pork.web.config.WebMvcConfig;
import com.pork.web.handler.GlobalExceptionHandler;
import com.pork.web.filter.TraceIdFilter;
import com.pork.web.interceptor.OperationAuditInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({WebMvcConfig.class, MybatisPlusConfig.class, JacksonConfig.class, GlobalExceptionHandler.class,
        TraceIdFilter.class, OperationAuditInterceptor.class})
public class CommonWebAutoConfiguration { }
