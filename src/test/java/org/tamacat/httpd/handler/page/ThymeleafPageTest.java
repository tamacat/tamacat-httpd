package org.tamacat.httpd.handler.page;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    /**
     * Thymeleaf evaluates simple variable references through its own OGNL shortcut,
     * which never touches OGNL's API, so templates like error.html keep rendering
     * even with an OGNL version Thymeleaf is not binary-compatible with. Each
     * expression below is evaluated by OGNL itself (Thymeleaf builds an OgnlContext):
     * th:href restricts variable access, which disables the shortcut, and an
     * expression object ('#') or a method call cannot be shortcut at all.
     * With ognl 3.4.x under Thymeleaf 3.1.x this fails with NoSuchMethodError on
     * OgnlContext.<init>(MemberAccess, ClassResolver, TypeConverter, Map).
     */
    @Test
    public void testExpressionsEvaluatedByOgnl(@TempDir Path docsRoot) throws Exception {
        Files.write(docsRoot.resolve("ognl.html"), (
            "<a th:href=\"${contextRoot}+'/index.html'\">Home</a>"
            + "<p id=\"upper\" th:text=\"${#strings.toUpperCase(name)}\">x</p>"
            + "<p id=\"length\" th:text=\"${name.length()}\">x</p>").getBytes(StandardCharsets.UTF_8));
        ThymeleafPage page = new ThymeleafPage(new Properties(), docsRoot.toString() + "/");
        Context context = new Context();
        context.setVariable("contextRoot", "/app");
        context.setVariable("name", "tamacat");

        String html = page.getTemplatePage(null, null, context, "ognl");

        assertTrue(html.contains("<a href=\"/app/index.html\">Home</a>"), html);
        assertTrue(html.contains("<p id=\"upper\">TAMACAT</p>"), html);
        assertTrue(html.contains("<p id=\"length\">7</p>"), html);
    }
}
