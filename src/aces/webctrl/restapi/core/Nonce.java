package aces.webctrl.restapi.core;
public class Nonce implements Comparable<Nonce> {
  private volatile String value;
  private volatile long timestamp;
  public Nonce(String value, long timestamp){
    this.value = value;
    this.timestamp = timestamp;
  }
  public String getValue(){
    return value;
  }
  public long getTimestamp(){
    return timestamp;
  }
  @Override public int compareTo(Nonce o) {
    return Long.compare(this.timestamp, o.timestamp);
  }
}