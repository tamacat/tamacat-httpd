package org.tamacat.httpd.handler.page;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import org.thymeleaf.context.Context;

public class ThymeleafPageTest {

    @Test
    public void testGetTemplatePage() {
        Properties props = new Properties();
        ThymeleafPage page = new ThymeleafPage(props, null);
        Context context = new Context();
        Map<String, Object> httpStatus = new HashMap<>();
        httpStatus.put("statusCode", "404");
        httpStatus.put("reasonPhrase", "Not Found");
        httpStatus.put("message", "The requested URL was not found on this server.");
        context.setVariables(httpStatus);
        String template = "/error";
        
        System.out.println(page.getTemplatePage(null, null, context, template));
    }

}
