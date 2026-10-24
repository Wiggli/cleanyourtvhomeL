package com.wiggli.gozofootball;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final long CACHE_MS = 15L * 60L * 1000L;
    private static final String PREFS = "gozo_football_cache";

    private static final TeamSpec[] TEAMS = new TeamSpec[] {
            new TeamSpec("Manchester United", "MU", 360, Color.rgb(218, 41, 28),
                    new String[]{"eng.1", "uefa.champions", "uefa.europa", "eng.fa", "eng.league_cup"}),
            new TeamSpec("AC Milan", "ACM", 103, Color.rgb(228, 0, 43),
                    new String[]{"ita.1", "uefa.champions", "uefa.europa", "ita.coppa_italia"}),
            new TeamSpec("England", "ENG", 448, Color.rgb(35, 71, 126),
                    new String[]{"fifa.world", "uefa.nations", "uefa.euro", "fifa.friendly"}),
            new TeamSpec("Italy", "ITA", 162, Color.rgb(0, 102, 179),
                    new String[]{"fifa.world", "uefa.nations", "uefa.euro", "fifa.friendly"})
    };

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private FootballView footballView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.30f;
        window.setAttributes(params);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        footballView = new FootballView();
        setContentView(footballView);
        loadCache();
        long updated = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("updated", 0L);
        if (System.currentTimeMillis() - updated > CACHE_MS || !footballView.hasAnyData()) {
            refresh(false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            footballView.selected = (footballView.selected + TEAMS.length - 1) % TEAMS.length;
            footballView.invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            footballView.selected = (footballView.selected + 1) % TEAMS.length;
            footballView.invalidate();
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

    private void loadCache() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < TEAMS.length; i++) {
            String raw = prefs.getString("team_" + TEAMS[i].id, null);
            if (raw == null) continue;
            try {
                JSONArray array = new JSONArray(raw);
                List<Match> matches = new ArrayList<>();
                for (int j = 0; j < array.length(); j++) {
                    Match match = Match.fromJson(array.getJSONObject(j));
                    if (match != null) matches.add(match);
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
        for (int i = 0; i < TEAMS.length; i++) {
            final int index = i;
            futures.add(executor.submit(() -> new TeamResult(index, fetchTeam(TEAMS[index]))));
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
                    editor.putString("team_" + TEAMS[entry.getKey()].id, array.toString());
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

    private List<Match> fetchTeam(TeamSpec team) {
        Map<String, Match> unique = new LinkedHashMap<>();
        for (String competition : team.competitions) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + competition + "/teams/" + team.id + "/schedule?limit=100");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "GozoFootball/1.0 AndroidTV");
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

        List<Match> matches = new ArrayList<>(unique.values());
        matches.sort(Comparator.comparingLong(m -> m.kickoff));
        long now = System.currentTimeMillis();
        long earliest = now - 14L * 24L * 60L * 60L * 1000L;
        long latest = now + 180L * 24L * 60L * 60L * 1000L;
        matches.removeIf(m -> m.kickoff < earliest || m.kickoff > latest);
        return matches;
    }

    private Match parseMatch(JSONObject event, TeamSpec team, String competitionSlug) {
        if (event == null) return null;
        try {
            String id = event.optString("id", "");
            String date = event.optString("date", "");
            if (id.isEmpty() || date.isEmpty()) return null;
            long kickoff = Instant.parse(date).toEpochMilli();

            JSONObject status = event.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            String state = type == null ? "pre" : type.optString("state", "pre");

            JSONArray competitions = event.optJSONArray("competitions");
            if (competitions == null || competitions.length() == 0) return null;
            JSONObject competition = competitions.optJSONObject(0);
            JSONArray competitors = competition == null ? null : competition.optJSONArray("competitors");
            if (competitors == null) return null;

            String opponent = null;
            String ourScore = "";
            String theirScore = "";
            boolean home = false;
            for (int i = 0; i < competitors.length(); i++) {
                JSONObject competitor = competitors.optJSONObject(i);
                if (competitor == null) continue;
                JSONObject teamJson = competitor.optJSONObject("team");
                if (teamJson == null) continue;
                boolean ours = String.valueOf(team.id).equals(teamJson.optString("id"));
                String score = competitor.optString("score", "");
                if (ours) {
                    ourScore = score;
                    home = "home".equals(competitor.optString("homeAway"));
                } else {
                    opponent = teamJson.optString("displayName", teamJson.optString("name", "Opponent"));
                    theirScore = score;
                }
            }
            if (opponent == null) return null;
            return new Match(id, kickoff, opponent, competitionName(competitionSlug), state,
                    ourScore, theirScore, home);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String competitionName(String slug) {
        switch (slug) {
            case "eng.1": return "Premier League";
            case "ita.1": return "Serie A";
            case "uefa.champions": return "Champions League";
            case "uefa.europa": return "Europa League";
            case "eng.fa": return "FA Cup";
            case "eng.league_cup": return "League Cup";
            case "ita.coppa_italia": return "Coppa Italia";
            case "fifa.world": return "World Cup";
            case "uefa.nations": return "Nations League";
            case "uefa.euro": return "European Championship";
            case "fifa.friendly": return "International Friendly";
            default: return slug;
        }
    }

    private final class FootballView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<Integer, List<Match>> data = new HashMap<>();
        private int selected = 0;
        private boolean loading = false;
        private long lastUpdated = 0L;
        private String message;

        FootballView() {
            super(MainActivity.this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }

        boolean hasAnyData() {
            for (List<Match> list : data.values()) if (list != null && !list.isEmpty()) return true;
            return false;
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

            drawRound(canvas, 90, 65, 1830, 1015, 46, Color.argb(220, 17, 20, 28));
            strokeRound(canvas, 90, 65, 1830, 1015, 46, Color.argb(70, 255, 255, 255), 2);

            text(canvas, "Football", 150, 145, 48, Color.WHITE, true);
            text(canvas, "Gozo match centre", 150, 187, 24, Color.argb(185, 255, 255, 255), false);
            String status = loading ? "Updating fixtures…" : updatedLabel();
            textRight(canvas, status, 1765, 150, 22, Color.argb(170, 255, 255, 255));

            float[] xs = {150, 555, 960, 1365};
            for (int i = 0; i < TEAMS.length; i++) drawTeamPill(canvas, xs[i], 220, 360, 82, i);

            TeamSpec team = TEAMS[selected];
            List<Match> matches = data.getOrDefault(selected, Collections.emptyList());
            Match next = nextMatch(matches);
            Match previous = previousMatch(matches);

            drawRound(canvas, 150, 335, 1050, 635, 34, Color.argb(150, 255, 255, 255));
            strokeRound(canvas, 150, 335, 1050, 635, 34, Color.argb(80, 255, 255, 255), 2);
            paint.setColor(team.color);
            canvas.drawCircle(220, 410, 34, paint);
            textCentered(canvas, team.shortName, 220, 420, 20, Color.WHITE, true);
            text(canvas, team.name, 280, 405, 38, Color.WHITE, true);

            if (next != null) {
                text(canvas, next.competition, 200, 475, 24, Color.argb(185, 255, 255, 255), false);
                text(canvas, next.home ? team.name + "  vs  " + next.opponent : next.opponent + "  vs  " + team.name,
                        200, 535, 34, Color.WHITE, true);
                text(canvas, formatWhen(next.kickoff), 200, 585, 27, Color.argb(235, 255, 255, 255), false);
                String countdown = countdown(next.kickoff);
                if (!countdown.isEmpty()) textRight(canvas, countdown, 1000, 585, 27, team.color);
            } else {
                text(canvas, message == null ? "No upcoming fixture found" : message,
                        200, 525, 30, Color.argb(210, 255, 255, 255), false);
            }

            drawRound(canvas, 1090, 335, 675, 485, 34, Color.argb(125, 255, 255, 255));
            strokeRound(canvas, 1090, 335, 675, 485, 34, Color.argb(65, 255, 255, 255), 2);
            text(canvas, "Coming up", 1140, 395, 28, Color.WHITE, true);
            List<Match> upcoming = upcoming(matches, 3);
            float rowY = 452;
            if (upcoming.isEmpty()) {
                text(canvas, "No fixtures loaded", 1140, 500, 24, Color.argb(180, 255, 255, 255), false);
            } else {
                for (Match match : upcoming) {
                    text(canvas, shortDate(match.kickoff), 1140, rowY, 21, Color.argb(170, 255, 255, 255), false);
                    text(canvas, match.opponent, 1325, rowY, 24, Color.WHITE, true);
                    textRight(canvas, shortTime(match.kickoff), 1710, rowY, 22, Color.argb(210, 255, 255, 255));
                    text(canvas, match.competition, 1325, rowY + 30, 18, Color.argb(145, 255, 255, 255), false);
                    rowY += 112;
                }
            }

            drawRound(canvas, 150, 675, 900, 170, 30, Color.argb(105, 255, 255, 255));
            text(canvas, "Latest result", 195, 730, 24, Color.argb(175, 255, 255, 255), false);
            if (previous != null) {
                String result = team.name + "  " + score(previous.ourScore) + "  –  "
                        + score(previous.theirScore) + "  " + previous.opponent;
                text(canvas, result, 195, 790, 30, Color.WHITE, true);
                textRight(canvas, previous.competition, 1000, 790, 21, Color.argb(160, 255, 255, 255));
            } else {
                text(canvas, "No recent result loaded", 195, 790, 27, Color.argb(190, 255, 255, 255), false);
            }

            drawRound(canvas, 1090, 845, 675, 80, 24, Color.argb(75, 255, 255, 255));
            text(canvas, "←  →  choose team", 1140, 895, 21, Color.argb(195, 255, 255, 255), false);
            textRight(canvas, "OK refresh   •   Back close", 1710, 895, 21, Color.argb(195, 255, 255, 255));

            text(canvas, "Fixtures via ESPN public schedule endpoints • cached for 15 minutes",
                    150, 960, 18, Color.argb(110, 255, 255, 255), false);
            canvas.restore();
        }

        private void drawTeamPill(Canvas canvas, float x, float y, float w, float h, int index) {
            TeamSpec team = TEAMS[index];
            boolean active = index == selected;
            int fill = active ? Color.argb(205, Color.red(team.color), Color.green(team.color), Color.blue(team.color))
                    : Color.argb(90, 255, 255, 255);
            drawRound(canvas, x, y, w, h, 26, fill);
            strokeRound(canvas, x, y, w, h, 26,
                    active ? Color.argb(210, 255, 255, 255) : Color.argb(45, 255, 255, 255), active ? 3 : 2);
            text(canvas, team.shortName, x + 26, y + 52, 23, Color.WHITE, true);
            text(canvas, team.name, x + 90, y + 52, 22, Color.WHITE, active);
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
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(ellipsize(value, size, 760), x, y, paint);
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
            if (!"post".equals(match.state) && match.kickoff >= now) return match;
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
            if (!"post".equals(match.state) && match.kickoff >= now) {
                out.add(match);
                if (out.size() == limit) break;
            }
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

    private static final class TeamSpec {
        final String name;
        final String shortName;
        final int id;
        final int color;
        final String[] competitions;

        TeamSpec(String name, String shortName, int id, int color, String[] competitions) {
            this.name = name;
            this.shortName = shortName;
            this.id = id;
            this.color = color;
            this.competitions = competitions;
        }
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
        final String ourScore;
        final String theirScore;
        final boolean home;

        Match(String id, long kickoff, String opponent, String competition, String state,
              String ourScore, String theirScore, boolean home) {
            this.id = id;
            this.kickoff = kickoff;
            this.opponent = opponent;
            this.competition = competition;
            this.state = state;
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
                        object.optString("state", "pre"), object.optString("ourScore", ""),
                        object.optString("theirScore", ""), object.optBoolean("home", false));
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
