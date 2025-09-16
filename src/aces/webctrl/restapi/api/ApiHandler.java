package aces.webctrl.restapi.api;
import aces.webctrl.restapi.core.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import javax.servlet.http.*;
import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.schema.*;
import java.nio.charset.StandardCharsets;
import com.controlj.green.core.data.Operator;
import com.controlj.green.tomcat.security.CJPrincipal;
public class ApiHandler extends HttpServlet {
  public final static HashMap<String,ApiBase> endpoints = new HashMap<>(43);
  public volatile static ApiBase[] endpointArray;
  private volatile static JSONSchema jwtSchema = null;
  public static void loadEndpoints() throws Throwable {
    {
      String name;
      Class<?> cl;
      ApiBase endpoint;
      for (Field field : Permissions.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()) && field.getType()==int.class) {
          name = field.getName();
          cl = Class.forName("aces.webctrl.restapi.api.exec."+name);
          if (ApiBase.class.isAssignableFrom(cl)) {
            endpoint = (ApiBase)cl.getConstructor().newInstance();
            endpoint.name = name;
            endpoint.index = field.getInt(null);
            endpoint.perm = 1L<<endpoint.index;
            endpoint.schema = JSONSchema.parseSchema(Utility.loadResourceAsString("aces/webctrl/restapi/api/exec/"+name+".json"));
            endpoints.put(name.toLowerCase(), endpoint);
          }
        }
      }
    }
    endpointArray = new ApiBase[endpoints.size()];
    for (ApiBase e: endpoints.values()){
      endpointArray[e.index] = e;
    }
    jwtSchema = JSONSchema.parseSchema(Utility.loadResourceAsString("aces/webctrl/restapi/api/JWT.json"));
  }
  @Override public void doPost(final HttpServletRequest req, final HttpServletResponse res){
    try{
      try{
        req.setCharacterEncoding("UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setContentType("application/json");
        if (Initializer.stop){
          writeError(res, 500, "Add-on is shutting down.");
          return;
        }
        if (jwtSchema==null){
          writeError(res, 500, "Add-on is not initialized.");
          return;
        }
        String auth = req.getHeader("Authorization");
        if (auth==null){
          if (!req.isUserInRole("login")){
            writeError(res, 403, "You do not have the required permissions to access this API.");
            return;
          }
        }else{
          if (!auth.toLowerCase().startsWith("bearer ")){
            writeError(res, 400, "Malformed Authorization header.");
            return;
          }
          auth = auth.substring(7).trim();
          if (auth.length()==0){
            writeError(res, 400, "Malformed Authorization header.");
            return;
          }
        }
        String name = (req.getServletPath()+Utility.coalesce(req.getPathInfo(),"")).toLowerCase();
        if (!name.startsWith("/api/")){
          writeError(res, 404, "Endpoint does not exist.");
          return;
        }
        name = name.substring(5);
        final ApiBase endpoint = endpoints.get(name);
        if (endpoint==null){
          writeError(res, 404, "Endpoint does not exist.");
          return;
        }
        JSONObject input;
        byte[] bytes = Utility.readAllBytes(req.getInputStream(),16384);
        if (bytes==null){
          writeError(res, 400, "Request body size has exceeded the 16 KB limit.");
          return;
        }
        try {
          input = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (JSONException e) {
          writeError(res, 400, "Failed to parse JSON from request body: "+e.getMessage());
          return;
        }
        if (input==null){
          writeError(res, 400, "Failed to parse JSON from request body: Body is null.");
          return;
        }
        if (Config.verboseLogging){
          final JSONObject log = new JSONObject();
          log.put("endpoint", name);
          log.put("address", req.getRemoteAddr());
          log.put("packet", input);
          Initializer.log("API Request - "+log.toString());
        }
        ValidateResult v;
        Operator operator = null;
        if (auth==null){
          bytes = null;
          operator = (Operator)(((CJPrincipal)req.getUserPrincipal()).getOperator());
        }else{
          v = jwtSchema.validate(input);
          if (!v.isSuccess()){
            writeError(res, 400, "Malformed JWT: "+v.getMessage());
            return;
          }
          if (!name.equalsIgnoreCase(input.getString("aud"))){
            writeError(res, 400, "JWT audience does not match endpoint.");
            return;
          }
          final ApiKey key = Config.getKey(input.getString("iss"));
          if (key==null){
            writeError(res, 403, "Invalid API key.");
            return;
          }
          if (!key.hasPermission(endpoint.perm)){
            writeError(res, 403, "You do not have the required permissions to access this endpoint.");
            return;
          }
          if (!auth.equals(key.HMACSHA256(bytes))){
            writeError(res, 403, "Invalid JWT signature.");
            return;
          }
          bytes = null;
          boolean expired = false;
          boolean limited = false;
          boolean collision = false;
          final long issuedAt = input.getLong("iat")*1000L;
          final String nonce = input.getString("jti");
          synchronized (key){
            final long currentTime = System.currentTimeMillis();
            expired = Math.abs(currentTime-issuedAt)>=300000L;
            if (!expired){
              key.cleanNonces(currentTime);
              if (key.getNonceCount()>=10000){
                limited = true;
              }else if (!key.addNonce(nonce, Math.min(issuedAt,currentTime))){
                collision = true;
              }
            }
          }
          if (expired){
            writeError(res, 400, "JWT issued-at time is not within 5 minutes of current time.");
            return;
          }
          if (limited){
            writeError(res, 429, "Rate limit has been exceeded.");
            return;
          }
          if (collision){
            writeError(res, 409, "JWT nonce collision has occurred.");
            return;
          }
          final String op = key.getOperator();
          if (!op.isEmpty()){
            try{
              operator = Operator.getOperator(op);
            }catch(Throwable t){
              writeError(res, 403, "The operator corresponding to this API key could not be retrieved.");
              return;
            }
          }
          input = input.getJSONObject("data");
          if (input==null){
            input = new JSONObject();
          }
        }
        v = endpoint.schema.validate(input);
        if (!v.isSuccess()){
          writeError(res, 400, "Malformed data: "+v.getMessage());
          return;
        }
        if (operator!=null && !endpoint.hasPermission(operator)){
          writeError(res, 403, "You do not have the required permissions to access this endpoint.");
          return;
        }
        try (
          final ApiResponse response = new ApiResponse(req, auth==null, operator);
        ){
          final String result = endpoint.exec(input, response).toString();
          res.setStatus(response.status);
          res.getWriter().write(result);
        }
      }catch(NumberFormatException e){
        writeError(res, 400, "Failed to parse number from string.");
      }catch(Throwable t){
        writeError(res, 500, t);
        if (Config.verboseLogging){
          Initializer.log(t);
        }
      }
    }catch(IOException e){
      res.setStatus(500);
      if (Config.verboseLogging){
        Initializer.log(e);
      }
    }
  }
  private static void writeError(HttpServletResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    final PrintWriter o = res.getWriter();
    o.write("{\"error\":\"");
    o.write(Utility.escapeJSON(message));
    o.write("\"}");
  }
  private static void writeError(HttpServletResponse res, int status, Throwable t) throws IOException {
    final JSONObject o = new JSONObject();
    final JSONArray arr = new JSONArray();
    Utility.getStackTrace(t).replace("\t","    ").lines().forEachOrdered(arr::add);
    o.put("error", arr);
    res.setStatus(status);
    res.getWriter().write(o.toString());
  }
}