package aces.webctrl.restapi.web;
import aces.webctrl.restapi.core.*;
import aces.webctrl.restapi.api.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.alibaba.fastjson2.*;
import java.nio.charset.StandardCharsets;
import com.controlj.green.addonsupport.web.auth.WebOperator;
import com.controlj.green.core.data.Operator;
import com.controlj.green.tomcat.security.CJPrincipal;
public class MainPage extends ServletBase {
  public boolean checkRole(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    boolean ret;
    if (!(ret = req.isUserInRole("login"))){
      res.sendError(403, "You do not have the required permissions to view this page.");
    }
    return ret;
  }
  @Override public void exec(final HttpServletRequest req, final HttpServletResponse res) throws Throwable {
    final String reqUsername = ((WebOperator)((CJPrincipal)req.getUserPrincipal()).getWebOperator()).getLoginName();
    if (reqUsername==null){
      res.sendError(500, "Unable to determine the current user.");
      return;
    }
    final String effUsername = req.isUserInRole("view_administrator_only") ? null : reqUsername;
    final String action = req.getParameter("action");
    if (action==null){
      res.setContentType("text/html");
      res.getWriter().print(getHTML(req)
        .replace("__ADMIN__", String.valueOf(effUsername==null))
        .replace("__OPERATOR__", reqUsername)
      );
      return;
    }
    switch (action.toLowerCase()){
      case "endpoints":{
        final JSONArray arr = new JSONArray();
        for (ApiBase e: ApiHandler.endpointArray){
          arr.add(e.name);
        }
        res.setContentType("application/json");
        res.getWriter().print(arr.toString());
        break;
      }
      case "load":{
        final String s = Config.listKeys(effUsername);
        res.setContentType("application/json");
        res.getWriter().print(s);
        break;
      }
      case "delete":{
        final String key = req.getParameter("key");
        if (key==null || key.isBlank()){
          res.setStatus(400);
          return;
        }
        if (!Config.deleteKey(key, effUsername)){
          res.setStatus(404);
          return;
        }
        Config.saveData();
        break;
      }
      case "new":{
        final String name = req.getParameter("name");
        String operator = req.getParameter("operator");
        final String p = req.getParameter("perms");
        if (name==null || name.isBlank() || p==null || p.isBlank() || operator==null){
          res.setStatus(400);
          return;
        }
        operator = operator.trim();
        long perms;
        try{
          perms = Long.parseLong(p);
        }catch(NumberFormatException ex){
          res.setStatus(400);
          return;
        }
        @SuppressWarnings("unused")
        Operator op = null;
        if (!operator.isEmpty()){
          try{
            op = Operator.getOperator(operator);
          }catch(Throwable t){
            res.setStatus(404);
            return;
          }
        }
        if (effUsername!=null){
          if (operator.isEmpty() || !effUsername.equals(operator)){
            res.setStatus(403);
            return;
          }
          // Optionally remove endpoint permissions that are not currently available to the operator
          // for (ApiBase e: ApiHandler.endpointArray){
          //   if (!e.hasPermission(op)){
          //     perms &= ~e.perm;
          //   }
          // }
        }
        op = null;
        if (perms==0){
          res.setStatus(400);
          return;
        }
        final ApiKey k = Config.newKey(name, operator, perms);
        Config.saveData();
        final JSONObject o = new JSONObject();
        o.put("id", k.getPublicKey());
        o.put("name", k.getName());
        o.put("operator", k.getOperator());
        o.put("perms", k.getPermissions());
        o.put("key", new String(Utility.obfuscate(new String(k.getPrivateKey(), StandardCharsets.UTF_8).toCharArray())));
        res.setContentType("application/json");
        res.getWriter().print(o.toString());
        break;
      }
      default:{
        res.setStatus(400);
      }
    }
  }
}