package com.hong.other;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by derek on 2017/2/7.
 */
public class HelloSlf4j {

    private static final Logger logger = LoggerFactory.getLogger(HelloSlf4j.class);

    public static void main(String[] args) {
        System.out.println(111);
        logger.debug("This is debug message");
        logger.info("This is info message");
        logger.warn("This is warn message");
        logger.error("This is error message");
        System.out.println(222);
    }

    @Test
    public void test(){
        System.out.println(333);
        logger.info("test info");
        System.out.println(444);
    }
}
