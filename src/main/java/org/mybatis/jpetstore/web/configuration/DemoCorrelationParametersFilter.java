/*
 *    Copyright 2010-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.jpetstore.web.configuration;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * OctoPerf demo only: injects the hidden {@code _sourcePage} and {@code __fp} fields that Stripes used to add to every
 * form, with a fresh random value on each response. The server ignores them; they exist so load-testing demos can
 * showcase correlation rules (extract from the previous response, inject in the next request).
 */
// Runs on the forward to the JSP view: the container closes the response once the forward returns, so the
// rewritten body must be written from within it.
@WebFilter(urlPatterns = "/WEB-INF/jsp/*", dispatcherTypes = DispatcherType.FORWARD)
public class DemoCorrelationParametersFilter extends HttpFilter {

  private static final long serialVersionUID = 1L;

  private static final Pattern FORM_END = Pattern.compile("</form>", Pattern.CASE_INSENSITIVE);

  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    BufferedResponse buffered = new BufferedResponse(response);
    chain.doFilter(request, buffered);

    String contentType = response.getContentType();
    if (contentType == null || !contentType.startsWith("text/html")) {
      buffered.writeTo(response);
      return;
    }

    String html = injectHiddenFields(buffered.getContent());
    Charset charset = Charset.forName(response.getCharacterEncoding());
    byte[] body = html.getBytes(charset);
    response.setContentLength(body.length);
    response.getOutputStream().write(body);
  }

  static String injectHiddenFields(String html) {
    Matcher matcher = FORM_END.matcher(html);
    StringBuffer result = new StringBuffer(html.length() + 512);
    while (matcher.find()) {
      String fields = "<div style=\"display: none;\"><input type=\"hidden\" name=\"_sourcePage\" value=\""
          + randomToken(48) + "\" /><input type=\"hidden\" name=\"__fp\" value=\"" + randomToken(36) + "\" /></div>";
      matcher.appendReplacement(result, Matcher.quoteReplacement(fields + matcher.group()));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static String randomToken(int bytes) {
    byte[] token = new byte[bytes];
    RANDOM.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }

  /**
   * Captures the whole response body so it can be rewritten once the view has been rendered.
   */
  private static class BufferedResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private CharArrayWriter chars;
    private PrintWriter writer;
    private ServletOutputStream outputStream;

    BufferedResponse(HttpServletResponse response) {
      super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
      if (outputStream == null) {
        outputStream = new ServletOutputStream() {
          @Override
          public void write(int b) {
            bytes.write(b);
          }

          @Override
          public void write(byte[] b, int off, int len) {
            bytes.write(b, off, len);
          }

          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(WriteListener listener) {
            throw new UnsupportedOperationException();
          }
        };
      }
      return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
      if (writer == null) {
        chars = new CharArrayWriter();
        writer = new PrintWriter(chars);
      }
      return writer;
    }

    @Override
    public void flushBuffer() {
      if (writer != null) {
        writer.flush();
      }
    }

    @Override
    public void setContentLength(int len) {
      // Length is recomputed once the body has been rewritten
    }

    @Override
    public void setContentLengthLong(long len) {
      // Length is recomputed once the body has been rewritten
    }

    @Override
    public void resetBuffer() {
      bytes.reset();
      if (chars != null) {
        writer.flush();
        chars.reset();
      }
    }

    @Override
    public void reset() {
      super.reset();
      resetBuffer();
    }

    String getContent() {
      Charset charset = Charset.forName(getCharacterEncoding());
      StringBuilder content = new StringBuilder(new String(bytes.toByteArray(), charset));
      if (chars != null) {
        writer.flush();
        content.append(chars.toCharArray());
      }
      return content.toString();
    }

    void writeTo(HttpServletResponse response) throws IOException {
      if (chars != null) {
        writer.flush();
        try (OutputStreamWriter out = new OutputStreamWriter(bytes,
            getCharacterEncoding() == null ? StandardCharsets.ISO_8859_1 : Charset.forName(getCharacterEncoding()))) {
          out.write(chars.toCharArray());
        }
      }
      if (bytes.size() > 0) {
        response.setContentLength(bytes.size());
        response.getOutputStream().write(bytes.toByteArray());
      }
    }
  }
}
