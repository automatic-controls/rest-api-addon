package aces.webctrl.restapi.api.exec;
import aces.webctrl.restapi.core.*;
import aces.webctrl.restapi.api.*;
import java.util.*;
import java.util.regex.*;
import com.alibaba.fastjson2.*;
import com.controlj.green.core.data.*;
public class SearchGQL extends ApiBase {
  @Override
  public JSONObject exec(JSONObject input, ApiResponse res) throws Throwable {
    final JSONObject ret = new JSONObject();
    final long contextDBID = input.getLongValue("contextDBID", -1);
    final boolean fieldAccess = input.getBooleanValue("fieldAccess", false);
    JSONArray roots = input.getJSONArray("roots");
    JSONArray steps = input.getJSONArray("steps");
    HashSet<String> rootGQLs = new HashSet<>();
    HashSet<Long> searchQueue = new HashSet<>();
    if (roots == null || roots.isEmpty()) {
      rootGQLs.add("/trees/geographic");
    } else {
      for (Object root : roots) {
        if (root instanceof Number) {
          searchQueue.add(((Number) root).longValue());
        } else if (root instanceof String) {
          rootGQLs.add(((String) root).trim());
        }
      }
    }
    roots = null;
    final ArrayList<Step> stepList = new ArrayList<>(steps.size());
    for (Object o: steps) {
      stepList.add(new Step((JSONObject)o, res.isAdmin(), fieldAccess));
    }
    steps = null;
    if (!rootGQLs.isEmpty()) {
      try(
        DatabaseLink link = res.createLink(0);
      ){
        CoreNode ctx,n;
        if (contextDBID>=0){
          ctx = link.getNode(contextDBID);
        }else{
          ctx = link.getNode("/trees/geographic");
        }
        final Iterator<Long> it = searchQueue.iterator();
        long dbid;
        while (it.hasNext()){
          dbid = it.next();
          n = link.getNode(dbid);
          if (!res.isAdmin() && !n.hasViewPriv()){
            it.remove();
          }
        }
        if (res.isAdmin() || ctx.hasViewPriv()){
          for (String g: rootGQLs){
            n = ResolveGQL.evalToNode(ctx, g, !res.isAdmin(), false);
            if (n!=null){
              searchQueue.add(n.getDbid());
            }
          }
        }
      }
    }
    rootGQLs = null;
    final JSONArray dbids = new JSONArray(32);
    final JSONArray errors = new JSONArray();
    if (!searchQueue.isEmpty()) {
      try(
        final DatabaseLinkManager dlm = res.createLinkManager(fieldAccess?8:0, 2000L, false);
      ){
        HashSet<Long> nextQueue = new HashSet<>();
        for (Step s: stepList){
          s.search(dlm, searchQueue, nextQueue, errors);
          nextQueue = s.applyJump(dlm, nextQueue, errors);
          searchQueue = nextQueue;
          if (searchQueue.isEmpty()) {
            break;
          }
          nextQueue = new HashSet<>(Math.max(16, (int)Math.ceil(searchQueue.size()/0.75)));
        }
      }
      dbids.addAll(searchQueue);
      dbids.sort(new Comparator<Object>(){
        @Override public int compare(Object o1, Object o2){
          return Long.compare((Long)o1, (Long)o2);
        }
      });
    }
    ret.put("dbids", dbids);
    if (!errors.isEmpty()){
      ret.put("errors", errors);
    }
    return ret;
  }

