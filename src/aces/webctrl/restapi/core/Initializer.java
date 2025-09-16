package aces.webctrl.restapi.core;
import aces.webctrl.restapi.api.*;
import javax.servlet.*;
import java.nio.file.*;
import com.controlj.green.addonsupport.*;
import com.controlj.green.common.CJProductDirectories;
public class Initializer implements ServletContextListener {
  /** Contains basic information about this addon */
  public volatile static AddOnInfo info = null;
  /** The name of this addon */
  private volatile static String name;
  /** Prefix used for constructing relative URL paths */
  private volatile static String prefix;
  /** Path to the private directory for this addon */
  private volatile static Path root;
  /** Path to WebCTRL's installation directory */
  public volatile static Path installDir = null;
  /** Path to WebCTRL's active system directory */
  public volatile static Path systemDir = null;
  /** Logger for this addon */
  private volatile static FileLogger logger;
  /** Becomes true when the servlet context is destroyed */
  public volatile static boolean stop = false;
  /**
   * Initializes static variables and attempts to load saved data.
   */
  @Override public void contextInitialized(ServletContextEvent sce){
    info = AddOnInfo.getAddOnInfo();
    logger = info.getDateStampLogger();
    try{
      name = info.getName();
      prefix = '/'+name+'/';
      root = info.getPrivateDir().toPath();
      installDir = CJProductDirectories.getBaseDir().toPath();
      systemDir = root.getParent().getParent().getParent();
      Config.init(root.resolve("config.dat"));
      ApiHandler.loadEndpoints();
    }catch(Throwable t){
      log(t);
    }
  }
  /**
   * Sets the stop variable to true, which will cause any long-running threads to terminate.
   */
  @Override public void contextDestroyed(ServletContextEvent sce){
    stop = true;
  }
  /**
   * @return the name of this application.
   */
  public static String getName(){
    return name;
  }
  /**
   * @return the prefix used for constructing relative URL paths.
   */
  public static String getPrefix(){
    return prefix;
  }
  /**
   * Logs a message.
   */
  public synchronized static void log(String str){
    logger.println(str);
  }
  /**
   * Logs a message.
   */
  public synchronized static void log(String str, boolean error){
    logger.println(str);
  }
  /**
   * Logs an error.
   */
  public synchronized static void log(Throwable t){
    logger.println(t);
  }
}