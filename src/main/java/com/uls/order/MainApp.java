package com.uls.order;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.main.Main;

public class MainApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();

        main.configure().addRoutesBuilder(new OrderProcessorRoute());
        main.configure().addRoutesBuilder(new StatusReportRoute());
        main.configure().addConfiguration(new CamelConfiguration() {
            @Override
            public void configure(CamelContext camelContext) {
                camelContext.getCamelContextExtension().addInterceptStrategy(new ObservabilityInterceptStrategy());
            }
        });

        main.run(args);
    }
}
