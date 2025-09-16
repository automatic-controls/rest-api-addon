package aces.webctrl.restapi.core;
import java.util.*;
import com.controlj.green.core.data.*;
import com.controlj.green.common.*;
import com.controlj.green.core.ui.*;
/**
 * Utility class meant to facilitate access to WebCTRL's internal operator API.
 */
public class DatabaseLink implements AutoCloseable {
  /** Controls the connection to the underlying database. */
  private volatile CoreDataSession cds;
  /** Used to cache CoreNodes. */
  private volatile HashMap<String,CoreNode> nodeMap = new HashMap<String,CoreNode>();
  /** Specifies whether modifications can be made to the underlying database. */
  private volatile boolean readOnly;

  public DatabaseLink(boolean readOnly) throws CoreDatabaseException {
    this.readOnly = readOnly;
    cds = CoreDataSession.open(readOnly?0:1);
  }
  public DatabaseLink(UserSession session, boolean readOnly) throws CoreDatabaseException {
    this.readOnly = readOnly;
    cds = CoreDataSession.open(session, readOnly?0:1);
  }
  public DatabaseLink(int flags) throws CoreDatabaseException {
    this.readOnly = (flags&1)==0;
    cds = CoreDataSession.open(flags);
  }
  public DatabaseLink(UserSession session, int flags) throws CoreDatabaseException {
    this.readOnly = (flags&1)==0;
    cds = CoreDataSession.open(session, flags);
  }
  
  /**
   * @return whether the underlying database connection is read-only.
   */
  public boolean isReadOnly(){
    return readOnly;
  }
  /**
   * @return the CoreNode for the operator with the given username.
   */
  public CoreNode getOperator(String username) throws CoreIntegrityException, CoreNotFoundException {
    return getNode("/trees/config/operators/operatorlist").getChildByAttribute(CoreNode.KEY, username.toLowerCase(), true);
  }
  /**
   * Creates a new administrative operator with the given username, displayName, and password.
   * If an operator of the same username already exists, it is overwritten.
   */
  public CoreNode createOperator(String username, String displayName, String password, boolean rawPassword, boolean temporary, boolean exempt) throws CoreIntegrityException, CJDataValueException, CoreNotFoundException, CoreDatabaseException {
    username = username.toLowerCase();
    final CoreNode opList = getNode("/trees/config/operators/operatorlist");
    int sort = -1;
    if (opList.hasChildByAttribute(CoreNode.KEY, username)){
      final CoreNode op = getOperator(username);
      sort = op.getSort();
      op.delete();
    }
    final CoreNode operator = getNode("/defs/core/operatorlist/operator").clone(opList, username.equals("administrator")?username:opList.makeUniqueRefName("operator"));
    if (sort!=-1){
      operator.setSort(sort);
    }
    operator.setAttribute(CoreNode.KEY, username);
    if (rawPassword){
      setRawPassword(operator, password, temporary, exempt);
    }else{
      setPassword(operator, password, temporary, exempt);
    }
    operator.getChild("roles").createNewChild().setCoreNodeAttribute(CoreNode.TARGET, getNode("/trees/config/roles/administrator"));
    operator.setAttribute(NodeAttribute.lookup(CoreNode.DISPLAY_NAME, "en", true), displayName);
    return operator;
  }
  /**
   * Sets the password of the given operator.
   */
  public void setPassword(CoreNode operator, String password, boolean temporary, boolean exempt) throws CoreNotFoundException {
    operator.getChild("password").setValueString(password);
    operator.getChild("password_is_temporary").setBooleanAttribute(CoreNode.VALUE, temporary);
    operator.getChild("operator_exempt").setBooleanAttribute(CoreNode.VALUE, exempt);
  }
  /**
   * Sets the raw digested password of the given operator.
   */
  public void setRawPassword(CoreNode operator, String digest, boolean temporary, boolean exempt) throws CoreNotFoundException, CoreDatabaseException {
    operator.getChild("password_is_temporary").setBooleanAttribute(CoreNode.VALUE, temporary);
    operator.getChild("operator_exempt").setBooleanAttribute(CoreNode.VALUE, exempt);
    final CoreNode passwordNode = operator.getChild("password");
    final String oldDigest = passwordNode.getValueString();
    if (!digest.equals(oldDigest)){
      passwordNode.setRawValueString(digest);
      operator.getChild("password_changed_date").setIntAttribute(CoreNode.VALUE, (int)(System.currentTimeMillis()/1000L));
      Operator.clearLoginLockout(operator.getAttribute(CoreNode.KEY));
      final CoreNode previous = operator.getChild("previous_passwords");
      final List<CoreNode> oldPasswords = previous.getSortedChildren();
      if (oldPasswords.size()>=20){
        oldPasswords.get(0).delete();
      }
      previous.createNewChild().setRawValueString(digest);
    }
  }
  /**
   * @return the CoreNode corresponding to the given absolute path.
   */
  public CoreNode getNode(String path) throws CoreIntegrityException {
    CoreNode n = nodeMap.get(path);
    if (n==null){
      n = cds.getExpectedNode(path);
      nodeMap.put(path,n);
    }
    return n;
  }
  /**
   * @return the CoreNode corresponding to the given DBID.
   */
  public CoreNode getNode(long dbid) throws CoreNotFoundException {
    return cds.getNode(dbid);
  }
  /**
   * @return the CoreDataSession used by this link.
   */
  public CoreDataSession getCoreDataSession(){
    return cds;
  }
  /**
   * Commits changes to the underlying database.
   */
  public void commit(){
    cds.commit();
  }
  /**
   * Closes the CoreDataSession associated with this Object.
   */
  @Override public void close(){
    cds.close();
  }
}