package com.uls.order;

import org.apache.camel.main.Main;

public class MainApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        
        main.configure().addRoutesBuilder(new OrderProcessorRoute());
        main.configure().addRoutesBuilder(new StatusReportRoute());

        main.run(args);
    }
}
