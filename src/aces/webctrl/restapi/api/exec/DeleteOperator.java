package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.api.*;
import aces.webctrl.restapi.core.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
import com.controlj.green.core.ui.UserSession;
public class DeleteOperator extends ApiBase {
  @Override public boolean hasPermission(Operator op) throws Throwable {
    return op.hasFunctionalPrivilege(201);
  }
  @Override public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    final JSONObject ret = new JSONObject();
    if (!res.isAdmin()){
      res.status = 403;
      ret.put("error", "You do not have permission to modify operators.");
      return ret;
    }
    final HashSet<String> operators = new HashSet<>(8);
    Object op = input.get("operator");
    if (op!=null){
      String s;
      if (op instanceof String){
        s = ((String)op).trim();
        if (!s.isEmpty()){
          operators.add(s.toLowerCase());
        }
      }else if (op instanceof JSONArray){
        for (Object o: (JSONArray)op){
          if (o instanceof String){
            s = ((String)o).trim();
            if (!s.isEmpty()){
              operators.add(s.toLowerCase());
            }
          }
        }
      }
      op = null;
    }
    if (operators.isEmpty()){
      res.status = 400;
      ret.put("error", "No operators specified.");
      return ret;
    }
    {
      final Operator current = res.getOperator();
      if (current!=null && operators.contains(current.getLoginName().toLowerCase())){
        res.status = 400;
        ret.put("error", "You cannot delete yourself.");
        return ret;
      }
    }
    int count = 0;
    try (
      DatabaseLink link = res.createLink(1);
    ){
      final CoreNode ops = link.getNode("/trees/config/operators/operatorlist");
      String user;
      for (final CoreNode n: ops.getChildren()){
        user = n.getAttribute(CoreNode.KEY).toLowerCase();
        if (operators.contains(user)){
          n.delete();
          ++count;
        }
      }
      link.commit();
    }
    if (count==0){
      res.status = 404;
      ret.put("error", "No matching operators found.");
      return ret;
    }
    String n;
    for (final UserSession session:UserSession.getAllUserSessions()){
      n = session.getOperator().getLoginName();
      if (n!=null && operators.contains(n.toLowerCase())){
        session.close();
      }
    }
    ret.put("deleted", count);
    return ret;
  }
}