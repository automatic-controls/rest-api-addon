package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.core.*;
import aces.webctrl.restapi.api.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
public class ExecGQL extends ApiBase {
  @Override
  public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    final JSONObject ret = new JSONObject();
    final long contextDBID = input.getLongValue("contextDBID", -1);
    final boolean fieldAccess = input.getBooleanValue("fieldAccess", false);
    JSONArray get = input.getJSONArray("get");
    JSONArray set = input.getJSONArray("set");
    JSONArray nodes = input.getJSONArray("nodes");
    HashSet<String> gqls = new HashSet<>();
    HashSet<Long> dbids = new HashSet<>(Math.max((int)Math.ceil(nodes.size()/0.75),8));
    if (nodes == null || nodes.isEmpty()) {
      gqls.add("/trees/geographic");
    } else {
      for (Object root : nodes) {
        if (root instanceof Number) {
          dbids.add(((Number) root).longValue());
        } else if (root instanceof String) {
          gqls.add(((String) root).trim());
        }
      }
    }
    nodes = null;
    final ArrayList<String> getEx = new ArrayList<>(get==null?0:get.size());
    final ArrayList<SetExpr> setEx = new ArrayList<>(set==null?0:set.size());
    if (get!=null) {
      for (Object o: get) {
        if (o instanceof String) {
          final String s = ((String)o).trim();
          if (!s.isEmpty()) {
            getEx.add(s);
          }
        }
      }
    }
    get = null;
    if (set!=null) {
      JSONObject jo;
      for (Object o: set) {
        if (o instanceof JSONObject) {
          jo = (JSONObject)o;
          final String expr = jo.getString("expression").trim();
          if (!expr.isEmpty()) {
            setEx.add(new SetExpr(expr, jo.get("value")));
          }
        }
      }
    }
    set = null;
    if (!gqls.isEmpty()) {
      try(
        DatabaseLink link = res.createLink(0);
      ){
        CoreNode ctx,n;
        if (contextDBID>=0){
          ctx = link.getNode(contextDBID);
        }else{
          ctx = link.getNode("/trees/geographic");
        }
        final Iterator<Long> it = dbids.iterator();
        long dbid;
        while (it.hasNext()){
          dbid = it.next();
          n = link.getNode(dbid);
          if (!res.isAdmin() && !n.hasViewPriv()){
            it.remove();
          }
        }
        if (res.isAdmin() || ctx.hasViewPriv()){
          for (String g: gqls){
            n = ResolveGQL.evalToNode(ctx, g, !res.isAdmin(), false);
            if (n!=null){
              dbids.add(n.getDbid());
            }
          }
        }
      }
    }
    gqls = null;
    final int len = dbids.size();
    final JSONArray arr = new JSONArray(len);
    final JSONArray errors = new JSONArray();
    if (!dbids.isEmpty()) {
      final ArrayList<Long> dbidList = new ArrayList<>(dbids);
      dbidList.sort(null);
      dbids = null;
      JSONObject o;
      for (long dbid : dbidList) {
        o = new JSONObject();
        o.put("dbid", dbid);
        arr.add(o);
      }
      if (!getEx.isEmpty()){
        try(
          final DatabaseLinkManager dlm = res.createLinkManager(fieldAccess?8:0, 2000L, false);
        ){
          long dbid;
          CoreNode n;
          for (int i=0,j;i<len;++i){
            dbid = dbidList.get(i);
            o = arr.getJSONObject(i);
            n = dlm.getLink().getNode(dbid);
            j = 0;
            for (String expr: getEx){
              ++j;
              try{
                o.put("get"+j, ResolveGQL.eval(n, expr, !res.isAdmin(), fieldAccess, false));
              }catch(CoreNotFoundException ex){}catch(Throwable ex){
                final JSONObject err = new JSONObject(5);
                err.put("dbid", dbid);
                err.put("expression", expr);
                err.put("error", ex.getMessage());
                errors.add(err);
              }
            }
          }
        }
      }
      if (!setEx.isEmpty()){
        try(
          final DatabaseLinkManager dlm = res.createLinkManager(fieldAccess?9:1, 2000L, true);
        ){
          long dbid;
          CoreNode n;
          for (int i=0,j;i<len;++i){
            dbid = dbidList.get(i);
            o = arr.getJSONObject(i);
            n = dlm.getLink().getNode(dbid);
            j = 0;
            for (SetExpr expr: setEx){
              ++j;
              try{
                o.put("set"+j, ResolveGQL.evalAndSet(n, expr.expr, expr.value, !res.isAdmin(), fieldAccess));
              }catch(CoreNotFoundException ex){}catch(Throwable ex){
                final JSONObject err = new JSONObject(5);
                err.put("dbid", dbid);
                err.put("expression", expr.expr);
                err.put("value", expr.value);
                err.put("error", ex.getMessage());
                errors.add(err);
              }
            }
          }
        }
      }
    }
    ret.put("nodes", arr);
    if (!errors.isEmpty()){
      ret.put("errors", errors);
    }
    return ret;
  }
  private static class SetExpr {
    public final String expr;
    public final String value;
    public SetExpr(String e, Object v){
      expr = e;
      value = v==null?null:v.toString();
    }
  }
}
