package aces.webctrl.restapi.api;
import aces.webctrl.restapi.core.*;
import javax.servlet.http.*;
import com.controlj.green.core.ui.*;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.impl.*;
import com.controlj.green.addonsupport.access.aspect.impl.*;
public class ApiResponse implements AutoCloseable {
  private volatile static DirectAccessImpl directAccess = null;
  private volatile static SystemConnection rootConnection = null;
  public volatile int status = 200;
  private volatile HttpServletRequest req;
  private volatile boolean loggedIn;
  private volatile boolean admin;
  private volatile UserSession wbs = null;
  private volatile SystemConnection sysCon = null;
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
  public DatabaseLinkManager createLinkManager(int flags, long maxSessionAge, boolean autoCommit) throws Throwable {
    return new DatabaseLinkManager(getSession(), flags, maxSessionAge, autoCommit);
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
  public SystemConnection getSystemConnection() throws Throwable {
    if (sysCon==null){
      if (directAccess==null){
        directAccess = (DirectAccessImpl)DirectAccess.getDirectAccess();
      }
      if (operator==null){
        if (rootConnection==null){
          rootConnection = directAccess.getRootSystemConnection();
        }
        sysCon = rootConnection;
      }else{
        sysCon = directAccess.getUserSystemConnection(operator);
      }
    }
    return sysCon;
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