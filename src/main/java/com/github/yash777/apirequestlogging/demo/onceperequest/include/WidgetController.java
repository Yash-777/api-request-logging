package com.github.yash777.apirequestlogging.demo.onceperequest.include;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

/**
 * <h2>WidgetController — included fragment for {@link DashboardController}</h2>
 *
 * <p>Writes a partial JSON fragment into the response during an INCLUDE dispatch.
 * It does NOT set the response content type or status — those are owned by
 * {@link DashboardController} (the including resource).</p>
 *
 * <h3>Standard include attributes set by the container</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>Attribute</th><th>Value</th></tr>
 *   <tr><td>{@code javax.servlet.include.request_uri}</td><td>{@code /demo/include/widget}</td></tr>
 *   <tr><td>{@code javax.servlet.include.servlet_path}</td><td>{@code /demo/include/widget}</td></tr>
 *   <tr><td>{@code javax.servlet.include.context_path}</td><td>context path</td></tr>
 * </table>
 *
 * @author Yash
 * @since 1.1.0
 * @see DashboardController
 */
@RestController
@RequestMapping("/demo/include")
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "once-per-request",
    havingValue = "true"
)
public class WidgetController {

    private static final Logger log = LoggerFactory.getLogger(WidgetController.class);

    /**
     * Writes a JSON widget fragment into the shared response writer.
     *
     * <p>Can also be called directly as a standalone endpoint to verify it
     * works independently of the include mechanism.</p>
     *
     * @param request  used to read the include dispatch attribute
     * @param response used to obtain the shared {@link PrintWriter}
     * @throws IOException if the writer fails
     */
    @GetMapping("/widget")
    public void widget(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String dispatchType = request.getDispatcherType().name();
        String includeUri   = (String) request.getAttribute("javax.servlet.include.request_uri");

        log.info("[WidgetController] dispatch={} includeUri={}", dispatchType, includeUri);

        PrintWriter writer = response.getWriter();
        writer.write("{");
        writer.write("\"type\":\"widget\",");
        writer.write("\"dispatchType\":\"" + dispatchType + "\",");
        writer.write("\"generatedAt\":\"" + LocalDateTime.now() + "\",");
        writer.write("\"data\":{\"metric\":\"requests\",\"value\":42}");
        writer.write("}");
        writer.flush();
    }
}