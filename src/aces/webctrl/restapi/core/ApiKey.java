package aces.webctrl.restapi.core;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
public class ApiKey {
  private volatile String name;
  private volatile String operator;
  private volatile byte[] privateKey;
  private volatile String publicKey;
  private volatile long perms;
  private volatile HashSet<String> nonceSet = new HashSet<>(43);
  private volatile PriorityQueue<Nonce> nonceQueue = new PriorityQueue<>(32);
  private volatile SecretKeySpec spec = null;
  public ApiKey(String name, String operator, String publicKey, byte[] privateKey, long perms){
    this.name = name;
    this.operator = operator;
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.perms = perms;
  }
  public ApiKey(String name, String operator){
    this(name, operator, -1);
  }
  public ApiKey(String name, String operator, long perms){
    this.name = name;
    this.operator = operator;
    this.perms = perms;
    privateKey = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    generatePublicKey();
  }
  public String getName(){
    return name;
  }
  public String getOperator(){
    return operator;
  }
  public byte[] getPrivateKey(){
    return privateKey;
  }
  public String getPublicKey(){
    return publicKey;
  }
  public String generatePublicKey(){
    publicKey = UUID.randomUUID().toString();
    return publicKey;
  }
  public long getPermissions(){
    return perms;
  }
  public boolean hasPermission(long p){
    return (perms&p)==p;
  }
  /**
   * Generates an HMAC-SHA256 hash of the given data using the given key.
   * The result is Base64 URL-safe encoded.
   */
  public String HMACSHA256(byte[] data) throws Throwable {
    if (spec==null){
      spec = new SecretKeySpec(privateKey, "HmacSHA256");
    }
    final Mac m = Mac.getInstance("HmacSHA256");
    m.init(spec);
    return Utility.base64UrlEncode(m.doFinal(data));
  }
  /**
   * Deletes nonces older than 5 minutes.
   */
  public synchronized void cleanNonces(long currentTime){
    final long threshold = currentTime-300000L;
    Nonce n;
    while ((n=nonceQueue.peek())!=null && n.getTimestamp()<threshold){
      n = nonceQueue.poll();
      nonceSet.remove(n.getValue());
    }
  }
  /**
   * @return true if the nonce was added, false if it already existed.
   */
  public synchronized boolean addNonce(String nonce, long timestamp) {
    if (!nonceSet.add(nonce)){
      return false;
    }
    nonceQueue.add(new Nonce(nonce,timestamp));
    return true;
  }
  /**
   * @return the number of nonces currently stored.
   */
  public int getNonceCount(){
    return nonceSet.size();
  }
}