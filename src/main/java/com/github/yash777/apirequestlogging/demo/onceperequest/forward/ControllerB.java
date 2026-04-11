package com.github.yash777.apirequestlogging.demo.onceperequest.forward;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h2>ControllerB — receives an internal {@code RequestDispatcher.forward()}</h2>
 *
 * <p>Handles the FORWARD dispatch initiated by {@link ControllerA}.
 * The client never sees this URL — from the client's perspective the response
 * comes from {@code /demo/forward/a}.</p>
 *
 * <h3>Standard forward attributes set by the container</h3>
 * <p>When the container performs a {@code RequestDispatcher.forward()}, it
 * sets these attributes on the request automatically:</p>
 * <table border="1" cellpadding="4">
 *   <tr><th>Attribute</th><th>Value</th></tr>
 *   <tr><td>{@code javax.servlet.forward.request_uri}</td><td>Original request URI ({@code /demo/forward/a})</td></tr>
 *   <tr><td>{@code javax.servlet.forward.context_path}</td><td>Context path</td></tr>
 *   <tr><td>{@code javax.servlet.forward.servlet_path}</td><td>Original servlet path</td></tr>
 *   <tr><td>{@code javax.servlet.forward.query_string}</td><td>Original query string</td></tr>
 * </table>
 *
 * @author Yash
 * @since 1.1.0
 * @see ControllerA
 */
@RestController
@RequestMapping("/demo/forward")
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "once-per-request",
    havingValue = "true"
)
public class ControllerB {

    private static final Logger log = LoggerFactory.getLogger(ControllerB.class);

    /**
     * Handles the forwarded request from {@link ControllerA}.
     *
     * <p>Reads both the custom attribute set by {@code ControllerA} and the
     * standard container-set forward attributes, returning them all in the
     * response body so the test can verify the forwarding worked correctly.</p>
     *
     * @param request the same request object originally received by {@code ControllerA}
     * @return map of attributes proving the forward chain succeeded
     */
    @GetMapping("/b")
    public Map<String, Object> handle(HttpServletRequest request) {

        String dispatchType  = request.getDispatcherType().name();
        String currentUri    = request.getRequestURI();
        String forwardedFrom = (String) request.getAttribute("forwardedFrom");
        String originalUri   = (String) request.getAttribute("originalUri");

        // Standard forward attributes set by the Servlet container
        String fwdUri        = (String) request.getAttribute("javax.servlet.forward.request_uri");
        String fwdContextPath= (String) request.getAttribute("javax.servlet.forward.context_path");
        String fwdServletPath= (String) request.getAttribute("javax.servlet.forward.servlet_path");

        log.info("[ControllerB] Handling FORWARD dispatch — dispatchType={} currentUri={} forwardedFrom={}",
                dispatchType, currentUri, forwardedFrom);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handledBy",           "ControllerB");
        result.put("dispatchType",        dispatchType);          // "FORWARD"
        result.put("currentUri",          currentUri);            // /demo/forward/b
        result.put("forwardedFrom",       forwardedFrom);         // "ControllerA" (our attribute)
        result.put("originalUri",         originalUri);           // /demo/forward/a (our attribute)
        result.put("fwdRequestUri",       fwdUri);               // /demo/forward/a (container attribute)
        result.put("fwdContextPath",      fwdContextPath);
        result.put("fwdServletPath",      fwdServletPath);
        result.put("note", "Client URL stayed /demo/forward/a — forwarding is server-side only");
        return result;
    }
}