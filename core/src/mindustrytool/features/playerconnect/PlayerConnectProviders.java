package mindustrytool.features.playerconnect;

import arc.Core;
import arc.struct.ArrayMap;
import mindustrytool.services.PlayerConnectService;

/** Порт: мёртвый код кастомных провайдеров (loadCustom/saveCustom) выброшен — его никто не звал. */
public class PlayerConnectProviders {
    public static final ArrayMap<String, String> online = new ArrayMap<>();
    private static final PlayerConnectService playerConnectService = PlayerConnectService.getInstance();

    public static synchronized void refreshOnline(Runnable onCompleted, arc.func.Cons<Throwable> onFailed) {
        playerConnectService.findPlayerConnectProvider().thenAccept(providers -> {
            Core.app.post(() -> {
                online.clear();

                for (var provider : providers) {
                    online.put(provider.getName(), provider.getAddress());
                }

                online.put("LocalHost", "localhost:11010");

                onCompleted.run();
            });
        }).exceptionally(error -> {
            onFailed.get(error);
            return null;
        });
    }
}
