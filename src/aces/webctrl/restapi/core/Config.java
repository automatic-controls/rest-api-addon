package aces.webctrl.restapi.core;
import java.util.*;
import java.util.function.Predicate;
import java.util.concurrent.locks.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.datatable.util.CoreHelper;
public class Config {
  /** The path to the saved data file. */
  private static volatile Path mainFile;
  public static volatile boolean verboseLogging = false;
  private final static HashMap<String,ApiKey> keys = new HashMap<>(32);
  private final static ReentrantReadWriteLock keyLock = new ReentrantReadWriteLock();
  /**
   * Sets the path to the saved data file and attempts to load any available data.
   */
  public static void init(Path mainFile){
    Config.mainFile = mainFile;
    loadData();
    try{
      final int size = keys.size();
      final Set<String> usernames = new CoreHelper().getOperatorList().keySet();
      keyLock.writeLock().lock();
      try{
        keys.values().removeIf(new Predicate<ApiKey>(){
          @Override public boolean test(ApiKey key) {
            final String op = key.getOperator();
            return !op.isEmpty() && !usernames.contains(op);
          }
        });
      }finally{
        keyLock.writeLock().unlock();
      }
      if (keys.size()!=size){
        saveData();
      }
    }catch(Throwable t){
      if (verboseLogging){
        Initializer.log(t);
      }
    }
  }
  /**
   * Returns the ApiKey object for the given public key, or null if not found.
   */
  public static ApiKey getKey(String publicKey){
    keyLock.readLock().lock();
    try{
      return keys.get(publicKey);
    }finally{
      keyLock.readLock().unlock();
    }
  }
  /**
   * Generate a new API key with the given name and permissions, and add it to the list of valid keys.
   */
  public static ApiKey newKey(String name, String operator, long perms){
    final ApiKey key = new ApiKey(name, operator, perms);
    keyLock.writeLock().lock();
    try{
      while (keys.containsKey(key.getPublicKey())){
        key.generatePublicKey();
      }
      keys.put(key.getPublicKey(), key);
    }finally{
      keyLock.writeLock().unlock();
    }
    return key;
  }
  /**
   * Deletes the API key with the given public key.
   * @return whether a key was deleted.
   */
  public static boolean deleteKey(String publicKey, String operator){
    keyLock.writeLock().lock();
    try{
      if (operator==null){
        return keys.remove(publicKey)!=null;
      }else{
        final ApiKey k = keys.get(publicKey);
        if (k==null){
          return false;
        }
        if (operator.equals(k.getOperator())){
          return keys.remove(publicKey)!=null;
        }else{
          return false;
        }
      }
    }finally{
      keyLock.writeLock().unlock();
    }
  }
  /**
   * @return a JSON array string containing information about all API keys.
   */
  public static String listKeys(String operator){
    final JSONArray arr = new JSONArray();
    keyLock.readLock().lock();
    try{
      String op;
      JSONObject o;
      for (ApiKey v: keys.values()){
        op = v.getOperator();
        if (operator==null || operator.equals(op)){
          o = new JSONObject();
          o.put("id", v.getPublicKey());
          o.put("name", v.getName());
          o.put("operator", op);
          o.put("perms", v.getPermissions());
          arr.add(o);
        }
      }
    }finally{
      keyLock.readLock().unlock();
    }
    return arr.toString();
  }
  /**
   * Load information from the saved data file.
   * @return whether data was loaded successfully.
   */
  public static boolean loadData(){
    if (mainFile==null){
      return false;
    }
    try{
      if (Files.exists(mainFile)){
        byte[] arr;
        synchronized(Config.class){
          arr = Files.readAllBytes(mainFile);
        }
        final SerializationStream s = new SerializationStream(arr);
        int size = s.readInt();
        int i;
        keyLock.writeLock().lock();
        try{
          keys.clear();
          ApiKey key;
          for (i=0;i<size;++i){
            key = new ApiKey(s.readString(true), s.readString(true), s.readString(true), s.readBytes(true), s.readLong());
            keys.put(key.getPublicKey(), key);
          }
        }finally{
          keyLock.writeLock().unlock();
        }
      }
      return true;
    }catch(Throwable t){
      Initializer.log("Error occurred while loading data.");
      Initializer.log(t);
      return false;
    }
  }
  /**
   * Writes information to the saved data file.
   * @return whether data was saved successfully.
   */
  public static boolean saveData(){
    if (mainFile==null){
      return false;
    }
    try{
      SerializationStream s = new SerializationStream(1024, true);
      synchronized(Config.class){
        keyLock.readLock().lock();
        try{
          s.write(keys.size());
          for (ApiKey v: keys.values()){
            s.write(v.getName(), true);
            s.write(v.getOperator(), true);
            s.write(v.getPublicKey(), true);
            s.write(v.getPrivateKey(), true, true);
            s.write(v.getPermissions());
          }
        }finally{
          keyLock.readLock().unlock();
        }
        ByteBuffer buf = s.getBuffer();
        try(
          FileChannel out = FileChannel.open(mainFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        ){
          while (buf.hasRemaining()){
            out.write(buf);
          }
        }
      }
      return true;
    }catch(Throwable t){
      Initializer.log(t);
      return false;
    }
  }
}