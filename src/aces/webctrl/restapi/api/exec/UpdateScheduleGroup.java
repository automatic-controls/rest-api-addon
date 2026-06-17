package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.core.*;
import aces.webctrl.restapi.api.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
import com.controlj.green.core.sched.*;
import com.controlj.green.core.download.api.TaskSet;
import com.controlj.green.core.download.api.DownloadAction;
import com.controlj.green.core.download.impl.Downloader;
import com.controlj.green.common.RefName;
public class UpdateScheduleGroup extends ApiBase {
  @Override public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    final JSONObject ret = new JSONObject();
    final long contextDBID = input.getLongValue("contextDBID", 0L);
    final String groupName = input.getString("group");
    final boolean download = input.getBooleanValue("download", false);
    final String action = input.getString("action", "READ").toUpperCase();
    JSONArray memberArr = input.getJSONArray("members");
    final int originalSize = memberArr!=null?memberArr.size():0;
    HashSet<String> memberGQLs = new HashSet<>();
    final HashSet<Long> members = new HashSet<>();
    if (memberArr != null) {
      for (Object mem : memberArr) {
        if (mem instanceof Number) {
          members.add(((Number) mem).longValue());
        } else if (mem instanceof String) {
          final String s = ((String) mem).trim();
          if (ResolveGQL.longPattern.matcher(s).matches()){
            try{
              members.add(Long.parseLong(s));
            }catch(NumberFormatException e){
              memberGQLs.add(s);
            }
          }else{
            memberGQLs.add(s);
          }
        }
      }
      memberArr = null;
    }
    long groupDBID = 0L;
    try(
      DatabaseLink link = res.createLink(0);
    ){
      CoreNode ctx,n;
      if (contextDBID!=0L){
        ctx = link.getNode(contextDBID);
      }else{
        ctx = link.getNode("/trees/geographic");
      }
      final Iterator<Long> it = members.iterator();
      long dbid;
      short type;
      while (it.hasNext()){
        dbid = it.next();
        n = link.getNode(dbid);
        if (!res.isAdmin() && !(n.hasViewPriv() && n.hasFuncPriv(202))){
          it.remove();
        }else{
          type = n.getNodeType();
          if (type!=NodeType.AREA && type!=NodeType.BEQU && type!=NodeType.GROUP && type!=NodeType.HSGROUP){
            it.remove();
          }
        }
      }
      if (res.isAdmin() || ctx.hasViewPriv()){
        for (String g: memberGQLs){
          n = ResolveGQL.evalToNode(ctx, g, !res.isAdmin(), false);
          if (n!=null && (res.isAdmin() || n.hasFuncPriv(202))){
            type = n.getNodeType();
            if (type==NodeType.AREA || type==NodeType.BEQU || type==NodeType.GROUP || type==NodeType.HSGROUP){
              members.add(n.getDbid());
            }
          }
        }
      }
      memberGQLs = null;
      final CoreNode grp = getScheduleGroup(link, groupName);
      if (grp!=null && (res.isAdmin() || (grp.hasViewPriv() && grp.hasFuncPriv(202)))){
        type = grp.getNodeType();
        if (type==NodeType.GROUP || type==NodeType.HSGROUP){
          groupDBID = grp.getDbid();
        }
      }
    }
    final int resolvedSize = members.size();
    final JSONArray errors = new JSONArray();
    if (resolvedSize!=originalSize){
      errors.add("Some members (" + (originalSize - resolvedSize) + ") could not be resolved (e.g, permissions issues or invalid node types).");
    }
    if (groupDBID==0L){
      errors.add("The schedule group could not be resolved (e.g, permissions issues or non-existent group).");
      ret.put("errors", errors);
      return ret;
    }
    try(
      final DatabaseLink link = res.createLink(action.startsWith("READ")?0:5);
    ){
      final CoreNode group = link.getNode(groupDBID);
      switch(action){
        case "READ":{
          ret.put("changes", 0);
          ret.put("members", new JSONArray(getMembers(group, false)));
          break;
        }
        case "READ_ALL":{
          ret.put("changes", 0);
          ret.put("members", new JSONArray(getMembers(group, true)));
          break;
        }
        case "ADD":{
          final ArrayList<CoreNode> modified = addMembers(link, group, members);
          link.commit();
          ret.put("changes", modified.size());
          if (!modified.isEmpty()){
            downloadSchedules(link, modified, download);
          }
          link.commit();
          break;
        }
        case "REMOVE":{
          final ArrayList<CoreNode> modified = removeMembers(link, group, members);
          link.commit();
          ret.put("changes", modified.size());
          if (!modified.isEmpty()){
            downloadSchedules(link, modified, download);
          }
          link.commit();
          break;
        }
        case "REPLACE":{
          final ArrayList<CoreNode> modified = replaceMembers(link, group, members);
          link.commit();
          ret.put("changes", modified.size());
          if (!modified.isEmpty()){
            downloadSchedules(link, modified, download);
          }
          link.commit();
          break;
        }
        default:{
          errors.add("Invalid action.");
        }
      }
    }
    if (!errors.isEmpty()){
      ret.put("errors", errors);
    }
    return ret;
  }
  /**
   * Asynchronously download schedules for the specified locations. If {@code startNow} is {@code true}, schedules will be downloaded as soon as possible; otherwise, they will be added to the suspended list and won't start until manually resumed.
   */
  public static void downloadSchedules(DatabaseLink dl, ArrayList<CoreNode> locs, boolean startNow) throws Throwable {
    HashSet<Long> cache = new HashSet<>();
    final Downloader downloader = new Downloader(dl.getCoreDataSession());
    try{
      DownloadAction action = downloader.getAddSuspendedAction(TaskSet.SCHEDULES);
      for (CoreNode loc: locs){
        downloadSchedules(dl, loc, action, cache);
      }
      if (startNow){
        cache.clear();
        action = downloader.getResumeAction(TaskSet.SCHEDULES);
        for (CoreNode loc: locs){
          downloadSchedules(dl, loc, action, cache);
        }
      }
    }finally{
      downloader.close();
    }
  }
  /**
   * Internal helper method to download schedules.
   */
  private static void downloadSchedules(DatabaseLink dl, CoreNode loc, DownloadAction action, HashSet<Long> cache) throws Throwable {
    if (!cache.add(loc.getDbid())){
      return;
    }
    final short type = loc.getNodeType();
    if (type==NodeType.GROUP || type==NodeType.HSGROUP){
      for (CoreNode n: loc.getExpectedChild(RefName.SCHED_GROUP_MEMBERS).getChildren()){
        downloadSchedules(dl, n.getCoreNodeAttribute(CoreNode.TARGET), action, cache);
      }
    }else{
      action.performActionRecursively(loc, Downloader.AFFECTED_SCHEDULES);
    }
  }
  /**
   * @return the schedule group with the specified reference name (case-insensitive), or null if no such group exists.
   */
  public static CoreNode getScheduleGroup(DatabaseLink dl, String refname) throws Throwable {
    for (final CoreNode group: new ScheduleGroupBase(dl.getCoreDataSession()).getAllGroups()){
      if (group.getReferenceName().equalsIgnoreCase(refname)){
        return group;
      }
    }
    return null;
  }
  /**
   * @param explode whether to recursively resolve members if there are nested groups.
   * @return a list of members in the specified schedule group.
   */
  public static HashSet<Long> getMembers(CoreNode group, boolean explode) throws Throwable {
    HashSet<Long> members;
    if (explode){
      members = new HashSet<>(32);
      getMembers(group, members, new HashSet<Long>());
    }else{
      final List<CoreNode> list = group.getExpectedChild(RefName.SCHED_GROUP_MEMBERS).getChildren();
      members = new HashSet<>(Math.max(16,(int)Math.ceil(list.size()/0.75)));
      for (CoreNode n: list){
        members.add(n.getCoreNodeAttribute(CoreNode.TARGET).getDbidObject());
      }
    }
    return members;
  }
  /**
   * Internal helper method to explode members of a schedule group.
   */
  private static void getMembers(CoreNode node, HashSet<Long> members, HashSet<Long> cache) throws Throwable {
    final short type = node.getNodeType();
    if (type==NodeType.GROUP || type==NodeType.HSGROUP){
      if (!cache.add(node.getDbid())){
        return;
      }
      for (CoreNode n: node.getExpectedChild(RefName.SCHED_GROUP_MEMBERS).getChildren()){
        getMembers(n.getCoreNodeAttribute(CoreNode.TARGET), members, cache);
      }
    }else{
      members.add(node.getDbidObject());
    }
  }
  /**
   * Add members to a schedule group.
   * @return a list of members whose schedule may have changed.
   */
  public static ArrayList<CoreNode> addMembers(DatabaseLink dl, CoreNode group, HashSet<Long> members) throws Throwable {
    members.removeAll(getMembers(group, false));
    final ArrayList<CoreNode> modified = new ArrayList<>(members.size());
    if (members.isEmpty()){
      return modified;
    }
    final CoreNode mems = group.getExpectedChild(RefName.SCHED_GROUP_MEMBERS);
    final CoreNode cloneNode = mems.getCoreNodeAttribute(CoreNode.DEFINITION).getExpectedChild("member");
    CoreNode n,m;
    for (Long member: members){
      n = dl.getNode(member);
      m = cloneNode.clone(mems, mems.makeUniqueRefName("member"));
      m.setCoreNodeAttribute(CoreNode.TARGET, n);
      modified.add(n);
    }
    return modified;
  }
  /**
   * Remove members from a schedule group.
   * @return a list of members whose schedule may have changed.
   */
  public static ArrayList<CoreNode> removeMembers(DatabaseLink dl, CoreNode group, HashSet<Long> members) throws Throwable {
    final ArrayList<CoreNode> modified = new ArrayList<>(members.size());
    if (members.isEmpty()){
      return modified;
    }
    final CoreNode mems = group.getExpectedChild(RefName.SCHED_GROUP_MEMBERS);
    CoreNode m;
    for (CoreNode n: mems.getChildren()){
      m = n.getCoreNodeAttribute(CoreNode.TARGET);
      if (members.contains(m.getDbidObject())){
        n.delete();
        modified.add(m);
      }
    }
    return modified;
  }
  /**
   * Replace members of a schedule group.
   * @return a list of members whose schedule may have changed.
   */
  public static ArrayList<CoreNode> replaceMembers(DatabaseLink dl, CoreNode group, HashSet<Long> members) throws Throwable {
    final ArrayList<CoreNode> modified = new ArrayList<>();
    final CoreNode mems = group.getExpectedChild(RefName.SCHED_GROUP_MEMBERS);
    final HashSet<Long> existing = new HashSet<>(members.size());
    CoreNode n,m;
    Long dbid;
    for (CoreNode c: mems.getChildren()){
      m = c.getCoreNodeAttribute(CoreNode.TARGET);
      dbid = m.getDbidObject();
      if (members.contains(dbid)){
        existing.add(dbid);
      }else{
        c.delete();
        modified.add(m);
      }
    }
    final CoreNode cloneNode = mems.getCoreNodeAttribute(CoreNode.DEFINITION).getExpectedChild("member");
    for (Long member: members){
      if (!existing.contains(member)){
        n = dl.getNode(member);
        m = cloneNode.clone(mems, mems.makeUniqueRefName("member"));
        m.setCoreNodeAttribute(CoreNode.TARGET, n);
        modified.add(n);
      }
    }
    return modified;
  }
}