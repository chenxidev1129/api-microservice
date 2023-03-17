package com.geosensorx.config;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.schema.AlternateTypeRule;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import static com.google.common.collect.Lists.newArrayList;
import static springfox.documentation.builders.PathSelectors.regex;

@Configuration
public class SwaggerConfiguration {

    @Value("${swagger.api_path_regex}")
    private String apiPathRegex;
    @Value("${swagger.title}")
    private String title;
    @Value("${swagger.description}")
    private String description;
    @Value("${swagger.contact.name}")
    private String contactName;
    @Value("${swagger.contact.url}")
    private String contactUrl;
    @Value("${swagger.contact.email}")
    private String contactEmail;
    @Value("${swagger.license.title}")
    private String licenseTitle;
    @Value("${swagger.license.url}")
    private String licenseUrl;
    @Value("${swagger.version}")
    private String version;

    @Bean
    public Docket thingsboardApi() {
        TypeResolver typeResolver = new TypeResolver();
        final ResolvedType jsonNodeType =
                typeResolver.resolve(
                        JsonNode.class);
        final ResolvedType stringType =
                typeResolver.resolve(
                        String.class);

        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("geosensorx")
                .apiInfo(apiInfo())
                .alternateTypeRules(
                        new AlternateTypeRule(
                                jsonNodeType,
                                stringType))
                .select()
                .paths(apiPaths())
                .build()
                .securitySchemes(newArrayList(jwtTokenKey()))
//                .securityContexts(newArrayList(securityContext()))
                .enableUrlTemplating(true);
    }

    private ApiKey jwtTokenKey() {
        return new ApiKey("X-Authorization", "X-Authorization", "header");
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title(title)
                .description(description)
                .contact(new Contact(contactName, contactUrl, contactEmail))
                .license(licenseTitle)
                .licenseUrl(licenseUrl)
                .version(version)
                .build();
    }

    private Predicate<String> apiPaths() {
        return regex(apiPathRegex);
    }

}
