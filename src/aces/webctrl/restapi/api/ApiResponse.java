package aces.webctrl.restapi.api;
import aces.webctrl.restapi.core.*;
import javax.servlet.http.*;
import com.controlj.green.core.ui.*;
import com.controlj.green.addonsupport.access.impl.*;
public class ApiResponse implements AutoCloseable {
  public volatile int status = 200;
  private volatile HttpServletRequest req;
  private volatile boolean loggedIn;
  private volatile boolean admin;
  private volatile UserSession wbs = null;
  private volatile com.controlj.green.core.data.Operator operator = null;
  public ApiResponse(HttpServletRequest req, boolean loggedIn, com.controlj.green.core.data.Operator operator) throws Throwable {
    this.req = req;
    this.loggedIn = loggedIn;
    this.operator = operator;
    this.admin = operator==null || operator.isAdministrator();
  }
  public DatabaseLink createLink(int flags) throws Throwable {
    return new DatabaseLink(getSession(), flags);
  }
  public UserSession getSession() throws Throwable {
    if (wbs==null){
      if (operator==null){
        wbs = new AddOnUserSession(com.controlj.green.core.data.Operator.getRootOperator(), req.getRemoteAddr());
      }else{
        wbs = new AddOnUserSession(operator, req.getRemoteAddr());
      }
    }
    return wbs;
  }
  public HttpServletRequest getRequest(){
    return req;
  }
  public boolean isLoggedIn(){
    return loggedIn;
  }
  public boolean isAdmin(){
    return admin;
  }
  public com.controlj.green.core.data.Operator getOperator(){
    return operator;
  }
  @Override public void close(){
    if (wbs!=null){
      try{
        wbs.close();
      }catch(Throwable t){}
      wbs = null;
    }
  }
}