package martian;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import java.util.Map;

public class Martian {
   private static IFn BOOTSTRAP;

   static {
      IFn require = Clojure.var("clojure.core", "require");
      require.invoke(Clojure.read("martian.core"));

      BOOTSTRAP = Clojure.var("martian.core", "bootstrap");
   }

   private final martian.protocols.Martian m;

   public Martian(String apiRoot, Map<String, Object> swaggerJson) {
      this.m = (martian.protocols.Martian) BOOTSTRAP.invoke(apiRoot, swaggerJson);
   }

   public String urlFor (String routeName) {
      return (String) m.url_for(routeName);
   }

   public String urlFor (String routeName, Map<String, Object> args) {
      return (String) m.url_for(routeName, args);
   }
}
