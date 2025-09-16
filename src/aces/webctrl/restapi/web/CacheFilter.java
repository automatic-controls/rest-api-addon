package aces.webctrl.restapi.web;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
public class CacheFilter extends HttpFilter {
  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
    res.setHeader("Cache-Control", "no-cache");
    chain.doFilter(req, res);
  }
}