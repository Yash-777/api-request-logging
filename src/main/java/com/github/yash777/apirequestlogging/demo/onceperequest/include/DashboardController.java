package com.github.yash777.apirequestlogging.demo.onceperequest.include;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * <h2>DashboardController — demonstrates {@code RequestDispatcher.include()}</h2>
 *
 * <p>Unlike {@code forward()}, {@code include()} lets the <em>including</em>
 * controller write part of the response, then includes content from another
 * resource, then optionally writes more — all within one request.</p>
 *
 * <h3>Key difference: forward vs include</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th></th><th>{@code forward()}</th><th>{@code include()}</th></tr>
 *   <tr><td>Who writes the response</td><td>Target only</td><td>Both caller and target</td></tr>
 *   <tr><td>Can caller write after?</td><td>No (response committed)</td><td>Yes</td></tr>
 *   <tr><td>Response headers</td><td>Target controls</td><td>Caller controls</td></tr>
 *   <tr><td>Typical use</td><td>Full page hand-off</td><td>Partial fragment inclusion</td></tr>
 * </table>
 *
 * <h3>Dispatch flow</h3>
 * <pre>
 * Client GET /demo/include/dashboard
 *   │
 *   └─► DashboardController (REQUEST dispatch)
 *         write "[DASHBOARD START]"
 *         RequestDispatcher.include() → WidgetController (INCLUDE dispatch)
 *           SpringOncePerRequestFilter → SKIPPED ❌
 *           ServletOncePerRequestFilter → runs AGAIN ✅
 *           WidgetController writes "WIDGET CONTENT"
 *         write "[DASHBOARD END]"
 *         └─► combined response sent to client
 * </pre>
 *
 * <h3>ApiLoggingFilter behaviour</h3>
 * <p>Runs once.  The response body captured is the <em>combined</em> output
 * of both controllers (DASHBOARD START + WIDGET CONTENT + DASHBOARD END),
 * because {@code ContentCachingResponseWrapper} buffers everything written
 * during the entire request lifecycle.</p>
 *
 * <h3>Activation</h3>
 * <pre>
 * internal.app.non-consumer.once-per-request=true
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see WidgetController
 */
@RestController
@RequestMapping("/demo/include")
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "once-per-request",
    havingValue = "true"
)
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    /**
     * Builds a "dashboard" response by composing its own content with an
     * included fragment from {@link WidgetController}.
     *
     * <p>The response content type is set to {@code application/json} so the
     * combined output is parseable, and the three sections are written
     * sequentially to the same {@link PrintWriter}.</p>
     *
     * @param request  used to obtain the {@link RequestDispatcher}
     * @param response used to write the dashboard's own content sections
     * @throws ServletException propagated from {@code include()}
     * @throws IOException      propagated from {@code include()} or writer
     */
    @GetMapping("/dashboard")
    public void dashboard(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        log.info("[DashboardController] REQUEST dispatch — building dashboard with included widget");

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        // ── Section 1: Dashboard header ───────────────────────────────
        writer.write("{\"dashboard\":{");
        writer.write("\"title\":\"Demo Dashboard\",");
        writer.write("\"dispatchType\":\"" + request.getDispatcherType().name() + "\",");
        writer.flush();

        // ── Section 2: Include widget fragment ────────────────────────
        // The included resource writes directly into this same response writer.
        // Control is transferred to WidgetController, which writes "widget":{...},
        // then returns here.
        writer.write("\"widget\":");
        writer.flush();

        RequestDispatcher dispatcher = request.getRequestDispatcher("/demo/include/widget");
        dispatcher.include(request, response);  // WidgetController writes here

        // ── Section 3: Dashboard footer (written after include returns) ──
        writer.write(",\"status\":\"composed\"");
        writer.write("}}");
        writer.flush();

        log.info("[DashboardController] Finished — response includes both dashboard and widget content");
    }
}