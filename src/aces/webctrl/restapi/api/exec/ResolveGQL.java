package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.api.*;
import aces.webctrl.restapi.core.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
public class ResolveGQL extends ApiBase {
  public static String evalAndSet(CoreNode node, String expr, String value, boolean checkPriv, boolean fieldAccess) throws Throwable {
    expr = expand(node, expr, checkPriv, fieldAccess);
    if (expr==null){
      return null;
    }
    final CoreNode.ExpInfo info = node.evalToExpInfo(expr);
    if (checkPriv && !info.node.hasEditPriv()){
      return null;
    }
    if (fieldAccess){
      info.node.makeFresh(1);
    }
    return info.set(value);
  }
  public static CoreNode evalToNode(CoreNode node, String expr, boolean checkPriv, boolean fieldAccess) throws Throwable {
    expr = expand(node, expr, checkPriv, fieldAccess);
    if (expr==null){
      return null;
    }
    final CoreNode n = node.evalToNode(expr);
    if (checkPriv && !n.hasViewPriv()){
      return null;
    }
    return n;
  }
  public static String eval(CoreNode node, String expr, boolean checkPriv, boolean fieldAccess, boolean display) throws Throwable {
    expr = expand(node, expr, checkPriv, fieldAccess);
    if (expr==null){
      return null;
    }
    final CoreNode.ExpInfo info = node.evalToExpInfo(expr);
    if (checkPriv && !info.node.hasViewPriv()){
      return null;
    }
    if (fieldAccess){
      info.node.makeFresh(1);
    }
    final String s = CoreNode.eval(info, true, display);
    return s==null?"":s;
  }
  public static String expand(CoreNode node, String expr, boolean checkPriv, boolean fieldAccess) throws Throwable {
    if (!expr.contains("${")){
      return expr;
    }
    final int len = expr.length();
    final StringBuilder sb = new StringBuilder(Math.max(len,8));
    final StringBuilder var = new StringBuilder(len);
    char c;
    boolean b = false;
    String s;
    for (int i=0,j=0;i<len;++i){
      c = expr.charAt(i);
      if (b){
        if (c=='{'){
          ++j;
        }else if (c=='}'){
          --j;
          if (j==0){
            b = false;
            s = eval(node, var.toString(), checkPriv, fieldAccess, false);
            if (s==null){
              return null;
            }
            sb.append(s);
            s = null;
            var.setLength(0);
            continue;
          }
        }
        var.append(c);
      }else if (c=='$' && i+3<len){
        if (expr.charAt(i+1)=='{'){
          b = true;
          ++i;
          j = 1;
        }else{
          sb.append('$');
        }
      }else{
        sb.append(c);
      }
    }
    if (b){
      sb.append(eval(node, var.toString(), checkPriv, fieldAccess, false));
    }
    return sb.toString();
  }
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
      expression = null;
    }
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
        if (!res.isAdmin() && !node.hasViewPriv()){
          node = null;
        }
      }else{
        if (relative && contextDBID>=0){
          node = link.getNode(contextDBID);
        }else{
          node = link.getNode("/trees/geographic");
        }
        if (res.isAdmin() || node.hasViewPriv()){
          if (!path.isBlank()){
            node = evalToNode(node, path, !res.isAdmin(), false);
          }
        }else{
          node = null;
        }
      }
      if (node==null){
        res.status = 403;
        ret.put("error", "You do not have permission to view the specified node.");
        return ret;
      }
      ret.put("display-name", node.getDisplayName());
      ret.put("reference-name", node.getReferenceName());
      ret.put("node-type", NodeType.toString(node.getNodeType()));
      ret.put("dbid", node.getDbid());
      if (listAttr){
        if (fieldAccess){
          node.makeFresh(1);
        }
        final JSONObject o = new JSONObject();
        for (NodeAttribute na:node.getAttributes()){
          if (na!=CoreNode.DBID && na!=CoreNode.REFERENCE_NAME && na!=CoreNode.DISPLAY_NAME){
            o.put(na.toString(), node.getAttribute(na));
          }
        }
        ret.put("attributes", o);
      }
      int i;
      String s;
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
            i = 0;
            for (String expr : exprs){
              ++i;
              try{
                s = eval(child, expr, !res.isAdmin(), fieldAccess, false);
                cobj.put("expr"+i, s==null?"ERROR: You do not have permission to evaluate this expression.":s);
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
            s = eval(node, expr, !res.isAdmin(), fieldAccess, false);
            ret.put("expr"+i, s==null?"ERROR: You do not have permission to evaluate this expression.":s);
          }catch(Throwable t){
            ret.put("expr"+i, "ERROR: "+t.getMessage());
          }
        }
      }
    }
    return ret;
  }
}