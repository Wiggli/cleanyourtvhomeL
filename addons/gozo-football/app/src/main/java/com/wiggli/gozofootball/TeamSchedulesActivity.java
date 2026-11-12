package com.wiggli.gozofootball;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamSchedulesActivity extends Activity {
    private static final long CACHE_MS = 15L * 60L * 1000L;
    private static final String PREFS = "gozo_football_cache";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<Integer, Bitmap> teamLogos = new ConcurrentHashMap<>();
    private FootballView footballView;
    private List<FootballConfig.Team> enabledTeams = new ArrayList<>();
    private String configurationSignature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        enabledTeams = FootballConfig.enabledTeams(FootballConfig.preferences(this));
        configurationSignature = configurationSignature();
        footballView = new FootballView();
        setContentView(footballView);
        loadTeamLogos();
        loadCache();
        long updated = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("updated", 0L);
        if (System.currentTimeMillis() - updated > CACHE_MS || !footballView.hasAnyData()) {
            refresh(false);
        }
    }

    private void loadTeamLogos() {
        for (FootballConfig.Team team : enabledTeams) {
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("https://a.espncdn.com/i/teamlogos/soccer/500/" + team.espnId + ".png");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(6000);
                    if (connection.getResponseCode() / 100 != 2) return;
                    Bitmap logo = BitmapFactory.decodeStream(connection.getInputStream());
                    if (logo != null) {
                        teamLogos.put(team.espnId, logo);
                        runOnUiThread(() -> footballView.invalidate());
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (footballView == null) return;
        String updatedSignature = configurationSignature();
        if (!updatedSignature.equals(configurationSignature)) {
            List<FootballConfig.Team> updated = FootballConfig.enabledTeams(FootballConfig.preferences(this));
            enabledTeams = updated;
            configurationSignature = updatedSignature;
            footballView.selected = 0;
            footballView.data.clear();
            loadCache();
            footballView.invalidate();
            refresh(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            footballView.selected = (footballView.selected + enabledTeams.size() - 1) % enabledTeams.size();
            footballView.invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            footballView.selected = (footballView.selected + 1) % enabledTeams.size();
            footballView.invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            footballView.moveSection(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            footballView.moveSection(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            refresh(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private String configurationSignature() {
        SharedPreferences preferences = FootballConfig.preferences(this);
        StringBuilder signature = new StringBuilder();
        for (FootballConfig.Team team : FootballConfig.TEAMS) {
            signature.append(FootballConfig.isTeamEnabled(preferences, team) ? '1' : '0');
        }
        signature.append('|');
        for (FootballConfig.Competition competition : FootballConfig.COMPETITIONS) {
            signature.append(FootballConfig.isCompetitionEnabled(preferences, competition) ? '1' : '0');
        }
        return signature.toString();
    }

    private void loadCache() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences configuration = FootballConfig.preferences(this);
        for (int i = 0; i < enabledTeams.size(); i++) {
            FootballConfig.Team team = enabledTeams.get(i);
            String raw = prefs.getString("team_" + team.espnId, null);
            if (raw == null) continue;
            try {
                Set<String> enabledCompetitionNames = new HashSet<>();
                for (String slug : FootballConfig.enabledCompetitions(configuration, team)) {
                    enabledCompetitionNames.add(FootballConfig.competitionName(slug));
                }
                JSONArray array = new JSONArray(raw);
                List<Match> matches = new ArrayList<>();
                for (int j = 0; j < array.length(); j++) {
                    Match match = Match.fromJson(array.getJSONObject(j));
                    if (match != null && enabledCompetitionNames.contains(match.competition)) matches.add(match);
                }
                footballView.data.put(i, matches);
            } catch (Exception ignored) {
            }
        }
        footballView.lastUpdated = prefs.getLong("updated", 0L);
        footballView.invalidate();
    }

    private void refresh(boolean forced) {
        if (footballView.loading) return;
        footballView.loading = true;
        footballView.invalidate();

        List<Future<TeamResult>> futures = new ArrayList<>();
        SharedPreferences preferences = FootballConfig.preferences(this);
        for (int i = 0; i < enabledTeams.size(); i++) {
            final int index = i;
            FootballConfig.Team team = enabledTeams.get(i);
            futures.add(executor.submit(() -> new TeamResult(index, fetchTeam(team, preferences))));
        }

        executor.execute(() -> {
            Map<Integer, List<Match>> fresh = new HashMap<>();
            for (Future<TeamResult> future : futures) {
                try {
                    TeamResult result = future.get();
                    if (!result.matches.isEmpty()) fresh.put(result.index, result.matches);
                } catch (Exception ignored) {
                }
            }

            long now = System.currentTimeMillis();
            if (!fresh.isEmpty()) {
                android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                for (Map.Entry<Integer, List<Match>> entry : fresh.entrySet()) {
                    JSONArray array = new JSONArray();
                    for (Match match : entry.getValue()) array.put(match.toJson());
                    editor.putString("team_" + enabledTeams.get(entry.getKey()).espnId, array.toString());
                }
                editor.putLong("updated", now).apply();
            }

            runOnUiThread(() -> {
                if (!fresh.isEmpty()) {
                    footballView.data.putAll(fresh);
                    footballView.lastUpdated = now;
                    footballView.message = null;
                } else if (!footballView.hasAnyData()) {
                    footballView.message = "Fixtures are unavailable right now";
                }
                footballView.loading = false;
                footballView.invalidate();
            });
        });
    }

    private List<Match> fetchTeam(FootballConfig.Team team, SharedPreferences preferences) {
        Map<String, Match> unique = new LinkedHashMap<>();
        for (String competition : FootballConfig.enabledCompetitions(preferences, team)) {
            for (boolean fixtures : new boolean[]{false, true}) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                            + competition + "/teams/" + team.espnId + "/schedule"
                            + (fixtures ? "?fixture=true" : ""));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(6000);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "GozoFootball/2.1 AndroidTV");
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) continue;

                    StringBuilder body = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }
                    JSONObject root = new JSONObject(body.toString());
                    JSONArray events = root.optJSONArray("events");
                    if (events == null) continue;
                    for (int i = 0; i < events.length(); i++) {
                        Match match = parseMatch(events.optJSONObject(i), team, competition);
                        if (match != null) unique.put(match.id, match);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }

        List<Match> matches = new ArrayList<>(unique.values());
        matches.sort(Comparator.comparingLong(m -> m.kickoff));
        long now = System.currentTimeMillis();
        long earliest = now - 14L * 24L * 60L * 60L * 1000L;
        long latest = now + 180L * 24L * 60L * 60L * 1000L;
        matches.removeIf(m -> m.kickoff < earliest || m.kickoff > latest);
        return matches;
    }

    private Match parseMatch(JSONObject event, FootballConfig.Team team, String competitionSlug) {
        if (event == null) return null;
        try {
            String id = event.optString("id", "");
            String date = event.optString("date", "");
            if (id.isEmpty() || date.isEmpty()) return null;
            long kickoff = FootballConfig.parseKickoffMillis(date);

            JSONArray competitions = event.optJSONArray("competitions");
            if (competitions == null || competitions.length() == 0) return null;
            JSONObject competition = competitions.optJSONObject(0);
            JSONArray competitors = competition == null ? null : competition.optJSONArray("competitors");
            if (competitors == null) return null;

            JSONObject status = competition.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            String state = type == null ? "pre" : type.optString("state", "pre");
            String statusDetail = type == null ? "" : type.optString("shortDetail", "");

            String opponent = null;
            String ourScore = "";
            String theirScore = "";
            boolean home = false;
            boolean foundTeam = false;
            for (int i = 0; i < competitors.length(); i++) {
                JSONObject competitor = competitors.optJSONObject(i);
                if (competitor == null) continue;
                JSONObject teamJson = competitor.optJSONObject("team");
                if (teamJson == null) continue;
                boolean ours = String.valueOf(team.espnId).equals(teamJson.optString("id"));
                JSONObject scoreJson = competitor.optJSONObject("score");
                String score = scoreJson == null
                        ? competitor.optString("score", "") : scoreJson.optString("displayValue", "");
                if (ours) {
                    foundTeam = true;
                    ourScore = score;
                    home = "home".equals(competitor.optString("homeAway"));
                } else {
                    opponent = teamJson.optString("displayName", teamJson.optString("name", "Opponent"));
                    theirScore = score;
                }
            }
            if (!foundTeam || opponent == null) return null;
            return new Match(id, kickoff, opponent, FootballConfig.competitionName(competitionSlug), state,
                    statusDetail, ourScore, theirScore, home);
        } catch (Exception ignored) {
            return null;
        }
    }

    private final class FootballView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<Integer, List<Match>> data = new HashMap<>();
        private int selected = 0;
        private int section = 0;
        private boolean loading = false;
        private long lastUpdated = 0L;
        private String message;

        FootballView() {
            super(TeamSchedulesActivity.this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }

        boolean hasAnyData() {
            for (List<Match> list : data.values()) if (list != null && !list.isEmpty()) return true;
            return false;
        }

        void moveSection(int amount) {
            section = (section + amount + 3) % 3;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 1920f, getHeight() / 1080f);
            float dx = (getWidth() - 1920f * scale) / 2f;
            float dy = (getHeight() - 1080f * scale) / 2f;
            canvas.save();
            canvas.translate(dx, dy);
            canvas.scale(scale, scale);

            paint.setColor(Color.argb(28, 218, 41, 28));
            canvas.drawCircle(1690, 100, 390, paint);
            paint.setColor(Color.argb(24, 0, 102, 179));
            canvas.drawCircle(150, 980, 460, paint);
            drawRound(canvas, 90, 65, 1740, 950, 46, Color.argb(228, 14, 18, 27));
            strokeRound(canvas, 90, 65, 1740, 950, 46, Color.argb(70, 255, 255, 255), 2);

            text(canvas, "Football", 150, 145, 48, Color.WHITE, true);
            text(canvas, "Match centre", 150, 187, 24, Color.argb(185, 255, 255, 255), false);
            String status = loading ? "Updating fixtures…" : updatedLabel();
            textRight(canvas, status, 1765, 150, 22, Color.argb(170, 255, 255, 255));

            float gap = 24f;
            float pillWidth = Math.min(360f,
                    (1615f - gap * (enabledTeams.size() - 1)) / enabledTeams.size());
            float pillsWidth = pillWidth * enabledTeams.size() + gap * (enabledTeams.size() - 1);
            float pillStart = 150f + (1615f - pillsWidth) / 2f;
            for (int i = 0; i < enabledTeams.size(); i++) {
                drawTeamPill(canvas, pillStart + i * (pillWidth + gap), 220, pillWidth, 82, i);
            }

            FootballConfig.Team team = enabledTeams.get(selected);
            List<Match> matches = data.getOrDefault(selected, Collections.emptyList());
            Match next = nextMatch(matches);
            Match previous = previousMatch(matches);

            drawSectionTabs(canvas);
            if (section == 1) {
                drawFixtures(canvas, team, matches);
                drawControls(canvas);
                canvas.restore();
                return;
            }
            if (section == 2) {
                drawResults(canvas, team, matches);
                drawControls(canvas);
                canvas.restore();
                return;
            }

            drawRound(canvas, 150, 335, 900, 590, 34, Color.argb(218, 15, 27, 42));
            strokeRound(canvas, 150, 335, 900, 590, 34, Color.argb(80, 255, 255, 255), 2);
            drawTeamMark(canvas, team, 220, 410, 72);
            text(canvas, team.name, 282, 405, 38, Color.WHITE, true);

            if (next != null) {
                text(canvas, next.competition, 200, 475, 24, Color.argb(185, 255, 255, 255), false);
                text(canvas, next.home ? team.name + "  vs  " + next.opponent : next.opponent + "  vs  " + team.name,
                        200, 535, 34, Color.WHITE, true);
                String when = "in".equals(next.state)
                        ? "LIVE  ·  " + score(next.ourScore) + " – " + score(next.theirScore)
                        + (next.statusDetail.isEmpty() ? "" : "  ·  " + next.statusDetail)
                        : formatWhen(next.kickoff);
                text(canvas, when, 200, 585, 27,
                        "in".equals(next.state) ? 0xFFFFB4A8 : Color.argb(235, 255, 255, 255), false);
                String countdown = "in".equals(next.state) ? "" : countdown(next.kickoff);
                if (!countdown.isEmpty()) textRight(canvas, countdown, 1000, 585, 27, team.color);
            } else {
                text(canvas, message == null ? "No upcoming fixture found" : message,
                        200, 525, 30, Color.argb(210, 255, 255, 255), false);
            }

            drawRound(canvas, 1090, 335, 675, 485, 34, Color.argb(210, 10, 21, 34));
            strokeRound(canvas, 1090, 335, 675, 485, 34, Color.argb(65, 255, 255, 255), 2);
            text(canvas, "Coming up", 1140, 395, 28, Color.WHITE, true);
            List<Match> upcoming = upcoming(matches, 3);
            float rowY = 452;
            if (upcoming.isEmpty()) {
                text(canvas, "No fixtures loaded", 1140, 500, 24, Color.argb(180, 255, 255, 255), false);
            } else {
                for (Match match : upcoming) {
                    text(canvas, shortDate(match.kickoff), 1140, rowY, 21, Color.argb(170, 255, 255, 255), false);
                    text(canvas, match.opponent, 1325, rowY, 24, Color.WHITE, true, 295);
                    textRight(canvas, shortTime(match.kickoff), 1710, rowY, 22, Color.argb(210, 255, 255, 255));
                    text(canvas, match.competition, 1325, rowY + 30, 18,
                            Color.argb(145, 255, 255, 255), false, 385);
                    rowY += 112;
                }
            }

            drawRound(canvas, 190, 665, 820, 200, 30, Color.argb(205, 20, 31, 46));
            text(canvas, "Latest result", 225, 715, 24, Color.argb(175, 255, 255, 255), false);
            if (previous != null) {
                String result = team.name + "  " + score(previous.ourScore) + "  –  "
                        + score(previous.theirScore) + "  " + previous.opponent;
                text(canvas, result, 225, 770, 30, Color.WHITE, true, 750);
                text(canvas, previous.competition, 225, 817, 21, Color.argb(160, 255, 255, 255), false);
            } else {
                text(canvas, "No recent result loaded", 225, 770, 27, Color.argb(190, 255, 255, 255), false);
            }

            drawControls(canvas);

            text(canvas, "Fixtures via ESPN public schedule endpoints • cached for 15 minutes",
                    150, 960, 18, Color.argb(110, 255, 255, 255), false);
            canvas.restore();
        }

        private void drawSectionTabs(Canvas canvas) {
            String[] labels = {"OVERVIEW", "FIXTURES", "RESULTS"};
            float x = 700;
            for (int i = 0; i < labels.length; i++) {
                boolean active = section == i;
                drawRound(canvas, x + i * 185, 112, 165, 54, 18,
                        active ? Color.argb(220, 66, 78, 94) : Color.argb(190, 27, 36, 49));
                strokeRound(canvas, x + i * 185, 112, 165, 54, 18,
                        active ? Color.argb(180, 255, 255, 255) : Color.argb(24, 255, 255, 255),
                        active ? 2 : 1);
                textCentered(canvas, labels[i], x + i * 185 + 82, 147, 17,
                        active ? Color.WHITE : Color.argb(150, 255, 255, 255), true);
            }
        }

        private void drawFixtures(Canvas canvas, FootballConfig.Team team, List<Match> matches) {
            drawRound(canvas, 150, 335, 1615, 590, 34, Color.argb(218, 15, 27, 42));
            strokeRound(canvas, 150, 335, 1615, 590, 34, Color.argb(65, 255, 255, 255), 2);
            text(canvas, "Upcoming fixtures", 205, 405, 32, Color.WHITE, true);
            List<Match> fixtures = upcoming(matches, 6);
            if (fixtures.isEmpty()) {
                text(canvas, message == null ? "No upcoming fixtures loaded" : message,
                        205, 500, 28, Color.argb(190, 255, 255, 255), false);
                return;
            }
            float y = 455;
            for (Match match : fixtures) {
                boolean live = "in".equals(match.state);
                drawRound(canvas, 195, y, 1525, 66, 19,
                        live ? Color.argb(70, 218, 41, 28) : Color.argb(190, 25, 38, 54));
                text(canvas, live ? "LIVE" : shortDate(match.kickoff), 225, y + 41, 18,
                        live ? 0xFFFFA99E : Color.argb(170, 255, 255, 255), live);
                String pairing = match.home ? team.name + "  v  " + match.opponent
                        : match.opponent + "  v  " + team.name;
                text(canvas, pairing, 420, y + 40, 23, Color.WHITE, true);
                textRight(canvas, live ? score(match.ourScore) + " – " + score(match.theirScore)
                                : shortTime(match.kickoff), 1660, y + 39, 22, Color.WHITE);
                textRight(canvas, match.competition, 1435, y + 39, 17,
                        Color.argb(145, 255, 255, 255));
                y += 78;
            }
        }

        private void drawResults(Canvas canvas, FootballConfig.Team team, List<Match> matches) {
            drawRound(canvas, 150, 335, 1615, 590, 34, Color.argb(218, 15, 27, 42));
            strokeRound(canvas, 150, 335, 1615, 590, 34, Color.argb(65, 255, 255, 255), 2);
            text(canvas, "Recent results", 205, 405, 32, Color.WHITE, true);
            List<Match> results = results(matches, 6);
            if (results.isEmpty()) {
                text(canvas, "No recent results loaded", 205, 500, 28,
                        Color.argb(190, 255, 255, 255), false);
                return;
            }
            float y = 455;
            for (Match match : results) {
                drawRound(canvas, 195, y, 1525, 66, 19, Color.argb(190, 25, 38, 54));
                text(canvas, shortDate(match.kickoff), 225, y + 41, 18,
                        Color.argb(170, 255, 255, 255), false);
                String pairing = match.home ? team.name + "  " + score(match.ourScore) + " – "
                        + score(match.theirScore) + "  " + match.opponent
                        : match.opponent + "  " + score(match.theirScore) + " – "
                        + score(match.ourScore) + "  " + team.name;
                text(canvas, pairing, 420, y + 40, 23, Color.WHITE, true);
                textRight(canvas, match.competition, 1660, y + 39, 18,
                        Color.argb(150, 255, 255, 255));
                y += 78;
            }
        }

        private void drawControls(Canvas canvas) {
            drawRound(canvas, 1090, 941, 675, 54, 20, Color.argb(205, 21, 33, 48));
            text(canvas, "←  →  team   ·   ↑  ↓  view", 1130, 975, 19,
                    Color.argb(195, 255, 255, 255), false);
            textRight(canvas, "Settings key   ·   OK refresh", 1720, 975, 19,
                    Color.argb(195, 255, 255, 255));
        }

        private void drawTeamPill(Canvas canvas, float x, float y, float w, float h, int index) {
            FootballConfig.Team team = enabledTeams.get(index);
            boolean active = index == selected;
            int fill = active ? Color.argb(225, Color.red(team.color), Color.green(team.color), Color.blue(team.color))
                    : Color.argb(205, 39, 49, 63);
            drawRound(canvas, x, y, w, h, 26, fill);
            strokeRound(canvas, x, y, w, h, 26,
                    active ? Color.argb(210, 255, 255, 255) : Color.argb(45, 255, 255, 255), active ? 3 : 2);
            drawTeamMark(canvas, team, x + 48, y + h / 2, 48);
            text(canvas, team.name, x + 88, y + 52, 22, Color.WHITE, active);
        }

        private void drawTeamMark(Canvas canvas, FootballConfig.Team team, float cx, float cy, float size) {
            paint.setColor(Color.argb(225, 245, 247, 250));
            canvas.drawCircle(cx, cy, size / 2, paint);
            Bitmap logo = teamLogos.get(team.espnId);
            if (logo != null) {
                float inset = size * 0.13f;
                canvas.drawBitmap(logo, null,
                        new RectF(cx - size / 2 + inset, cy - size / 2 + inset,
                                cx + size / 2 - inset, cy + size / 2 - inset), paint);
            } else {
                textCentered(canvas, team.shortName, cx, cy + size * 0.12f,
                        Math.max(15, size * 0.27f), team.color, true);
            }
        }

        private String updatedLabel() {
            if (lastUpdated == 0L) return "Waiting for fixtures";
            long mins = Math.max(0L, (System.currentTimeMillis() - lastUpdated) / 60000L);
            if (mins < 1) return "Updated now";
            if (mins == 1) return "Updated 1 min ago";
            return "Updated " + mins + " min ago";
        }

        private void drawRound(Canvas c, float x, float y, float w, float h, float r, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            c.drawRoundRect(new RectF(x, y, x + w, y + h), r, r, paint);
        }

        private void strokeRound(Canvas c, float x, float y, float w, float h, float r, int color, float width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
            paint.setColor(color);
            c.drawRoundRect(new RectF(x, y, x + w, y + h), r, r, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
            text(c, value, x, y, size, color, bold, 760);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold,
                          float maxWidth) {
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(ellipsize(value, size, maxWidth), x, y, paint);
        }

        private void textRight(Canvas c, String value, float x, float y, float size, int color) {
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText(value, x, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void textCentered(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(value, x, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private String ellipsize(String value, float size, float maxWidth) {
            if (value == null) return "";
            paint.setTextSize(size);
            if (paint.measureText(value) <= maxWidth) return value;
            String out = value;
            while (out.length() > 2 && paint.measureText(out + "…") > maxWidth) {
                out = out.substring(0, out.length() - 1);
            }
            return out + "…";
        }
    }

    private static Match nextMatch(List<Match> matches) {
        long now = System.currentTimeMillis() - 2L * 60L * 60L * 1000L;
        for (Match match : matches) {
            if ("in".equals(match.state) || (!"post".equals(match.state) && match.kickoff >= now)) return match;
        }
        return null;
    }

    private static Match previousMatch(List<Match> matches) {
        Match found = null;
        for (Match match : matches) {
            if ("post".equals(match.state) && match.kickoff <= System.currentTimeMillis()) found = match;
        }
        return found;
    }

    private static List<Match> upcoming(List<Match> matches, int limit) {
        List<Match> out = new ArrayList<>();
        long now = System.currentTimeMillis() - 2L * 60L * 60L * 1000L;
        for (Match match : matches) {
            if ("in".equals(match.state) || (!"post".equals(match.state) && match.kickoff >= now)) {
                out.add(match);
                if (out.size() == limit) break;
            }
        }
        return out;
    }

    private static List<Match> results(List<Match> matches, int limit) {
        List<Match> out = new ArrayList<>();
        for (int i = matches.size() - 1; i >= 0 && out.size() < limit; i--) {
            Match match = matches.get(i);
            if ("post".equals(match.state) && match.kickoff <= System.currentTimeMillis()) out.add(match);
        }
        return out;
    }

    private static String score(String value) {
        return value == null || value.isEmpty() ? "–" : value;
    }

    private static String formatWhen(long millis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone);
        LocalDate today = LocalDate.now(zone);
        String time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
        if (dateTime.toLocalDate().equals(today)) return "Tonight · " + time;
        if (dateTime.toLocalDate().equals(today.plusDays(1))) return "Tomorrow · " + time;
        return dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault()));
    }

    private static String shortDate(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()));
    }

    private static String shortTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
    }

    private static String countdown(long millis) {
        long seconds = Duration.between(Instant.now(), Instant.ofEpochMilli(millis)).getSeconds();
        if (seconds <= 0) return "";
        long hours = seconds / 3600L;
        long days = hours / 24L;
        hours %= 24L;
        if (days > 0) return "In " + days + "d " + hours + "h";
        if (hours > 0) return "In " + hours + "h";
        return "Soon";
    }

    private static final class TeamResult {
        final int index;
        final List<Match> matches;

        TeamResult(int index, List<Match> matches) {
            this.index = index;
            this.matches = matches;
        }
    }

    private static final class Match {
        final String id;
        final long kickoff;
        final String opponent;
        final String competition;
        final String state;
        final String statusDetail;
        final String ourScore;
        final String theirScore;
        final boolean home;

        Match(String id, long kickoff, String opponent, String competition, String state,
              String statusDetail, String ourScore, String theirScore, boolean home) {
            this.id = id;
            this.kickoff = kickoff;
            this.opponent = opponent;
            this.competition = competition;
            this.state = state;
            this.statusDetail = statusDetail;
            this.ourScore = ourScore;
            this.theirScore = theirScore;
            this.home = home;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("kickoff", kickoff);
                object.put("opponent", opponent);
                object.put("competition", competition);
                object.put("state", state);
                object.put("statusDetail", statusDetail);
                object.put("ourScore", ourScore);
                object.put("theirScore", theirScore);
                object.put("home", home);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Match fromJson(JSONObject object) {
            try {
                return new Match(object.getString("id"), object.getLong("kickoff"),
                        object.getString("opponent"), object.getString("competition"),
                        object.optString("state", "pre"), object.optString("statusDetail", ""),
                        object.optString("ourScore", ""),
                        object.optString("theirScore", ""), object.optBoolean("home", false));
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
