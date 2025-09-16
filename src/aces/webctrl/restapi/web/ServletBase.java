package aces.webctrl.restapi.web;
import aces.webctrl.restapi.core.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
/**
 * This is used as a base class for most servlets.
 * Thrown errors and HTML resources are handled automatically.
 */
public abstract class ServletBase extends HttpServlet {
  private volatile String html = null;
  /**
   * This is the primary method which subclasses will want to override.
   * When a GET or POST request is made, this method will be invoked.
   */
  public abstract void exec(HttpServletRequest req, HttpServletResponse res) throws Throwable;
  /**
   * This method specifies that GET requests are handled identically to POST requests.
   */
  @Override public void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req,res);
  }
  /**
   * Used to check if the current user has the required role to access this servlet.
   */
  public boolean checkRole(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    boolean ret;
    if (!(ret = req.isUserInRole("view_administrator_only"))){
      res.sendError(403, "You do not have the required permissions to view this page.");
    }
    return ret;
  }
  /**
   * This is the primary method wrapping our overridden {@code exec} method.
   */
  @Override public void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    try{
      req.setCharacterEncoding("UTF-8");
      res.setCharacterEncoding("UTF-8");
      res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      if (Initializer.stop){
        res.sendError(404, "Add-on is shutting down.");
      }else if (checkRole(req,res)){
        exec(req,res);
      }
    }catch(NumberFormatException e){
      Initializer.log(e);
      res.sendError(400, "Failed to parse number from string.");
    }catch(Throwable t){
      Initializer.log(t);
      if (!res.isCommitted()){
        res.reset();
        res.setCharacterEncoding("UTF-8");
        res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setContentType("text/plain");
        res.setStatus(500);
        t.printStackTrace(res.getWriter());
      }
    }
  }
  /**
   * After initialially loading the HTML file, the data remains in RAM for efficient use in the future.
   * @return a String containing the HTML file corresponding to the front-end GUI for this servlet.
   */
  public String getHTML(final HttpServletRequest req) throws Throwable {
    if (html==null){
      html = Utility.loadResourceAsString("aces/webctrl/restapi/resources/"+getClass().getSimpleName()+".html")
      .replaceAll("href=\"\\.\\./\\.\\./\\.\\./\\.\\./\\.\\./root/webapp/([^\"]+)\"", "href=\"./$1\"");
    }
    return html.replace("__PREFIX__", req.getContextPath());
  }
}