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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The Class DemoCorrelationParametersFilterTest.
 */
class DemoCorrelationParametersFilterTest {

  private static final Pattern SOURCE_PAGE = Pattern.compile("name=\"_sourcePage\" value=\"([A-Za-z0-9_-]+)\"");

  @Test
  void injectsHiddenFieldsInEveryForm() {
    String html = DemoCorrelationParametersFilter
        .injectHiddenFields("<form action=\"/a\"><input name=\"x\"/></form><p>$1</p><FORM></FORM>");

    assertThat(html).startsWith("<form action=\"/a\"><input name=\"x\"/><div style=\"display: none;\">");
    assertThat(html).contains("<p>$1</p>");
    assertThat(countMatches(SOURCE_PAGE, html)).isEqualTo(2);
    assertThat(countMatches(Pattern.compile("name=\"__fp\" value=\"[A-Za-z0-9_-]+\""), html)).isEqualTo(2);
  }

  @Test
  void generatesFreshValuesOnEachResponse() {
    String html = "<form></form>";

    assertThat(sourcePage(DemoCorrelationParametersFilter.injectHiddenFields(html)))
        .isNotEqualTo(sourcePage(DemoCorrelationParametersFilter.injectHiddenFields(html)));
  }

  @Test
  void rewritesHtmlRenderedThroughTheWriter() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new DemoCorrelationParametersFilter().doFilter(new MockHttpServletRequest(), response,
        new MockFilterChain(new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void service(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
            resp.setContentType("text/html;charset=UTF-8");
            resp.getWriter().write("<form>é</form>");
          }
        }));

    String body = response.getContentAsString();
    assertThat(body).startsWith("<form>é<div").contains("_sourcePage").contains("__fp");
    assertThat(response.getContentLength()).isEqualTo(body.getBytes("UTF-8").length);
  }

  @Test
  void leavesNonHtmlResponsesUntouched() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new DemoCorrelationParametersFilter().doFilter(new MockHttpServletRequest(), response,
        new MockFilterChain(new HttpServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void service(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
            resp.setContentType("application/json");
            resp.getOutputStream().write("{\"form\":\"</form>\"}".getBytes("UTF-8"));
          }
        }));

    assertThat(response.getContentAsString()).isEqualTo("{\"form\":\"</form>\"}");
  }

  private static String sourcePage(String html) {
    Matcher matcher = SOURCE_PAGE.matcher(html);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static int countMatches(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
