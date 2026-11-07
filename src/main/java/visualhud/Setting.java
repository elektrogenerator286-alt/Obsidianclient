package visualhud;

import com.google.gson.JsonObject;

import java.util.List;

/** Настройка пункта меню. Три вида: переключатель, ползунок, выбор из списка. */
public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    abstract void save(JsonObject o, String key);

    abstract void load(JsonObject o, String key);

    /** Переключатель (вкл/выкл). */
    public static final class Bool extends Setting {
        public boolean value;

        public Bool(String name, boolean def) {
            super(name);
            this.value = def;
        }

        public boolean get() {
            return value;
        }

        @Override void save(JsonObject o, String key) {
            o.addProperty(key, value);
        }

        @Override void load(JsonObject o, String key) {
            if (o.has(key)) value = o.get(key).getAsBoolean();
        }
    }

    /** Ползунок от min до max с шагом step. */
    public static final class Slider extends Setting {
        public final double min, max, step;
        private double value;

        public Slider(String name, double min, double max, double step, double def) {
            super(name);
            this.min = min;
            this.max = max;
            this.step = step;
            set(def);
        }

        public double get() {
            return value;
        }

        public void set(double v) {
            if (step > 0) v = Math.round((v - min) / step) * step + min;
            value = Math.max(min, Math.min(max, v));
        }

        public void setFraction(double f) {
            set(min + Math.max(0, Math.min(1, f)) * (max - min));
        }

        public double fraction() {
            return max > min ? (value - min) / (max - min) : 0;
        }

        @Override void save(JsonObject o, String key) {
            o.addProperty(key, value);
        }

        @Override void load(JsonObject o, String key) {
            if (o.has(key)) set(o.get(key).getAsDouble());
        }
    }

    /** Выбор одного значения из списка (клик: дальше, правый клик: назад). */
    public static final class Choice extends Setting {
        public final List<String> options;
        private int index;

        public Choice(String name, List<String> options, int def) {
            super(name);
            this.options = options;
            this.index = Math.max(0, Math.min(options.size() - 1, def));
        }

        public String get() {
            return options.get(index);
        }

        public int index() {
            return index;
        }

        public void next() {
            index = (index + 1) % options.size();
        }

        public void prev() {
            index = (index - 1 + options.size()) % options.size();
        }

        @Override void save(JsonObject o, String key) {
            o.addProperty(key, get());
        }

        @Override void load(JsonObject o, String key) {
            if (!o.has(key)) return;
            int i = options.indexOf(o.get(key).getAsString());
            if (i >= 0) index = i;
        }
    }
}