  private static class Step {
    private final boolean includeRoots;
    private final Filter intermediateFilter;
    private final Filter leafFilter;
    private final String jump;
    private final boolean admin;
    private final boolean fieldAccess;
    public Step(JSONObject o, boolean admin, boolean fieldAccess) throws Throwable {
      includeRoots = o.getBooleanValue("includeRoots", false);
      intermediateFilter = new Filter(o.get("intermediateFilter"), admin, fieldAccess, false);
      leafFilter = new Filter(o.get("leafFilter"), admin, fieldAccess, true);
      jump = o.getString("jump");
      this.admin = admin;
      this.fieldAccess = fieldAccess;
    }
    public void search(DatabaseLinkManager dlm, HashSet<Long> searchQueue, HashSet<Long> nextQueue, JSONArray errors) throws Throwable {
      final ArrayDeque<Long> queue = new ArrayDeque<>();
      if (includeRoots){
        CoreNode n;
        for (Long dbid: searchQueue){
          n = dlm.getLink().getNode(dbid);
          if (n!=null && (admin || n.hasViewPriv())){
            try{
              if (intermediateFilter.test(n)){
                queue.add(dbid);
              }else if (leafFilter.test(n)){
                nextQueue.add(dbid);
              }
            }catch(Throwable t){
              final JSONObject err = new JSONObject(4);
              err.put("dbid", dbid);
              err.put("error", t.getMessage());
              errors.add(err);
            }
          }
        }
      }else{
        queue.addAll(searchQueue);
      }
      Long dbid;
      while ((dbid=queue.pollLast())!=null){
        final CoreNode n = dlm.getLink().getNode(dbid);
        if (n!=null && (admin || n.hasViewPriv())){
          for (CoreNode c: n.getChildren()){
            if (admin || c.hasViewPriv()){
              try{
                if (intermediateFilter.test(c)){
                  queue.addLast(c.getDbid());
                }else if (leafFilter.test(c)){
                  nextQueue.add(c.getDbid());
                }
              }catch(Throwable t){
                final JSONObject err = new JSONObject(4);
                err.put("dbid", dbid);
                err.put("error", t.getMessage());
                errors.add(err);
              }
            }
          }
        }
      }
    }
    public HashSet<Long> applyJump(DatabaseLinkManager dlm, HashSet<Long> dbids, JSONArray errors) throws Throwable {
      if (jump==null || dbids.isEmpty()){
        return dbids;
      }
      CoreNode n;
      final HashSet<Long> ret = new HashSet<>((int)Math.ceil(dbids.size()/0.75));
      for (Long dbid: dbids){
        n = dlm.getLink().getNode(dbid);
        if (n!=null && (admin || n.hasViewPriv())){
          try{
            n = ResolveGQL.evalToNode(n, jump, !admin, fieldAccess);
            if (n!=null){
              ret.add(n.getDbid());
            }
          }catch(CoreNotFoundException e){}catch(Throwable t){
            final JSONObject err = new JSONObject(5);
            err.put("dbid", dbid);
            err.put("expression", jump);
            err.put("error", t.getMessage());
            errors.add(err);
          }
        }
      }
      return ret;
    }
  }

  private static abstract class FilterNode {
    public abstract boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable;
  }

  private static class BooleanFilter extends FilterNode {
    private final boolean value;

    public BooleanFilter(boolean value) {
      this.value = value;
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      return value;
    }
  }

  private static class AndFilter extends FilterNode {
    private final List<FilterNode> children;

    public AndFilter(List<FilterNode> children) {
      this.children = children;
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      for (FilterNode child : children) {
        if (!child.test(node, admin, fieldAccess)) {
          return false;
        }
      }
      return true;
    }
  }

  private static class OrFilter extends FilterNode {
    private final List<FilterNode> children;

