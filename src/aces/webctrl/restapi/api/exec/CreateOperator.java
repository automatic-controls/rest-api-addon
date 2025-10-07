package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.api.*;
import aces.webctrl.restapi.core.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
public class CreateOperator extends ApiBase {
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
    final String username = input.getString("username");
    String displayName = input.getString("displayName");
    String password = input.getString("password");
    final boolean hashed = input.getBooleanValue("hashed", false);
    final Boolean temporary = input.getBoolean("temporary");
    final Boolean exempt = input.getBoolean("exempt");
    final boolean update = input.getBooleanValue("update", false);
    final JSONArray roleArray = input.getJSONArray("roles");
    final JSONArray groupsArray = input.getJSONArray("groups");
    if (password!=null && password.isBlank()){
      password = null;
    }
    if (displayName!=null && displayName.isBlank()){
      displayName = null;
    }
    HashSet<String> roles = null;
    HashSet<String> groups = null;
    if (roleArray!=null){
      roles = new HashSet<>(Math.max(1,(int)Math.ceil(roleArray.size()/0.75)));
      for (Object o: roleArray){
        if (o!=null){
          roles.add((String)o);
        }
      }
    }
    if (groupsArray!=null){
      groups = new HashSet<>(Math.max(1,(int)Math.ceil(groupsArray.size()/0.75)));
      for (Object o: groupsArray){
        if (o!=null){
          groups.add((String)o);
        }
      }
    }
    try(
      DatabaseLink link = res.createLink(1);
      ){
      final Container<Boolean> updateContainer = new Container<>(update);
      final CoreNode op = link.createOperator(username, displayName, password, hashed, temporary, exempt, roles, groups, updateContainer);
      if (op==null){
        res.status = 400;
        ret.put("error", "An operator with that username already exists.");
        return ret;
      }
      if (!updateContainer.x){
        res.status = 201;
      }
      ret.put("path", "/trees/config/operators/operatorlist/"+op.getReferenceName());
      ret.put("dbid", op.getDbid());
      link.commit();
    }
    return ret;
  }
}