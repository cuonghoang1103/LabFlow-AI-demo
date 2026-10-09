package vn.swt301.labflowdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LabFlow AI demo for SWT301 (unit / integration / system testing labs).
 * NOT the LabFlow AI capstone project.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LabflowDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabflowDemoApplication.class, args);
    }
}
