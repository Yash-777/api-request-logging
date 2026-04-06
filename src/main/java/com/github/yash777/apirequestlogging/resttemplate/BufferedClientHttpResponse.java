package com.github.yash777.apirequestlogging.resttemplate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps a {@link ClientHttpResponse} whose body has already been consumed and
 * re-buffered into a {@code byte[]} so it can be read a second time.
 *
 * <p>{@code RestTemplate} reads the response body once during deserialisation.
 * The {@link RestTemplateLoggingInterceptor} buffers the body bytes via
 * {@code StreamUtils.copyToByteArray(response.getBody())}, then wraps the
 * original response in this class so the response can be returned to {@code RestTemplate}
 * while the interceptor holds a copy for logging.</p>
 *
 * @author Yash
 * @since 1.1.0
 */
public class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse original;
    private final byte[]             body;

    BufferedClientHttpResponse(ClientHttpResponse original, byte[] body) {
        this.original = original;
        this.body     = body;
    }

    @Override public HttpStatus getStatusCode()  throws IOException { return original.getStatusCode(); }
    @Override public int getRawStatusCode()       throws IOException { return original.getRawStatusCode(); }
    @Override public String getStatusText()       throws IOException { return original.getStatusText(); }
    @Override public HttpHeaders getHeaders()                        { return original.getHeaders(); }
    @Override public InputStream getBody()                           { return new ByteArrayInputStream(body); }
    @Override public void close()                                    { original.close(); }
}
