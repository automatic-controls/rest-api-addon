package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.api.*;
import aces.webctrl.restapi.core.*;
import java.util.*;
import java.lang.reflect.Method;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.CoreNode;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.impl.LookupString;
import com.controlj.green.trendgraph.datasources.processing.MultiSourceTrendProcessor;
import com.controlj.green.trendgraph.servlets.*;
public class GetTrendData extends ApiBase {
  private final static TrendCollector collector = new TrendCollector();
  @Override public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    JSONObject ret = new JSONObject();
    final long contextDBID = input.getLongValue("contextDBID", -1);
    final boolean fieldAccess = input.getBooleanValue("fieldAccess", false);
    final long startTime = input.getLongValue("startTime", 0);
    final long endTime = input.getLongValue("endTime", 0);
    final long resolution = input.getLongValue("resolution", 60000);
    JSONArray nodes = input.getJSONArray("sources");
    HashSet<String> gqls = new HashSet<>();
    HashSet<Long> dbids = new HashSet<>(Math.max((int)Math.ceil(nodes.size()/0.75),8));
    ArrayList<String> sources = new ArrayList<>(nodes.size());
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
        for (long dbid: dbids){
          n = link.getNode(dbid);
          if (res.isAdmin() || n.hasViewPriv()){
            sources.add(LookupString.getString(true, n));
          }
        }
        if (res.isAdmin() || ctx.hasViewPriv()){
          for (String g: gqls){
            n = ResolveGQL.evalToNode(ctx, g, !res.isAdmin(), false);
            if (n!=null){
              sources.add(LookupString.getString(true, n));
            }
          }
        }
      }
    }
    gqls = null;
    dbids = null;
    ret.put("data", collector.collect(sources, startTime, endTime, resolution, fieldAccess, res.getSystemConnection()));
    return ret;
  }
  private static class TrendCollector extends JSONResponseTrendDataServlet {
    public JSONArray collect(List<String> sources, long startTime, long endTime, Long resolution, boolean fieldAccess, SystemConnection con) throws Throwable {
      if (startTime<0 || endTime<=0){
        final long now = System.currentTimeMillis();
        if (startTime<0){
          startTime += now;
        }
        if (endTime<=0){
          endTime += now;
        }
      }
      final Date start = new Date(startTime);
      final Date end = new Date(endTime);
      final StringBuilder sb = new StringBuilder(2048);
      final org.json.JSONWriter writer = new org.json.JSONWriter(sb);
      final Method m = JSONResponseTrendDataServlet.class.getDeclaredMethod("makeCallbackInternal", Date.class, Date.class, Long.class, org.json.JSONWriter.class);
      m.setAccessible(true);
      new MultiSourceTrendProcessor().processMultipleSources(con, fieldAccess, sources, (MultiSourceTrendProcessor.Callback)m.invoke(this, start, end, resolution, writer));
      return JSONArray.parse(sb.toString());
    }
  }
}