package emu.grasscutter.loot;

import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.game.inventory.GameItem;
import it.unimi.dsi.fastutil.ints.IntIntPair;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntPredicate;

public class LootRegistry {
    public static final LootTable DEFAULT_LOOT = new LootTable() {
        @Override
        public List<GameItem> loot(LootContext ctx) {
            return List.of();
        }
    };


    private final String name;
    private final HashMap<IntPredicate, LootTable> rules;

    public LootRegistry(String name, HashMap<IntPredicate, LootTable> ret) {
        this.name = name;
        this.rules = ret;
    }

    public LootTable getLootTable (int matcher) {
        for (var k : rules.entrySet()) {
            if (k.getKey().test(matcher)) {
                return k.getValue();
            }
        }
        Grasscutter.getLogger().debug("Loot table not found for " + matcher + " in registry " + name);
        return DEFAULT_LOOT;
    }

    private static IntPredicate getLootTablePredicate(String tester) {
        var parts = tester.split(",");
        var cond = new ArrayList<IntIntPair>();
        for (var p : parts) {
            var f = p.split("-");
            if (f.length == 2) {
                cond.add(IntIntPair.of(Integer.parseInt(f[0]), Integer.parseInt(f[1])));
            } else if (f.length == 1) {
                cond.add(IntIntPair.of(Integer.parseInt(f[0]), Integer.parseInt(f[0])));
            } else {
                throw new RuntimeException("Invalid loot condition selector");
            }
        }
        return i -> cond.stream().anyMatch(c -> i >= c.firstInt() && i <= c.secondInt());
    }

    public static LootTable loadTableFromDisk(String name) {
        try (Reader fileReader = new InputStreamReader(DataLoader.load("loot/" + name))) {
            return Grasscutter.getGsonFactory().fromJson(fileReader, LootTable.class);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load drop data.", e);
            return DEFAULT_LOOT;
        }
    }

    public static LootRegistry getLootRegistry(String name) {
        try (Reader fileReader = new InputStreamReader(DataLoader.load(name))) {
            HashMap<IntPredicate, LootTable> ret = new HashMap<>();

            var elem = JsonParser.parseReader(fileReader);
            var obj = elem.getAsJsonObject();
            obj.entrySet().forEach(e -> {
                var value = e.getValue();
                if (value.isJsonObject()) {
                    ret.put(getLootTablePredicate(e.getKey()), Grasscutter.getGsonFactory().fromJson(value, LootTable.class));
                } else {
                    ret.put(getLootTablePredicate(e.getKey()), loadTableFromDisk(value.getAsString()));
                }
            });

            return new LootRegistry(name, ret);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load drop registry", e);
            return new LootRegistry(name, new HashMap<>());
        }
    }
}
