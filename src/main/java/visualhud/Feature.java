package visualhud;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Пункт меню: название, включён ли, и список его настроек. */
public final class Feature {
    public final String name;
    public boolean enabled;
    public boolean expanded;          // раскрыт ли список настроек в меню
    public final List<Setting> settings = new ArrayList<>();

    public Feature(String name, boolean enabledByDefault) {
        this.name = name;
        this.enabled = enabledByDefault;
    }

    public Feature add(Setting s) {
        settings.add(s);
        return this;
    }

    void save(JsonObject o) {
        o.addProperty(name + ".enabled", enabled);
        for (Setting s : settings) s.save(o, name + "." + s.name);
    }

    void load(JsonObject o) {
        if (o.has(name + ".enabled")) enabled = o.get(name + ".enabled").getAsBoolean();
        for (Setting s : settings) s.load(o, name + "." + s.name);
    }
}
