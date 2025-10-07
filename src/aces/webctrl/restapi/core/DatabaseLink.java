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
    return getNode("/trees/config/operators/operatorlist").getChildByAttribute(CoreNode.KEY, username, true);
  }
  public CoreNode createOperator(String username, String displayName, String password, boolean hashed, Boolean temporary, Boolean exempt, Set<String> roles, Set<String> groups, Container<Boolean> update) throws CoreIntegrityException, CJDataValueException, CoreNotFoundException, CoreDatabaseException {
    boolean exists = false;
    final CoreNode opList = getNode("/trees/config/operators/operatorlist");
    CoreNode operator = null;
    try{
      operator = opList.getChildByAttribute(CoreNode.KEY, username, true);
      if (!update.x){
        return null;
      }
      exists = true;
    }catch(CoreNotFoundException e){}
    update.x&=exists;
    if (!update.x){
      if (displayName==null){
        displayName = username;
      }
      if (temporary==null){
        temporary = password==null;
      }
      if (password==null){
        password = "Hvac1234!";
        hashed = false;
      }
      if (exempt==null){
        exempt = false;
      }
    }
    username = username.toLowerCase();
    HashSet<String> roleSet = null;
    HashSet<String> groupSet = null;
    if (roles!=null){
      roleSet = new HashSet<>(Math.max(1,(int)Math.ceil(roles.size()/0.75)));
      if (!roles.isEmpty()){
        final CoreNode roleList = getNode("/trees/config/roles");
        String name;
        for (CoreNode n : roleList.getChildren()){
          name = n.getReferenceName();
          if (roles.contains(name)){
            try{
              if (!n.getChild("domain_role").getBooleanAttribute(CoreNode.VALUE)){
                roleSet.add(name);
              }
            }catch(CoreNotFoundException e){}
          }
        }
      }
    }
    if (groups!=null){
      groupSet = new HashSet<>(Math.max(1,(int)Math.ceil(groups.size()/0.75)));
      if (!groups.isEmpty()){
        final CoreNode groupList = getNode("/trees/config/operator_groups");
        String name;
        for (CoreNode n : groupList.getChildren()){
          name = n.getReferenceName();
          if (groups.contains(name)){
            groupSet.add(name);
          }
        }
      }
      groupSet.remove("everybody");
    }
    if (update.x){
      if (!username.equals(operator.getAttribute(CoreNode.KEY))){
        operator.setAttribute(CoreNode.KEY, username);
      }
    }else{
      operator = getNode("/defs/core/operatorlist/operator").clone(opList, opList.makeUniqueRefName("operator"));
      operator.setAttribute(CoreNode.KEY, username);
    }
    if (password!=null){
      if (hashed){
        operator.getChild("password").setRawValueString(password);
        operator.getChild("password_changed_date").setIntAttribute(CoreNode.VALUE, (int)(System.currentTimeMillis()/1000L));
        Operator.clearLoginLockout(username);
        final CoreNode previous = operator.getChild("previous_passwords");
        if (update.x){
          final List<CoreNode> oldPasswords = previous.getSortedChildren();
          if (oldPasswords.size()>=20){
            oldPasswords.get(0).delete();
          }
        }
        previous.createNewChild().setRawValueString(password);
      }else{
        operator.getChild("password").setValueString(password);
      }
    }
    if (temporary!=null){
      operator.getChild("password_is_temporary").setBooleanAttribute(CoreNode.VALUE, temporary);
    }
    if (exempt!=null){
      operator.getChild("operator_exempt").setBooleanAttribute(CoreNode.VALUE, exempt);
    }
    if (displayName!=null){
      operator.setAttribute(NodeAttribute.lookup(CoreNode.DISPLAY_NAME, "en", true), displayName);
    }
    if (roleSet!=null){
      final CoreNode roleNode = operator.getChild("roles");
      if (update.x){
        String s;
        for (CoreNode n :roleNode.getChildren()){
          s = n.getAttribute(CoreNode.TARGET);
          if (s!=null && s.startsWith("/trees/config/roles/")){
            s = s.substring(20);
            if (!roleSet.remove(s)){
              n.delete();
            }
          }
        }
      }
      for (String r :roleSet){
        roleNode.createNewChild().setCoreNodeAttribute(CoreNode.TARGET, getNode("/trees/config/roles/"+r));
      }
    }
    if (groupSet!=null){
      final String refname = operator.getReferenceName();
      if (update.x){
        String s;
        CoreNode members;
        for (CoreNode n: getNode("/trees/config/operator_groups").getChildren()){
          s = n.getReferenceName();
          if (!s.equals("everybody") && (members=n.getChild("members")).hasChild(refname) && !groupSet.remove(s)){
            members.getChild(refname).delete();
          }
        }
      }
      for (String g: groupSet){
        getNode("/trees/config/operator_groups/"+g+"/members").createNewChild(refname).setCoreNodeAttribute(CoreNode.TARGET, operator);
      }
    }
    return operator;
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