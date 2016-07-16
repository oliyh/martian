package martian;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import java.util.Map;

public class Martian {
   private static IFn BOOTSTRAP_SWAGGER;
   private static IFn URL_FOR;

   static {
      IFn require = Clojure.var("clojure.core", "require");
      require.invoke(Clojure.read("martian.core"));

      BOOTSTRAP_SWAGGER = Clojure.var("martian.core", "bootstrap-swagger");
      URL_FOR = Clojure.var("martian.core", "url-for");
   }

   private final Object m;

   public Martian(String apiRoot, Map<String, Object> swaggerJson) {
      this.m = BOOTSTRAP_SWAGGER.invoke(apiRoot, swaggerJson);
   }

   public Martian(String apiRoot, Map<String, Object> swaggerJson, Map<String, Object> opts) {
      this.m = BOOTSTRAP_SWAGGER.invoke(apiRoot, swaggerJson, opts);
   }

   public String urlFor (String routeName) {
      return (String) URL_FOR.invoke(m, routeName);
   }

   public String urlFor (String routeName, Map<String, Object> params) {
      return (String) URL_FOR.invoke(m, routeName, params);
   }
}
