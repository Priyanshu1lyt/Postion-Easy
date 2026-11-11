package com.easypotions;

// Authors: NotHeartYT, Praveen_god_67, Nebrion_Verse

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;

public class EasyScreen extends Screen {
    private enum Mode { PLAYERS, PLAYER, EFFECTS, APPLY }
    private record Eff(Identifier id, StatusEffect effect, String name, boolean priority) {}
    private record Row(int y, Identifier id, String name) {}

    private static final int W = 260;
    private static final List<String> PRIORITY = List.of(
            "speed", "slowness", "strength", "instant_health", "instant_damage",
            "jump_boost", "regeneration", "resistance", "fire_resistance",
            "water_breathing", "night_vision", "invisibility", "haste",
            "mining_fatigue", "poison", "weakness", "slow_falling",
            "health_boost", "absorption", "luck");
    private static List<Eff> ALL;

    private Mode mode = Mode.PLAYERS;
    private boolean everyone;
    private String target = "";
    private int page, left;
    private String lastSig = "";
    private boolean headsOk = true, iconsOk = true;

    private TextFieldWidget search, levelField, durField;
    private Eff chosen;
    private boolean infinite, editing;
    private String levelText = "1", durText = "30";
    private final List<Row> rows = new ArrayList<>();

    public EasyScreen() {
        super(Text.literal("Easy Potions"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- effect data ----------
    private static List<Eff> allEffects() {
        if (ALL != null) return ALL;
        List<Eff> out = new ArrayList<>();
        for (StatusEffect e : Registries.STATUS_EFFECT) {
            Identifier id = Registries.STATUS_EFFECT.getId(e);
            if (id == null) continue;
            boolean pr = id.getNamespace().equals("minecraft") && PRIORITY.contains(id.getPath());
            out.add(new Eff(id, e, e.getName().getString(), pr));
        }
        out.sort((a, b) -> {
            if (a.priority() && b.priority())
                return Integer.compare(PRIORITY.indexOf(a.id().getPath()), PRIORITY.indexOf(b.id().getPath()));
            if (a.priority()) return -1;
            if (b.priority()) return 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        ALL = out;
        return out;
    }

    private static Eff find(Identifier id) {
        for (Eff e : allEffects()) if (e.id().equals(id)) return e;
        return null;
    }

    private static Identifier idOf(StatusEffectInstance i) {
        return i.getEffectType().getKey().map(k -> k.getValue()).orElse(null);
    }

    private List<StatusEffectInstance> effectsOf() {
        if (everyone || client == null || client.world == null) return List.of();
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p.getName().getString().equals(target)) {
                List<StatusEffectInstance> l = new ArrayList<>(p.getStatusEffects());
                l.removeIf(i -> idOf(i) == null);
                l.sort(Comparator.comparing(i -> idOf(i).toString()));
                return l;
            }
        }
        return List.of();
    }

    private boolean online(String name) {
        if (client == null || client.getNetworkHandler() == null) return false;
        for (PlayerListEntry e : client.getNetworkHandler().getPlayerList())
            if (e.getProfile().name().equals(name)) return true;
        return false;
    }

    // ---------- navigation ----------
    private void go(Mode m) {
        mode = m;
        page = 0;
        search = null;
        rebuild();
        if (search != null) {
            setFocused(search);
            search.setFocused(true);
        }
    }

    private void rebuild() {
        boolean focus = search != null && search.isFocused();
        clearChildren();
        rows.clear();
        left = width / 2 - W / 2;
        switch (mode) {
            case PLAYERS -> buildPlayers();
            case PLAYER -> buildPlayer();
            case EFFECTS -> buildEffects();
            case APPLY -> buildApply();
        }
        if (focus && search != null && children().contains(search)) {
            setFocused(search);
            search.setFocused(true);
        }
        lastSig = signature();
    }

    private String signature() {
        StringBuilder sb = new StringBuilder().append(mode).append(page);
        if (mode == Mode.PLAYERS && client != null && client.getNetworkHandler() != null)
            for (PlayerListEntry e : client.getNetworkHandler().getPlayerList())
                sb.append(e.getProfile().name()).append(',');
        if (mode == Mode.PLAYER)
            for (StatusEffectInstance i : effectsOf())
                sb.append(idOf(i)).append(i.getAmplifier()).append(i.isInfinite()).append(',');
        return sb.toString();
    }

    @Override
    public void tick() {
        super.tick();
        if (client == null || client.player == null || client.world == null
                || client.getNetworkHandler() == null) {
            close();
            return;
        }
        if (mode != Mode.PLAYERS && !everyone && !online(target)) {
            EasyPotionsClient.setStatus(target + " left the game.", 0xFFFF5555);
            go(Mode.PLAYERS);
            return;
        }
        if (!signature().equals(lastSig)) rebuild();
    }

    // ---------- helpers ----------
    private int perPage() {
        return Math.max(3, (height - 58 - 62) / 24);
    }

    private int[] slice(int total) {
        int pp = perPage();
        int pages = Math.max(1, (total + pp - 1) / pp);
        page = Math.max(0, Math.min(page, pages - 1));
        return new int[]{page * pp, Math.min(total, page * pp + pp), pages};
    }

    private void pager(int pages, String midLabel, Runnable mid) {
        int y = 58 + perPage() * 24;
        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuild(); })
                .dimensions(left, y, 40, 20).build();
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuild(); })
                .dimensions(left + W - 40, y, 40, 20).build();
        prev.active = page > 0;
        next.active = page < pages - 1;
        addDrawableChild(prev);
        addDrawableChild(next);
        addDrawableChild(ButtonWidget.builder(Text.literal(midLabel), b -> mid.run())
                .dimensions(left + 44, y, W - 88, 20).build());
    }

    private void button(Text t, int y, Runnable r) {
        addDrawableChild(ButtonWidget.builder(t, b -> r.run()).dimensions(left, y, W, 20).build());
    }

    private TextFieldWidget search(int y) {
        if (search == null) {
            search = new TextFieldWidget(textRenderer, left, y, W, 18, Text.literal("Search"));
            search.setPlaceholder(Text.literal("Search..."));
            search.setChangedListener(s -> { page = 0; rebuild(); });
        }
        search.setPosition(left, y);
        search.setWidth(W);
        return search;
    }

    private String query() {
        return search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
    }

    // ---------- screen 1: players ----------
    private void buildPlayers() {
        addDrawableChild(search(32));
        String q = query();
        List<String> names = new ArrayList<>();
        if (client.getNetworkHandler() != null)
            for (PlayerListEntry e : client.getNetworkHandler().getPlayerList())
                names.add(e.getProfile().name());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        names.removeIf(n -> !n.toLowerCase(Locale.ROOT).contains(q));

        List<String> items = new ArrayList<>();
        if (q.isEmpty() || "everyone".contains(q)) items.add(null); // null = EVERYONE
        items.addAll(names);

        int[] s = slice(items.size());
        for (int i = s[0]; i < s[1]; i++) {
            String n = items.get(i);
            int y = 58 + (i - s[0]) * 24;
            if (n == null) {
                button(Text.literal("★ EVERYONE ★").formatted(Formatting.GOLD, Formatting.BOLD), y, () -> {
                    everyone = true;
                    target = "@a";
                    go(Mode.PLAYER);
                });
            } else {
                rows.add(new Row(y, null, n));
                button(Text.literal(n), y, () -> {
                    everyone = false;
                    target = n;
                    go(Mode.PLAYER);
                });
            }
        }
        pager(s[2], "Close", this::close);
    }

    // ---------- screen 2: player's effects ----------
    private void buildPlayer() {
        button(Text.literal("+ Add Effect"), 32, () -> {
            editing = false;
            go(Mode.EFFECTS);
        });
        if (everyone) {
            button(Text.literal("Clear all effects from everyone"), 58,
                    () -> EasyPotionsClient.run("effect clear @a"));
            slice(0);
            pager(1, "Back", () -> go(Mode.PLAYERS));
            return;
        }
        List<StatusEffectInstance> list = effectsOf();
        int[] s = slice(list.size());
        for (int i = s[0]; i < s[1]; i++) {
            StatusEffectInstance inst = list.get(i);
            Identifier id = idOf(inst);
            int y = 58 + (i - s[0]) * 24;
            rows.add(new Row(y, id, inst.getEffectType().value().getName().getString()));
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"), b -> {
                Eff e = find(id);
                if (e == null) {
                    EasyPotionsClient.setStatus("Effect not available.", 0xFFFF5555);
                    return;
                }
                chosen = e;
                editing = true;
                levelText = String.valueOf(inst.getAmplifier() + 1);
                infinite = inst.isInfinite();
                durText = infinite ? "30" : String.valueOf(Math.max(1, inst.getDuration() / 20));
                go(Mode.APPLY);
            }).dimensions(left + W - 98, y, 44, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Remove").formatted(Formatting.RED), b ->
                    EasyPotionsClient.run("effect clear " + target + " " + id)
            ).dimensions(left + W - 52, y, 52, 20).build());
        }
        pager(s[2], "Back", () -> go(Mode.PLAYERS));
    }

    // ---------- screen 3: effect list ----------
    private void buildEffects() {
        addDrawableChild(search(32));
        String q = query();
        String qp = q.replace(' ', '_');
        List<Eff> items = new ArrayList<>();
        for (Eff e : allEffects())
            if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)
                    || e.id().getPath().contains(qp)) items.add(e);

        int[] s = slice(items.size());
        for (int i = s[0]; i < s[1]; i++) {
            Eff e = items.get(i);
            int y = 58 + (i - s[0]) * 24;
            rows.add(new Row(y, e.id(), e.name()));
            Text label = e.priority()
                    ? Text.literal("★ " + e.name()).formatted(Formatting.GOLD)
                    : Text.literal(e.name());
            button(label, y, () -> {
                chosen = e;
                editing = false;
                levelText = "1";
                durText = "30";
                infinite = false;
                go(Mode.APPLY);
            });
        }
        pager(s[2], "Back", () -> go(Mode.PLAYER));
    }

    // ---------- screen 4: apply ----------
    private int level() {
        try {
            return Math.max(1, Math.min(256, Integer.parseInt(levelText.trim())));
        } catch (Exception e) {
            return 1;
        }
    }

    private int secs() {
        try {
            return Math.max(1, Math.min(1_000_000, Integer.parseInt(durText.trim())));
        } catch (Exception e) {
            return 30;
        }
    }

    private void adjustLevel(int d) {
        levelText = String.valueOf(Math.max(1, Math.min(256, level() + d)));
        levelField.setText(levelText);
    }

    private void buildApply() {
        if (chosen == null) {
            go(Mode.EFFECTS);
            return;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> adjustLevel(-1))
                .dimensions(left, 66, 20, 20).build());
        levelField = new TextFieldWidget(textRenderer, left + 24, 66, W - 48, 20, Text.literal("Level"));
        levelField.setTextPredicate(s -> s.matches("\\d{0,3}"));
        levelField.setText(levelText);
        levelField.setChangedListener(s -> levelText = s);
        addDrawableChild(levelField);
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> adjustLevel(1))
                .dimensions(left + W - 20, 66, 20, 20).build());

        durField = new TextFieldWidget(textRenderer, left, 114, W, 20, Text.literal("Seconds"));
        durField.setTextPredicate(s -> s.matches("\\d{0,7}"));
        durField.setText(durText);
        durField.setChangedListener(s -> durText = s);
        durField.setEditable(!infinite);
        addDrawableChild(durField);

        addDrawableChild(CheckboxWidget.builder(Text.literal("Infinite"), textRenderer)
                .pos(left, 140).checked(infinite)
                .callback((cb, checked) -> {
                    infinite = checked;
                    durField.setEditable(!checked);
                }).build());

        button(Text.literal("Apply"), 170, this::apply);
        button(Text.literal("Back"), 194, () -> go(editing ? Mode.PLAYER : Mode.EFFECTS));
    }

    private void apply() {
        if (chosen == null) return;
        String tgt = everyone ? "@a" : target;
        String dur = infinite ? "infinite" : String.valueOf(secs());
        if (chosen.effect().isInstant()) dur = "1";
        if (editing) EasyPotionsClient.run("effect clear " + tgt + " " + chosen.id());
        EasyPotionsClient.run("effect give " + tgt + " " + chosen.id() + " " + dur + " " + (level() - 1));
        editing = false;
        go(Mode.PLAYER);
    }

    // ---------- rendering ----------
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int cx = width / 2;
        String title = switch (mode) {
            case PLAYERS -> "Easy Potions - Online Players";
            case PLAYER -> "Effects: " + (everyone ? "EVERYONE" : target);
            case EFFECTS -> "Choose Effect";
            case APPLY -> editing ? "Edit Effect" : "Apply Effect";
        };
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title), cx, 14, 0xFFFFFFFF);

        try {
            switch (mode) {
                case PLAYERS -> {
                    for (Row r : rows) drawHead(ctx, r.name(), left + 4, r.y() + 2);
                }
                case EFFECTS -> {
                    for (Row r : rows) drawIcon(ctx, r.id(), left + 4, r.y() + 2);
                }
                case PLAYER -> renderPlayer(ctx, cx);
                case APPLY -> renderApply(ctx);
            }
        } catch (Throwable ignored) {
        }

        long age = System.currentTimeMillis() - EasyPotionsClient.statusTime;
        if (!EasyPotionsClient.status.isEmpty() && age < 10000) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(EasyPotionsClient.status),
                    cx, height - 14, EasyPotionsClient.statusColor);
        }
    }

    private void renderPlayer(DrawContext ctx, int cx) {
        if (everyone) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Everything here applies to all online players."), cx, 86, 0xFFAAAAAA);
            return;
        }
        List<StatusEffectInstance> now = effectsOf();
        if (rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No active effects (or player is out of range)."), cx, 66, 0xFFAAAAAA);
            return;
        }
        for (Row r : rows) {
            StatusEffectInstance inst = null;
            for (StatusEffectInstance i : now) if (r.id().equals(idOf(i))) inst = i;
            if (inst == null) continue;
            ctx.fill(left, r.y(), left + W - 100, r.y() + 20, 0x80000000);
            drawIcon(ctx, r.id(), left + 2, r.y() + 2);
            String dur;
            if (inst.isInfinite()) dur = "Infinite";
            else {
                int s = inst.getDuration() / 20;
                dur = String.format("%d:%02d", s / 60, s % 60);
            }
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(r.name() + " " + (inst.getAmplifier() + 1) + "  " + dur),
                    left + 22, r.y() + 6, 0xFFFFFFFF);
        }
    }

    private void renderApply(DrawContext ctx) {
        if (chosen == null) return;
        drawIcon(ctx, chosen.id(), left, 34);
        ctx.drawTextWithShadow(textRenderer, Text.literal(chosen.name()), left + 20, 38, 0xFFFFAA00);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Level"), left, 56, 0xFFAAAAAA);
        int l = level();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Level " + l + "  (amplifier " + (l - 1) + ")"), left, 90, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(infinite ? "Duration: Infinite" : "Duration (seconds)"), left, 104, 0xFFAAAAAA);
    }

    private void drawHead(DrawContext ctx, String name, int x, int y) {
        if (!headsOk || client == null || client.getNetworkHandler() == null) return;
        try {
            PlayerListEntry e = client.getNetworkHandler().getPlayerListEntry(name);
            if (e != null) PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), x, y, 16);
        } catch (Throwable t) {
            headsOk = false;
        }
    }

    private void drawIcon(DrawContext ctx, Identifier id, int x, int y) {
        if (!iconsOk || id == null) return;
        try {
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
                    id.withPrefixedPath("mob_effect/"), x, y, 16, 16);
        } catch (Throwable t) {
            iconsOk = false;
        }
    }
                 }
