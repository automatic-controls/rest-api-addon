package aces.webctrl.restapi.core;
import com.controlj.green.core.ui.*;
public class DatabaseLinkManager implements AutoCloseable {
  private DatabaseLink link = null;
  private final UserSession session;
  private final int flags;
  private final long maxSessionAge;
  private final boolean autoCommit;
  private long expiry;
  public DatabaseLinkManager(UserSession session, int flags, long maxSessionAge, boolean autoCommit){
    this.session = session;
    this.flags = flags;
    this.maxSessionAge = maxSessionAge;
    this.autoCommit = autoCommit;
  }
  public DatabaseLink getLink() throws Throwable {
    checkLink();
    if (link==null){
      link = new DatabaseLink(session, flags);
      expiry = System.currentTimeMillis()+maxSessionAge;
    }
    return link;
  }
  public void checkLink() throws Throwable {
    if (link!=null && System.currentTimeMillis()>expiry){
      if (autoCommit){
        link.commit();
      }
      link.close();
      link = null;
    }
  }
  @Override public void close(){
    if (link!=null){
      if (autoCommit){
        link.commit();
      }
      link.close();
      link = null;
    }
  }
}