    public OrFilter(List<FilterNode> children) {
      this.children = children;
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      for (FilterNode child : children) {
        if (child.test(node, admin, fieldAccess)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class NotFilter extends FilterNode {
    private final FilterNode child;

    public NotFilter(FilterNode child) {
      this.child = child;
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      return !child.test(node, admin, fieldAccess);
    }
  }

  private static class HasGQLFilter extends FilterNode {
    private final String expression;

    public HasGQLFilter(String expression) {
      this.expression = expression;
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      try {
        return ResolveGQL.eval(node, expression, !admin, fieldAccess, false) != null;
      }catch(CoreNotFoundException t){
        return false;
      }
    }
  }

  private static class HasTypeFilter extends FilterNode {
    private final Set<Short> types;

    public HasTypeFilter(Set<Short> types) {
      this.types = types;
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      return types.contains(node.getNodeType());
    }
  }

  private static class ExpressionFilter extends FilterNode {
    private final static Pattern numberPattern = Pattern.compile("[\\-\\+]?+\\d++(?:\\.\\d++)?+");
    private int type = 0;
    private final String expression;
    private final String equalsString;
    private final Boolean equalsBoolean;
    private final Double equalsNumber;
    private final Pattern regexPattern;
    private final Double greaterThan;
    private final Double lessThan;
    private final Double greaterThanOrEqual;
    private final Double lessThanOrEqual;

    public ExpressionFilter(String expression, Object equalsValue, Pattern regexPattern,
        Double greaterThan, Double lessThan, Double greaterThanOrEqual, Double lessThanOrEqual) {
      this.expression = expression;
      this.regexPattern = regexPattern;
      this.equalsString = equalsValue instanceof String ? (String) equalsValue : null;
      this.equalsBoolean = equalsValue instanceof Boolean ? (Boolean) equalsValue : null;
      this.equalsNumber = equalsValue instanceof Number ? ((Number) equalsValue).doubleValue() : null;
      this.greaterThan = greaterThan;
      this.lessThan = lessThan;
      this.greaterThanOrEqual = greaterThanOrEqual;
      this.lessThanOrEqual = lessThanOrEqual;
      if (regexPattern != null) {
        type = 0;
      } else if (equalsString != null) {
        type = 1;
      } else if (equalsBoolean != null) {
        type = 2;
      } else if (equalsNumber != null) {
        type = 3;
      } else if (greaterThan != null) {
        type = 4;
      } else if (lessThan != null) {
        type = 5;
      } else if (greaterThanOrEqual != null) {
        type = 6;
      } else if (lessThanOrEqual != null) {
        type = 7;
      }
    }

    @Override
    public boolean test(CoreNode node, boolean admin, boolean fieldAccess) throws Throwable {
      String result;
      try {
        result = ResolveGQL.eval(node, expression, !admin, fieldAccess, false);
      } catch (CoreNotFoundException e) {
        return false;
      }
      if (result == null) {
        return false;
      }
      Double resultNum = null;
      if (type >= 3) {
        if (!numberPattern.matcher(result).matches()) {
          return false;
        }
        try {
          resultNum = Double.parseDouble(result);
        } catch (NumberFormatException e) {
          return false;
        }
      }
      switch (type) {
        case 0:
          return regexPattern.matcher(result).find();
        case 1:
          return result.equals(equalsString);
        case 2: {
          if (numberPattern.matcher(result).matches()){
            return (Double.parseDouble(result)!=0) == equalsBoolean;
          }else{
            return Boolean.parseBoolean(result) == equalsBoolean;
          }
        }
        case 3:
          return resultNum.equals(equalsNumber);
        case 4:
          return resultNum > greaterThan;
        case 5:
          return resultNum < lessThan;
        case 6:
          return resultNum >= greaterThanOrEqual;
        case 7:
          return resultNum <= lessThanOrEqual;
        default:
          return false;
      }
    }
  }

  private static class Filter {
    private final boolean admin;
    private final boolean fieldAccess;
    private final FilterNode root;

    public Filter(Object json, boolean admin, boolean fieldAccess, boolean defaultFilter) throws Throwable {
      this.admin = admin;
      this.fieldAccess = fieldAccess;
      this.root = json==null?new BooleanFilter(defaultFilter):buildFilterNode(json);
    }

    private static FilterNode buildFilterNode(Object obj) throws Throwable {
      // Handle the case where the JSON is just a boolean value
      if (obj instanceof Boolean) {
        return new BooleanFilter((Boolean) obj);
      }

      // If it's not a Boolean, it must be a JSONObject
      final JSONObject json = (JSONObject) obj;

      // Check for the first type of object (with and, or, not, hasGQL, hasType)
      if (json.containsKey("and")) {
        final JSONArray andArray = json.getJSONArray("and");
        final List<FilterNode> children = new ArrayList<>(andArray.size());
        for (Object child : andArray) {
          children.add(buildFilterNode(child));
        }
        return new AndFilter(children);
      } else if (json.containsKey("or")) {
        final JSONArray orArray = json.getJSONArray("or");
        final List<FilterNode> children = new ArrayList<>(orArray.size());
        for (Object child : orArray) {
          children.add(buildFilterNode(child));
        }
        return new OrFilter(children);
      } else if (json.containsKey("not")) {
        return new NotFilter(buildFilterNode(json.get("not")));
      } else if (json.containsKey("hasGQL")) {
        return new HasGQLFilter(json.getString("hasGQL"));
      } else if (json.containsKey("hasType")) {
        final JSONArray typesArray = json.getJSONArray("hasType");
        final Set<Short> types = new HashSet<>((int) Math.ceil(typesArray.size() / 0.75));
        for (Object o : typesArray) {
          if (o instanceof Number) {
            types.add(((Number) o).shortValue());
          } else if (o instanceof String) {
            try{
              types.add(NodeType.toNodeType((String)o));
            }catch(Throwable t){}
          }
        }
        return new HasTypeFilter(types);
      } else {
        // It's the expression type
        final String expression = json.getString("expression");
        final Object equalsValue = json.get("equals");
        final String regexStr = json.getString("regex");
        final Pattern regexPattern = regexStr == null ? null : Pattern.compile(regexStr);
        final Double greaterThan = json.getDouble("greaterThan");
        final Double lessThan = json.getDouble("lessThan");
        final Double greaterThanOrEqual = json.getDouble("greaterThanOrEqual");
        final Double lessThanOrEqual = json.getDouble("lessThanOrEqual");

        return new ExpressionFilter(expression, equalsValue, regexPattern,
            greaterThan, lessThan, greaterThanOrEqual, lessThanOrEqual);
      }
    }

    public boolean test(CoreNode node) throws Throwable {
      return root.test(node, admin, fieldAccess);
    }
  }
}