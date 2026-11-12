package com.wiggli.gozofootball;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MatchdayFeed {
    private static final String SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    private static final ZoneId SCOREBOARD_ZONE = ZoneId.of("America/New_York");

    private MatchdayFeed() {}

    static List<Game> day(String league, LocalDate day, ZoneId zone) throws Exception {
        DateTimeFormatter date = DateTimeFormatter.BASIC_ISO_DATE;
        LocalDate first = day.atStartOfDay(zone).withZoneSameInstant(SCOREBOARD_ZONE).toLocalDate();
        LocalDate last = day.plusDays(1).atStartOfDay(zone).minusNanos(1)
                .withZoneSameInstant(SCOREBOARD_ZONE).toLocalDate();
        Map<String, Game> byId = new LinkedHashMap<>();
        for (LocalDate query = first; !query.isAfter(last); query = query.plusDays(1)) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            JSONObject response = read(SITE + league + "/scoreboard?dates=" + query.format(date) + "&limit=200");
            collectDay(array(response, "events"), league, day, zone, byId);
        }
        List<Game> games = new ArrayList<>(byId.values());
        games.sort(Comparator.comparingLong(game -> game.time));
        return games;
    }

    private static void collectDay(JSONArray events, String league, LocalDate day, ZoneId zone,
                                   Map<String, Game> games) {
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            try {
                long time = FootballConfig.parseKickoffMillis(event.optString("date"));
                if (!Instant.ofEpochMilli(time).atZone(zone).toLocalDate().equals(day)) continue;
                JSONObject competition = array(event, "competitions").optJSONObject(0);
                String id = event.optString("id");
                if (!id.matches("[0-9]+")) continue;
                Game game = fromCompetition(league, id, time, competition);
                if (game != null) games.put(id, game);
            } catch (java.time.DateTimeException ignored) {
                // A fixture without a usable date cannot be placed on a local matchday.
            }
        }
    }

    static JSONObject details(Game game) throws Exception {
        return read(SITE + game.league + "/summary?event=" + game.id);
    }

    static Game updatedGame(Game previous, JSONObject details) {
        JSONObject competition = array(child(details, "header"), "competitions").optJSONObject(0);
        Game fresh = fromCompetition(previous.league, previous.id, previous.time, competition);
        return fresh == null ? previous : fresh;
    }

    private static Game fromCompetition(String league, String id, long time, JSONObject competition) {
        JSONObject home = null;
        JSONObject away = null;
        JSONArray teams = array(competition, "competitors");
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.optJSONObject(i);
            String side = object(team, "homeAway");
            if ("home".equals(side)) home = team;
            if ("away".equals(side)) away = team;
        }
        if (home == null || away == null) return null;
        return new Game(league, id, time, home, away, child(child(competition, "status"), "type"));
    }

    static JSONArray standings(String league) throws Exception {
        return array(read("https://site.api.espn.com/apis/v2/sports/soccer/" + league + "/standings"),
                "children");
    }

    private static JSONObject read(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Score feed unavailable");
            StringBuilder data = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    data.append(buffer, 0, count);
                    if (data.length() > 4_000_000) throw new IOException("Score feed too large");
                }
            }
            return new JSONObject(data.toString());
        } finally {
            connection.disconnect();
        }
    }

    static JSONObject child(JSONObject parent, String key) {
        JSONObject value = parent == null ? null : parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    static JSONArray array(JSONObject parent, String key) {
        JSONArray value = parent == null ? null : parent.optJSONArray(key);
        return value == null ? new JSONArray() : value;
    }

    static String object(JSONObject parent, String key) {
        return parent == null ? "" : parent.optString(key, "");
    }

    static String score(JSONObject competitor) {
        JSONObject nested = competitor.optJSONObject("score");
        return nested == null ? competitor.optString("score", "") : nested.optString("displayValue", "");
    }

    static final class Game {
        final String league, id, home, away, state, status, score;
        final int homeId, awayId;
        final long time;
        final boolean kickoffTbd;

        Game(String league, String id, long time, JSONObject home, JSONObject away, JSONObject status) {
            this.league = league;
            this.id = id;
            this.time = time;
            JSONObject homeTeam = child(home, "team");
            JSONObject awayTeam = child(away, "team");
            this.home = homeTeam.optString("shortDisplayName", homeTeam.optString("displayName", "Home"));
            this.away = awayTeam.optString("shortDisplayName", awayTeam.optString("displayName", "Away"));
            homeId = homeTeam.optInt("id");
            awayId = awayTeam.optInt("id");
            state = status.optString("state", "pre");
            this.status = status.optString("shortDetail");
            kickoffTbd = "STATUS_TBD".equals(status.optString("name"))
                    || "TBD".equalsIgnoreCase(this.status);
            String homeScore = score(home);
            String awayScore = score(away);
            score = homeScore.isEmpty() || awayScore.isEmpty() ? "" : homeScore + "  –  " + awayScore;
        }
    }
}
