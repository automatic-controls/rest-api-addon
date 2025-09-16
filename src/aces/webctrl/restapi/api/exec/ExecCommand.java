package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.api.*;
import aces.webctrl.restapi.core.*;
import java.io.StringWriter;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
import com.controlj.green.core.process.*;
import com.controlj.green.core.process.CoreWriter.Type;
public class ExecCommand extends ApiBase {
  @Override public boolean hasPermission(Operator op) throws Throwable {
    return op.hasFunctionalPrivilege(212);
  }
  @Override public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    JSONObject ret = new JSONObject();
    final String cmd = input.getString("command");
    final boolean split = input.getBooleanValue("splitLines", false);
    final long contextDBID = input.getLongValue("contextDBID", -1);
    if (res.isAdmin() && input.getBooleanValue("dev", false)){
      res.getSession().setIsDeveloper(true);
    }
    String path = null;
    try(
      final DatabaseLink link = res.createLink(0);
    ){
      CoreNode node;
      if (contextDBID>=0){
        node = link.getNode(contextDBID);
      }else{
        node = link.getNode("/trees/geographic");
      }
      if (!res.isAdmin() && !node.hasViewPriv()){
        res.status = 403;
        ret.put("error", "You do not have permission to view the specified node.");
        return ret;
      }
      path = node.getPath();
    }
    final StringWriter sw = new StringWriter(128);
    final CoreWriter cr = new CoreWriter(sw, "text", Type.TEXT);
    final CoreProcess child = CoreProcess.init.exec("gsh", new String[]{"gsh", "-n", path, "-c", cmd}, null, 1);
    child.stdout = cr;
    child.stderr = cr;
    child.setUser(res.getSession());
    child.start();
    child.waitFor();
    ret.put("exitCode", child.getStatus());
    if (split){
      final JSONArray arr = new JSONArray();
      sw.toString().replace("\t","    ").lines().forEachOrdered(arr::add);
      ret.put("output", arr);
    }else{
      ret.put("output", sw.toString());
    }
    return ret;
  }
}