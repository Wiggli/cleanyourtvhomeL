package com.wiggli.gozofootball;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.wiggli.gozofootball.MatchdayFeed.array;
import static com.wiggli.gozofootball.MatchdayFeed.child;

public final class MatchdayActivity extends Activity {
    private static final int INK = 0xfff4f7fc;
    private static final int MUTED = 0xffaebdce;
    private static final int ACCENT = 0xff86d8ff;
    private static final int LIVE = 0xff82e6ad;
    private static final int SURFACE = 0xb82a3b52;
    private static final int SELECTED = 0xff284660;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter MATCH_DATE =
            DateTimeFormatter.ofPattern("EEE, d MMM 'at' HH:mm", Locale.ENGLISH);
    private enum Page { MATCHES, FAVOURITES, TABLES, DETAILS }

    private final ExecutorService feed = Executors.newFixedThreadPool(5);
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private final LruCache<Integer, Bitmap> crests = new LruCache<>(64);
    private final Map<Integer, List<WeakReference<ImageView>>> crestRequests = new HashMap<>();
    private final Set<Integer> unavailableCrests = new HashSet<>();
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable liveTick = new Runnable() {
        @Override public void run() {
            if (pending == 0) {
                if ((page == Page.MATCHES || page == Page.FAVOURITES)
                        && date.equals(LocalDate.now()) && hasActiveMatch()) loadDay(true);
                if (page == Page.DETAILS && active(selectedGame)) loadDetails(selectedGame, true);
            }
            clock.postDelayed(this, 60_000);
        }
    };
    private final List<Future<?>> requests = new ArrayList<>();
    private final Map<String, List<MatchdayFeed.Game>> games = new LinkedHashMap<>();
    private final Set<Integer> favourites = new HashSet<>();
    private LinearLayout root, controls, content;
    private ScrollView scroll;
    private LocalDate date;
    private Page page = Page.MATCHES;
    private Page returnPage = Page.MATCHES;
    private MatchdayFeed.Game selectedGame;
    private JSONObject details;
    private JSONArray standings;
    private String tableLeague;
    private String detailTab = "Timeline";
    private int generation, pending, failures;
    private boolean resumedBefore;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        date = LocalDate.now();
        root = column();
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff203850, 0xff101b2b, 0xff101827}));
        root.setPadding(dp(30), dp(17), dp(30), dp(16));
        root.addView(label("Football", 32, INK, true));
        controls = column();
        root.addView(controls);
        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setFillViewport(false);
        content = column();
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showDay();
    }

    @Override protected void onResume() {
        super.onResume();
        if (resumedBefore && page != Page.DETAILS) {
            if (page == Page.TABLES) showTable(null);
            else showDay();
        }
        resumedBefore = true;
        clock.postDelayed(liveTick, 60_000);
    }

    @Override protected void onPause() {
        clock.removeCallbacks(liveTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        cancelRequests();
        feed.shutdownNow();
        images.shutdownNow();
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int key, android.view.KeyEvent event) {
        if (key == android.view.KeyEvent.KEYCODE_MENU || key == android.view.KeyEvent.KEYCODE_SETTINGS) {
            settings();
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override public void onBackPressed() {
        if (page != Page.DETAILS) {
            super.onBackPressed();
            return;
        }
        cancelRequests();
        page = returnPage;
        header();
        renderDay();
        View previous = content.findViewWithTag(selectedGame.id);
        if (previous != null) previous.requestFocus();
    }

    private void cancelRequests() {
        generation++;
        for (Future<?> request : requests) request.cancel(true);
        requests.clear();
        pending = failures = 0;
    }

    private List<String> leagues() {
        SharedPreferences prefs = FootballConfig.preferences(this);
        List<String> enabled = new ArrayList<>();
        for (FootballConfig.Competition competition : FootballConfig.COMPETITIONS) {
            if (FootballConfig.isCompetitionEnabled(prefs, competition)) enabled.add(competition.slug);
        }
        favourites.clear();
        for (FootballConfig.Team team : FootballConfig.enabledTeams(prefs)) favourites.add(team.espnId);
        return enabled;
    }

    private void showDay() {
        loadDay(false);
    }

    private boolean hasActiveMatch() {
        for (List<MatchdayFeed.Game> group : games.values()) {
            for (MatchdayFeed.Game game : group) {
                if (active(game)) return true;
            }
        }
        return false;
    }

    private static boolean active(MatchdayFeed.Game game) {
        long now = System.currentTimeMillis();
        return "in".equals(game.state) || ("pre".equals(game.state)
                && game.time >= now - 15 * 60_000 && game.time <= now + 2 * 60_000);
    }

    private void loadDay(boolean inPlace) {
        cancelRequests();
        List<String> enabled = leagues();
        pending = enabled.size();
        if (!inPlace) {
            games.clear();
            for (String league : enabled) games.put(league, new ArrayList<>());
            header();
            renderDay();
        }
        final int token = generation;
        final LocalDate requestedDay = date;
        final java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        for (String league : enabled) {
            requests.add(feed.submit(() -> {
                List<MatchdayFeed.Game> result = null;
                try {
                    result = MatchdayFeed.day(league, requestedDay, zone);
                } catch (Exception ignored) {}
                List<MatchdayFeed.Game> loaded = result;
                runOnUiThread(() -> {
                    if (generation != token) return;
                    if (loaded != null) games.put(league, loaded);
                    else failures++;
                    if (--pending == 0) {
                        View focused = getCurrentFocus();
                        Object tag = focused == null ? null : focused.getTag();
                        int position = scroll.getScrollY();
                        renderDay();
                        if (inPlace) {
                            View previous = tag == null ? null : content.findViewWithTag(tag);
                            if (previous != null) previous.requestFocus();
                            scroll.post(() -> scroll.scrollTo(0, position));
                        }
                    }
                });
            }));
        }
    }

    private void header() {
        controls.removeAllViews();
        LinearLayout tabs = row();
        controls.addView(tabs);
        if (page == Page.DETAILS) {
            button(tabs, "‹ Matches", false, this::onBackPressed);
            for (String tab : new String[]{"Timeline", "Lineups", "Stats"}) {
                button(tabs, tab, tab.equals(detailTab), () -> {
                    detailTab = tab;
                    header();
                    renderDetails();
                });
            }
            return;
        }
        button(tabs, "Matches", page == Page.MATCHES, () -> changePage(Page.MATCHES));
        button(tabs, "Favourites", page == Page.FAVOURITES, () -> changePage(Page.FAVOURITES));
        button(tabs, "Tables", page == Page.TABLES, () -> changePage(Page.TABLES));
        button(tabs, "Settings", false, this::settings);
        if (page == Page.TABLES) {
            LinearLayout chooser = row();
            controls.addView(chooser);
            button(chooser, tableLeague == null ? "Choose competition"
                    : FootballConfig.competitionName(tableLeague) + "  ▾", false, this::chooseTable);
        } else {
            LinearLayout days = row();
            controls.addView(days);
            button(days, "‹ Previous", false, () -> { date = date.minusDays(1); showDay(); });
            button(days, "Today", date.equals(LocalDate.now()), () -> { date = LocalDate.now(); showDay(); });
            button(days, "Next ›", false, () -> { date = date.plusDays(1); showDay(); });
            button(days, "Refresh", false, this::showDay);
            if (page == Page.FAVOURITES) {
                button(days, "Team schedules", false,
                        () -> startActivity(new Intent(this, TeamSchedulesActivity.class)));
            }
        }
    }

    private void changePage(Page next) {
        if (page == next) return;
        page = next;
        if (next == Page.TABLES) showTable(null);
        else showDay();
    }

    private void settings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void renderDay() {
        resetContent();
        section(date.equals(LocalDate.now()) ? "Today  ·  " + DAY.format(date) : DAY.format(date));
        if (pending > 0) {
            note("Loading matches…");
            return;
        }
        boolean any = false;
        for (Map.Entry<String, List<MatchdayFeed.Game>> entry : games.entrySet()) {
            boolean heading = false;
            for (MatchdayFeed.Game game : entry.getValue()) {
                boolean favourite = favourites.contains(game.homeId) || favourites.contains(game.awayId);
                if (page == Page.FAVOURITES && !favourite) continue;
                if (!heading) {
                    section(FootballConfig.competitionName(entry.getKey()));
                    heading = true;
                }
                any = true;
                gameRow(game, favourite);
            }
        }
        if (!any && failures == 0) {
            note(games.isEmpty() ? "Choose competitions in Settings to see their matches."
                    : "No matches on this date. Try Previous or Next.");
        }
        if (failures > 0) {
            note(failures == games.size() ? "Scores are unavailable. Select Refresh to try again."
                    : "Some competitions are unavailable. Select Refresh to try again.");
        }
    }

    private void gameRow(MatchdayFeed.Game game, boolean favourite) {
        LinearLayout match = row();
        match.setTag(game.id);
        match.setPadding(dp(16), dp(7), dp(16), dp(7));
        match.setMinimumHeight(dp(48));
        match.setBackground(focusSurface(SURFACE));
        match.setFocusable(true);
        match.setOnClickListener(view -> openGame(game));
        String statusText = gameStatus(game);
        match.setContentDescription(game.home + " vs " + game.away + ", kickoff " + kickoff(game)
                + (statusText.isEmpty() ? "" : ", " + statusText));
        addFixed(match, label(kickoff(game), 17, MUTED, true), 84);
        addWeighted(match, teamMark(game.home, game.homeId, 18, 24));
        addWeighted(match, teamMark(game.away, game.awayId, 18, 24));
        TextView status = centred(statusText, 15, "in".equals(game.state) ? LIVE : INK);
        if (!statusText.isEmpty()) {
            status.setBackground(surface(0x40101827));
            status.setPadding(dp(8), dp(4), dp(8), dp(4));
        }
        addFixed(match, status, 120);
        TextView affordance = centred(favourite ? "★   ›" : "›", 19, favourite ? ACCENT : MUTED);
        addFixed(match, affordance, 50);
        addCard(content, match);
    }

    private String gameStatus(MatchdayFeed.Game game) {
        if ("pre".equals(game.state)) {
            return "Scheduled".equalsIgnoreCase(game.status) ? "" : game.status;
        }
        String status = "in".equals(game.state) && game.status.isEmpty() ? "LIVE" : game.status;
        return game.score.isEmpty() ? status : game.score + (status.isEmpty() ? "" : "  ·  " + status);
    }

    private String kickoff(MatchdayFeed.Game game) {
        return game.kickoffTbd ? "TBD"
                : TIME.format(Instant.ofEpochMilli(game.time).atZone(java.time.ZoneId.systemDefault()));
    }

    private void openGame(MatchdayFeed.Game game) {
        cancelRequests();
        returnPage = page;
        page = Page.DETAILS;
        selectedGame = game;
        detailTab = "Timeline";
        details = null;
        pending = 1;
        header();
        renderDetails();
        loadDetails(game, false);
    }

    private void loadDetails(MatchdayFeed.Game game, boolean inPlace) {
        requests.removeIf(Future::isDone);
        pending = 1;
        final int token = generation;
        requests.add(feed.submit(() -> {
            JSONObject result = null;
            try { result = MatchdayFeed.details(game); } catch (Exception ignored) {}
            JSONObject loaded = result;
            runOnUiThread(() -> {
                if (token != generation) return;
                pending = 0;
                if (loaded == null && inPlace) return;
                details = loaded;
                if (loaded != null) selectedGame = MatchdayFeed.updatedGame(game, loaded);
                int position = scroll.getScrollY();
                boolean restoreFocus = scroll.hasFocus();
                renderDetails();
                if (inPlace) {
                    if (restoreFocus) scroll.requestFocus();
                    scroll.post(() -> scroll.scrollTo(0, position));
                }
            });
        }));
    }

    private void renderDetails() {
        resetContent();
        section(FootballConfig.competitionName(selectedGame.league));
        String score = "pre".equals(selectedGame.state) || selectedGame.score.isEmpty()
                ? "vs" : selectedGame.score;
        LinearLayout hero = row();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(12), dp(18), dp(12));
        hero.setBackground(surface(SURFACE));
        addWeighted(hero, teamMark(selectedGame.home, selectedGame.homeId, 22, 38));
        TextView scoreboard = centred(score, 32, INK);
        scoreboard.setPadding(dp(20), dp(5), dp(20), dp(5));
        scoreboard.setBackground(surface(0x66101827));
        hero.addView(scoreboard);
        addWeighted(hero, teamMark(selectedGame.away, selectedGame.awayId, 22, 38));
        addCard(content, hero);
        String date = selectedGame.kickoffTbd ? DAY.format(Instant.ofEpochMilli(selectedGame.time)
                .atZone(java.time.ZoneId.systemDefault())) + "  ·  Kickoff TBD"
                : MATCH_DATE.format(Instant.ofEpochMilli(selectedGame.time).atZone(java.time.ZoneId.systemDefault()));
        String status = gameStatus(selectedGame);
        if (!"pre".equals(selectedGame.state)) status = selectedGame.status;
        note(date + (status.isEmpty() ? "" : "  ·  " + status));
        if (pending > 0) { note("Loading match details…"); return; }
        if (details == null) { note("Match details are unavailable. Go back and open the match to retry."); return; }
        switch (detailTab) {
            case "Lineups": lineups(); break;
            case "Stats": stats(); break;
            default: timeline(); break;
        }
        allowScrollFocus();
    }

    private void timeline() {
        JSONArray events = array(details, "keyEvents");
        section("Match timeline");
        boolean hasEvents = false;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            String type = child(event, "type").optString("type");
            if ("start-delay".equals(type) || "end-delay".equals(type)) continue;
            String description = event.optString("text", event.optString("shortText"));
            if (description.isEmpty()) continue;
            String time = child(event, "clock").optString("displayValue");
            dataRow(time.isEmpty() ? "•" : time, description);
            hasEvents = true;
        }
        if (!hasEvents) note("Match events have not been published.");
    }

    private void lineups() {
        JSONArray rosters = array(details, "rosters");
        if (rosters.length() == 0) { note("Lineups have not been published."); return; }
        LinearLayout sides = row();
        sides.setGravity(Gravity.TOP);
        addCard(content, sides);
        for (int i = 0; i < Math.min(2, rosters.length()); i++) {
            JSONObject team = rosters.optJSONObject(i);
            LinearLayout side = column();
            side.setPadding(dp(14), dp(10), dp(14), dp(10));
            side.setBackground(surface(SURFACE));
            LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i == 1) sideParams.setMarginStart(dp(10));
            sides.addView(side, sideParams);
            side.addView(label(child(team, "team").optString("displayName"), 20, INK, true));
            side.addView(label(MatchdayFeed.object(team, "formation"), 15, ACCENT, true));
            JSONArray players = array(team, "roster");
            for (boolean starter : new boolean[]{true, false}) {
                boolean heading = false;
                for (int j = 0; j < players.length(); j++) {
                    JSONObject player = players.optJSONObject(j);
                    if (player == null || player.optBoolean("starter") != starter) continue;
                    String name = child(player, "athlete").optString("displayName");
                    if (name.isEmpty()) continue;
                    if (!heading) {
                        side.addView(label(starter ? "Starting XI" : "Bench", 14, ACCENT, true));
                        heading = true;
                    }
                    side.addView(label(player.optString("jersey") + "   " + name, 16, INK, false));
                }
            }
        }
    }

    private void stats() {
        JSONArray teams = array(child(details, "boxscore"), "teams");
        JSONObject home = null;
        JSONObject away = null;
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.optJSONObject(i);
            int id = child(team, "team").optInt("id", -1);
            if (id == selectedGame.homeId) home = team;
            if (id == selectedGame.awayId) away = team;
        }
        JSONArray homeStats = array(home, "statistics");
        JSONArray awayStats = array(away, "statistics");
        if (homeStats.length() == 0 || awayStats.length() == 0) { note("Team stats are unavailable for this match."); return; }
        scoreRow(selectedGame.home, "Team stats", selectedGame.away);
        for (int i = 0; i < homeStats.length(); i++) {
            JSONObject stat = homeStats.optJSONObject(i);
            if (stat == null) continue;
            String left = stat.optString("displayValue", "—");
            String right = statValue(awayStats, stat.optString("name"));
            statRow(left, stat.optString("label", stat.optString("name")), right);
        }
    }

    private void chooseTable() {
        List<String> enabled = leagues();
        if (enabled.isEmpty()) { settings(); return; }
        String[] names = new String[enabled.size()];
        for (int i = 0; i < names.length; i++) names[i] = FootballConfig.competitionName(enabled.get(i));
        new android.app.AlertDialog.Builder(this).setTitle("League tables")
                .setItems(names, (dialog, which) -> showTable(enabled.get(which))).show();
    }

    private void showTable(String league) {
        cancelRequests();
        List<String> enabled = leagues();
        if (league != null) tableLeague = league;
        if (!enabled.contains(tableLeague)) tableLeague = enabled.isEmpty() ? null : enabled.get(0);
        standings = null;
        pending = tableLeague == null ? 0 : 1;
        header();
        renderTable();
        if (tableLeague == null) return;
        final int token = generation;
        final String requested = tableLeague;
        requests.add(feed.submit(() -> {
            JSONArray result = null;
            try { result = MatchdayFeed.standings(requested); } catch (Exception ignored) {}
            JSONArray loaded = result;
            runOnUiThread(() -> {
                if (token != generation) return;
                standings = loaded;
                pending = 0;
                renderTable();
            });
        }));
    }

    private void renderTable() {
        resetContent();
        if (pending > 0) { note("Loading table…"); return; }
        if (standings == null || standings.length() == 0) {
            note(tableLeague == null ? "Choose competitions in Settings." : "No table available for this competition.");
            return;
        }
        for (int i = 0; i < standings.length(); i++) drawGroup(standings.optJSONObject(i));
        if (content.getChildCount() == 0) note("No table available for this competition.");
        allowScrollFocus();
    }

    private void drawGroup(JSONObject group) {
        JSONArray entries = array(child(group, "standings"), "entries");
        if (entries.length() == 0) {
            JSONArray children = array(group, "children");
            for (int i = 0; i < children.length(); i++) drawGroup(children.optJSONObject(i));
            return;
        }
        section(group.optString("name", FootballConfig.competitionName(tableLeague)));
        tableRow("Pos", "Team", "Played", "GD", "Pts", false);
        for (int j = 0; j < entries.length(); j++) {
            JSONObject entry = entries.optJSONObject(j);
            if (entry == null) continue;
            JSONArray values = array(entry, "stats");
            JSONObject team = child(entry, "team");
            String name = team.optString("displayName");
            if (name.isEmpty()) continue;
            tableRow(statValue(values, "rank"), name, statValue(values, "gamesPlayed"),
                    statValue(values, "pointDifferential"), statValue(values, "points"),
                    favourites.contains(team.optInt("id")));
        }
    }

    private static String statValue(JSONArray values, String key) {
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null && key.equals(value.optString("name"))) return value.optString("displayValue", "—");
        }
        return "—";
    }

    private void tableRow(String position, String name, String played, String gd, String points, boolean favourite) {
        LinearLayout row = cardRow();
        addFixed(row, label(position, 15, MUTED, false), 65);
        addWeighted(row, label(name, 17, favourite ? ACCENT : INK, true));
        for (String value : new String[]{played, gd, points}) addFixed(row, centred(value, 16, INK), 78);
        addCard(content, row);
    }

    private void scoreRow(String left, String title, String right) {
        LinearLayout row = cardRow();
        fillScoreRow(row, left, title, right);
        addCard(content, row);
    }

    private void fillScoreRow(LinearLayout row, String left, String title, String right) {
        addWeighted(row, centred(left, 17, INK));
        addWeighted(row, centred(title, 16, MUTED));
        addWeighted(row, centred(right, 17, INK));
    }

    private void statRow(String left, String title, String right) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(6), dp(14), dp(9));
        card.setBackground(surface(SURFACE));
        LinearLayout values = row();
        fillScoreRow(values, left, title, right);
        card.addView(values);
        float home = numericStat(left);
        float away = numericStat(right);
        if (home >= 0 && away >= 0 && home + away > 0) {
            LinearLayout bar = row();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(4));
            params.setMargins(dp(30), dp(5), dp(30), 0);
            card.addView(bar, params);
            if (home > 0) addStatBar(bar, home, ACCENT);
            if (away > 0) addStatBar(bar, away, 0xffb6b0ee);
        }
        addCard(content, card);
    }

    private void addStatBar(LinearLayout bar, float value, int colour) {
        View segment = new View(this);
        segment.setBackground(surface(colour));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, value);
        params.setMargins(dp(2), 0, dp(2), 0);
        bar.addView(segment, params);
    }

    private static float numericStat(String value) {
        try {
            float number = Float.parseFloat(value.replace("%", "").trim());
            return Float.isFinite(number) ? number : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void dataRow(String time, String title) {
        LinearLayout row = cardRow();
        addFixed(row, label(time, 15, ACCENT, true), 78);
        addWeighted(row, label(title, 16, INK, false));
        addCard(content, row);
    }

    private void resetContent() {
        scroll.setFocusable(false);
        content.removeAllViews();
        scroll.scrollTo(0, 0);
    }

    private void allowScrollFocus() {
        scroll.setFocusable(true);
        scroll.setBackground(focusSurface(Color.TRANSPARENT));
    }

    private LinearLayout cardRow() {
        LinearLayout row = row();
        row.setPadding(dp(14), dp(6), dp(14), dp(6));
        row.setBackground(surface(SURFACE));
        return row;
    }

    private void section(String title) {
        TextView text = label(title, 18, ACCENT, true);
        text.setPadding(0, dp(10), 0, dp(6));
        content.addView(text);
    }

    private void note(String message) {
        TextView text = label(message, 16, MUTED, false);
        text.setPadding(0, dp(8), 0, dp(8));
        content.addView(text);
    }

    private void button(LinearLayout parent, String title, boolean active, Runnable action) {
        TextView button = centred(title, 15, active ? ACCENT : INK);
        button.setTag(title);
        button.setFocusable(true);
        button.setPadding(dp(14), dp(6), dp(14), dp(6));
        button.setMinHeight(dp(34));
        button.setBackground(focusSurface(active ? SELECTED : SURFACE));
        button.setOnClickListener(view -> {
            action.run();
            View replacement = controls.findViewWithTag(title);
            if (replacement != null) replacement.requestFocus();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, dp(5), dp(8), dp(4));
        parent.addView(button, params);
    }

    private void addCard(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(5);
        parent.addView(view, params);
    }

    private void addFixed(LinearLayout parent, TextView view, int width) {
        parent.addView(view, new LinearLayout.LayoutParams(dp(width), -2));
    }

    private void addWeighted(LinearLayout parent, View view) {
        parent.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private LinearLayout teamMark(String name, int id, int textSize, int iconSize) {
        LinearLayout team = row();
        team.setGravity(Gravity.CENTER_VERTICAL);
        ImageView crest = new ImageView(this);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setPadding(dp(2), dp(2), dp(2), dp(2));
        crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize));
        params.setMargins(dp(8), 0, dp(8), 0);
        team.addView(crest, params);
        team.addView(label(name, textSize, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        loadCrest(id, crest);
        return team;
    }

    private void loadCrest(int id, ImageView target) {
        if (id <= 0 || unavailableCrests.contains(id)) return;
        Bitmap cached = crests.get(id);
        if (cached != null) {
            displayCrest(target, cached);
            return;
        }
        List<WeakReference<ImageView>> waiting = crestRequests.get(id);
        if (waiting != null) {
            waiting.add(new WeakReference<>(target));
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(new WeakReference<>(target));
        crestRequests.put(id, waiting);
        images.execute(() -> {
            Bitmap image = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                        "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png").openConnection();
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(4000);
                if (connection.getResponseCode() == 200) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4;
                    try (java.io.InputStream body = connection.getInputStream()) {
                        image = BitmapFactory.decodeStream(body, null, options);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap loaded = image;
            runOnUiThread(() -> {
                List<WeakReference<ImageView>> targets = crestRequests.remove(id);
                if (loaded == null) {
                    unavailableCrests.add(id);
                    return;
                }
                crests.put(id, loaded);
                for (WeakReference<ImageView> reference : targets) {
                    ImageView view = reference.get();
                    if (view != null) displayCrest(view, loaded);
                }
            });
        });
    }

    private void displayCrest(ImageView view, Bitmap crest) {
        view.setBackground(surface(0xffdce5ef));
        view.setImageBitmap(crest);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, int colour, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(colour);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private TextView centred(String value, int size, int colour) {
        TextView text = label(value, size, colour, true);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private GradientDrawable surface(int colour) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(colour);
        background.setCornerRadius(dp(13));
        if (colour == SURFACE || colour == SELECTED) background.setStroke(dp(1), 0x25ffffff);
        return background;
    }

    private StateListDrawable focusSurface(int colour) {
        GradientDrawable focused = surface(colour == Color.TRANSPARENT ? colour : 0xff3a536e);
        focused.setStroke(dp(2), 0xffe8f4ff);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, surface(colour));
        return states;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
