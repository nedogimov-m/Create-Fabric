// Ponder 1.0.82 source scope, MIT; native26.3 actual persisted config adaptation.
package net.createmod.ponder.enums;

import com.simibubi.create.infrastructure.config.ConfigScope;
import com.simibubi.create.infrastructure.config.NativeConfigStore;
import net.createmod.ponder.config.CClient;
import net.fabricmc.loader.api.FabricLoader;

/** One actual Ponder client authority, using the already-installed native config persistence. */
public final class PonderConfig {
  private static final CClient CLIENT=new CClient();
  private static final NativeConfigStore STORE=new NativeConfigStore(ConfigScope.CLIENT,CLIENT);
  private static boolean registered;
  public static synchronized void registerConfigs(){
    if(registered)return;
    STORE.reload(FabricLoader.getInstance().getConfigDir().resolve("ponder-client.json"));
    registered=true;
  }
  public static CClient client(){
    if(!registered)throw new AssertionError("Ponder Client Config was accessed, but not registered yet!");
    return CLIENT;
  }
  public static NativeConfigStore store(){return STORE;}
  public static void reload(){
    var path=STORE.file().orElseThrow(()->new IllegalStateException("Ponder config has not been registered"));
    STORE.reload(path,new com.google.gson.JsonObject(),true);
  }
  private PonderConfig(){}
}
