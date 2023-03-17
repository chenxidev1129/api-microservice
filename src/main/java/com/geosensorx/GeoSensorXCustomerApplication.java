package com.geosensorx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Arrays;

@Slf4j
@SpringBootApplication
@EnableSwagger2
public class GeoSensorXCustomerApplication {

    private static final String SPRING_CONFIG_NAME_KEY = "--spring.config.name";
    private static final String DEFAULT_SPRING_CONFIG_PARAM = SPRING_CONFIG_NAME_KEY + "=" + "geosensorx-custom-tb-service";

    public static void main(String[] args) {
        try {
            SpringApplication.run(GeoSensorXCustomerApplication.class, updateArguments(args));
        } catch (Exception e) {
            log.error("Unexpected error during GeoSensorX Custom TB Application execution:", e);
            System.exit(1);
        }
    }

    private static String[] updateArguments(String[] args) {
        if (Arrays.stream(args).noneMatch(arg -> arg.startsWith(SPRING_CONFIG_NAME_KEY))) {
            String[] modifiedArgs = new String[args.length + 1];
            System.arraycopy(args, 0, modifiedArgs, 0, args.length);
            modifiedArgs[args.length] = DEFAULT_SPRING_CONFIG_PARAM;
            return modifiedArgs;
        }
        return args;
    }
}