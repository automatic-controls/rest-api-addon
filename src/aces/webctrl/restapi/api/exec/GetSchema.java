package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.core.*;
import aces.webctrl.restapi.api.*;
import com.alibaba.fastjson2.*;
public class GetSchema extends ApiBase {
  @Override public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    final String name = input.getString("endpoint");
    final boolean verbose = input.getBooleanValue("verbose", false);
    final ApiBase endpoint = ApiHandler.endpoints.get(name.toLowerCase());
    if (endpoint==null){
      res.status = 404;
      JSONObject ret = new JSONObject();
      ret.put("error", "An endpoint with the specified name does not exist.");
      return ret;
    }
    JSONObject o;
    if (verbose){
      o = JSONObject.parse(Utility.loadResourceAsString("aces/webctrl/restapi/api/exec/"+endpoint.name+".json"));
    }else{
      o = endpoint.schema.toJSONObject();
      o.remove("title");
      o.remove("description");
    }
    return o;
  }
}