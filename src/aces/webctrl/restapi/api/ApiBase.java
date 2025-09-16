package aces.webctrl.restapi.api;
import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.schema.*;
import com.controlj.green.core.data.Operator;
public abstract class ApiBase {
  public volatile String name;
  public volatile long perm = 0;
  public volatile int index = 0;
  public volatile JSONSchema schema = null;
  /**
   * Checks if the authenticated user has the necessary permissions to access the API endpoint.
   * 
   * <p>Implementations should look similar to the following:</p>
   * <pre>
   * return op.hasFunctionalPrivilege(212);
   * </pre>
   * 
   * <p>Role and privilege definitions are configured in the WebCTRL installation directory at
   * {@code /resources/xmlfiles/privileges.xml}.</p>
   * 
   * @return {@code true} if the user has the required permissions, {@code false} otherwise
   */
  public boolean hasPermission(Operator op) throws Throwable {
    return true;
  }
  public abstract JSONObject exec(JSONObject input, ApiResponse response) throws Throwable;
}