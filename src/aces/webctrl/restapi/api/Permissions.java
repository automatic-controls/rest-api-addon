package aces.webctrl.restapi.api;
public class Permissions {
  private volatile static int next = -1;
  public final static int GetSchema = ++next;
  public final static int ResolveGQL = ++next;
  public final static int ExecCommand = ++next;
  //public final static int SetGQL = ++next;
  //public final static int FindNodes = ++next;
}