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

   private final IFn urlFor;

   public Martian(String apiRoot, Map<String, Object> swaggerJson) {
      this.urlFor = (IFn) BOOTSTRAP.invoke(apiRoot, swaggerJson);
   }

   public String urlFor (String routeName) {
      return (String) urlFor.invoke(routeName);
   }

   public String urlFor (String routeName, Map<String, Object> args) {
      return (String) urlFor.invoke(routeName, args);
   }
}
