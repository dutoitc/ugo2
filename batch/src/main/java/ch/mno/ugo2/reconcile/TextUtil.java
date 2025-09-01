package ch.mno.ugo2.reconcile;

public final class TextUtil {
  private TextUtil() {}
  public static String norm(String s) {
    if (s == null) return "";
    String t = s.toLowerCase();
    t = t.replaceAll("[\u0300-\u036f]", "");
    t = t.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\s+", " ");
    return t;
  }
}
