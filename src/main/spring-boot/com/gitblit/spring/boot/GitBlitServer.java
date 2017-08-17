package com.gitblit.spring.boot;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.gitblit.Constants;
import com.gitblit.spring.boot.configure.HotPatch;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.gitblit.spring.boot.configure")
public class GitBlitServer {
    static HotPatch p = new HotPatch();
    private static Logger logger = LogManager.getLogger(GitBlitServer.class);

    public static void main(String[] args) {
        if ("UTF-8".equalsIgnoreCase(System.getProperty("file.encoding"))) {
            logger.info("file.encoding check done!");
        } else {
            logger.error("file.encoding not utf-8 ! set -Dfile.encoding=UTF-8");
            System.exit(1);
        }
        SpringApplication application = new SpringApplication(GitBlitServer.class);
        application.setAddCommandLineProperties(true);
        application.setBanner((Environment environment, Class<?> sourceClass, PrintStream out)->{
            try {
                out.write(Constants.getASCIIArt().getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        application.run(args);
    }

}
