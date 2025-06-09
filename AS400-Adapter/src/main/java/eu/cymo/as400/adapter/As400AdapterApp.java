package eu.cymo.as400.adapter;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class As400AdapterApp {


    private static final Logger logger = LogManager.getLogger(As400AdapterApp.class);

    public static void main(String[] args) {
        SpringApplication.run(As400AdapterApp.class,args);
    }

}
