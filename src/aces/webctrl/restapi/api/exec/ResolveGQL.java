package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.api.*;
import aces.webctrl.restapi.core.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
public class ResolveGQL extends ApiBase {
  @Override public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    JSONObject ret = new JSONObject();
    final Object p = input.get("path");
    String path = null;
    long dbid = 0;
    if (p==null){
      path = "";
    }else if (p instanceof Number){
      dbid = ((Number)p).longValue();
    }else{
      path = ((String)p).trim();
    }
    final boolean relative = input.getBooleanValue("relative", true);
    final boolean includeChildren = input.getBooleanValue("includeChildren", false);
    final boolean listAttr = input.getBooleanValue("listAttributes", false);
    final long contextDBID = input.getLongValue("contextDBID", -1);
    boolean fieldAccess = input.getBooleanValue("fieldAccess", false);
    final ArrayList<String> exprs = new ArrayList<>(4);
    Object expression = input.get("expression");
    if (expression!=null){
      String s;
      if (expression instanceof String){
        s = ((String)expression).trim();
        if (!s.isEmpty()){
          exprs.add(s);
        }
      }else if (expression instanceof JSONArray){
        for (Object o: (JSONArray)expression){
          if (o instanceof String){
            s = ((String)o).trim();
            if (!s.isEmpty()){
              exprs.add(s);
            }
          }
        }
      }
    }
    expression = null;
    HashSet<Short> nodeTypes = null;
    final JSONArray childFilter = input.getJSONArray("childFilter");
    if (childFilter!=null){
      nodeTypes = new HashSet<>();
      for (Object o : childFilter){
        nodeTypes.add(NodeType.toNodeType((String)o));
      }
      if (nodeTypes.isEmpty()){
        nodeTypes = null;
      }
    }
    fieldAccess &= listAttr || !exprs.isEmpty();
    try(
      DatabaseLink link = res.createLink(fieldAccess?8:0);
    ){
      CoreNode node;
      if (path==null){
        node = link.getNode(dbid);
      }else{
        if (relative && contextDBID>=0){
          node = link.getNode(contextDBID);
        }else{
          node = link.getNode("/trees/geographic");
        }
        if (!path.isBlank()){
          node = node.evalToNode(path);
        }
      }
      if (!res.isAdmin() && !node.hasViewPriv()){
        res.status = 403;
        ret.put("error", "You do not have permission to view the specified node.");
        return ret;
      }
      ret.put("display-name", node.getDisplayName());
      ret.put("reference-name", node.getReferenceName());
      ret.put("node-type", NodeType.toString(node.getNodeType()));
      ret.put("dbid", node.getDbid());
      if (fieldAccess && (listAttr || !includeChildren && !exprs.isEmpty())){
        node.makeFresh(1);
      }
      if (listAttr){
        final JSONObject o = new JSONObject();
        for (NodeAttribute na:node.getAttributes()){
          if (na!=CoreNode.DBID && na!=CoreNode.REFERENCE_NAME && na!=CoreNode.DISPLAY_NAME){
            o.put(na.toString(), node.getAttribute(na));
          }
        }
        ret.put("attributes", o);
      }
      int i;
      if (includeChildren){
        final JSONArray arr = new JSONArray();
        JSONObject cobj;
        short type;
        for (CoreNode child : node.getChildrenSortedForPresentation()){
          type = child.getNodeType();
          if ((nodeTypes==null || nodeTypes.contains(type)) && (res.isAdmin() || child.hasViewPriv())){
            cobj = new JSONObject();
            cobj.put("display-name", child.getDisplayName());
            cobj.put("reference-name", child.getReferenceName());
            cobj.put("node-type", NodeType.toString(type));
            cobj.put("dbid", child.getDbid());
            if (fieldAccess && !exprs.isEmpty()){
              child.makeFresh(1);
            }
            i = 0;
            for (String expr : exprs){
              ++i;
              try{
                cobj.put("expr"+i, child.evalToDisplayValueString(expr, true));
              }catch(Throwable t){
                cobj.put("expr"+i, "ERROR: "+t.getMessage());
              }
            }
            arr.add(cobj);
          }
        }
        ret.put("children", arr);
      }else{
        i = 0;
        for (String expr : exprs){
          ++i;
          try{
            ret.put("expr"+i, node.evalToDisplayValueString(expr, true));
          }catch(Throwable t){
            ret.put("expr"+i, "ERROR: "+t.getMessage());
          }
        }
      }
    }
    return ret;
  }
}