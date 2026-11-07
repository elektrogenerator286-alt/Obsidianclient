package visualhud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Реестр пунктов меню + сохранение их состояния в config/visualhud.json. */
public final class Features {
    private static final List<Feature> LIST = new ArrayList<>();

    private Features() {}

    public static void register(Feature f) {
        LIST.add(f);
    }

    public static List<Feature> list() {
        return LIST;
    }

    /** Включён ли пункт с таким названием (для использования в коде мода). */
    public static boolean enabled(String name) {
        for (Feature f : LIST) if (f.name.equals(name)) return f.enabled;
        return false;
    }

    /** Вызывается один раз при запуске: регистрирует пункты и подгружает сохранённые значения. */
    public static void init() {
        // Пока пунктов нет. Чтобы добавить пункт, зарегистрируйте его здесь, например:
        //
        // register(new Feature("Название пункта", true)
        //         .add(new Setting.Bool("Переключатель", true))
        //         .add(new Setting.Slider("Ползунок", 0, 10, 1, 5))
        //         .add(new Setting.Choice("Режим", List.of("Один", "Два"), 0)));
        load();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualhud.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Feature feature : LIST) feature.load(o);
        } catch (Exception ignored) {
            // повреждённый конфиг не должен ломать запуск
        }
    }

    public static void save() {
        if (LIST.isEmpty()) return;
        try {
            JsonObject o = new JsonObject();
            for (Feature feature : LIST) feature.save(o);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }
}
