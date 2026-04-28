package com.github.yash777.apirequestlogging.aop;

/**
 * Controls the verbosity of the {@code handler=[...]} field emitted by
 * {@link ControllerHandlerAspect}.
 *
 * <p>The four levels form a strict superset chain — each one adds information
 * to the previous:</p>
 *
 * <table border="1" cellpadding="4">
 *   <caption>Format levels</caption>
 *   <tr><th>Level</th><th>Example output</th></tr>
 *   <tr><td>{@link #SIMPLE}</td>
 *       <td>{@code OrderController#createOrder}</td></tr>
 *   <tr><td>{@link #WITH_PARAMS}</td>
 *       <td>{@code OrderController#createOrder(OrderRequest)}</td></tr>
 *   <tr><td>{@link #QUALIFIED}</td>
 *       <td>{@code com.github.yash777.demo.controller.OrderController#createOrder}</td></tr>
 *   <tr><td>{@link #FULL}</td>
 *       <td>{@code com.github.yash777.demo.controller.OrderController#createOrder(com.github.yash777.demo.dto.OrderRequest)}</td></tr>
 * </table>
 *
 * <p>The notation itself ({@code Class#method}) matches Spring's own
 * {@code HandlerMethod.toString()} and the JavaDoc {@code {@link}} reference
 * style, making the log lines familiar to any Java/Spring developer.</p>
 *
 * @author Yash
 * @since 1.2.0
 */
public enum HandlerFormat {

    /**
     * Simple class name and method name only.
     * <p>Example: {@code OrderController#createOrder}</p>
     * <p>Best for most cases — readable, grep-friendly, low-noise.</p>
     */
    SIMPLE,

    /**
     * Simple class name, method name, and simple parameter type names.
     * <p>Example: {@code OrderController#createOrder(OrderRequest)}</p>
     * <p>Use when you have overloaded controller methods that share a name.</p>
     */
    WITH_PARAMS,

    /**
     * Fully-qualified class name and method name; no parameter types.
     * <p>Example: {@code com.github.yash777.demo.controller.OrderController#createOrder}</p>
     * <p>Use when controllers in different packages share simple names.</p>
     */
    QUALIFIED,

    /**
     * Fully-qualified class name, method name, and fully-qualified parameter
     * type names — a complete handler signature suitable for static analysis
     * and distributed-tracing pipelines.
     * <p>Example:
     * {@code com.github.yash777.demo.controller.OrderController#createOrder(com.github.yash777.demo.dto.OrderRequest)}</p>
     */
    FULL
}