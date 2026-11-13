package com.wiggli.gozofootball;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Read-only signed bridge that gives the Gozo launcher the next selected fixture. */
public final class FootballProvider extends ContentProvider {
    public static final String AUTHORITY = "com.wiggli.gozofootball.fixtures";
    private static final String[] COLUMNS = {"headline", "fixture", "detail", "home_name", "away_name",
            "competition", "match_time", "home_crest", "away_crest"};
    private static final long CACHE_MS = 15L * 60L * 1000L;
    private static Candidate cached;
    private static long cachedAt;
    private static java.util.Map<String, ?> cachedPreferences;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        if (getContext() == null || uri == null || !"next".equals(uri.getLastPathSegment())) return cursor;
        Candidate next;
        synchronized (FootballProvider.class) {
            SharedPreferences preferences = FootballConfig.preferences(getContext());
            java.util.Map<String, ?> selected = preferences.getAll();
            if (cached != null && selected.equals(cachedPreferences)
                    && System.currentTimeMillis() - cachedAt < CACHE_MS) {
                next = cached;
            } else {
                next = fetchNext(preferences);
                if (next != null) {
                    cached = next;
                    cachedAt = System.currentTimeMillis();
                    cachedPreferences = selected;
                }
            }
        }
        if (next != null) {
            boolean live = next.isLive();
            String pairing = next.home ? next.shortName + "  v  " + next.opponent
                    : next.opponent + "  v  " + next.shortName;
            String detail = (live ? score(next.ourScore) + " – " + score(next.theirScore)
                    + "  ·  " + next.status : when(next.kickoff)) + "  ·  " + next.competition;
            cursor.addRow(new Object[] {live ? "FOOTBALL  ·  LIVE" : "FOOTBALL  ·  NEXT",
                    pairing, detail, next.home ? next.team : next.opponentFull,
                    next.home ? next.opponentFull : next.team, next.competition,
                    live ? next.homeScore() + " – " + next.awayScore() + "  " + next.status : when(next.kickoff),
                    crest(next.home ? next.teamId : next.opponentId),
                    crest(next.home ? next.opponentId : next.teamId)});
        }
        return cursor;
    }

    private Candidate fetchNext(SharedPreferences preferences) {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Candidate>> futures = new ArrayList<>();
        for (FootballConfig.Team team : FootballConfig.enabledTeams(preferences)) {
            for (String competition : FootballConfig.enabledCompetitions(preferences, team)) {
                futures.add(pool.submit(() -> fetch(team, competition, preferences)));
            }
        }
        Candidate best = null;
        for (Future<Candidate> future : futures) {
            try {
                Candidate candidate = future.get();
                if (candidate != null && (best == null || candidate.isLive() && !best.isLive()
                        || candidate.isLive() == best.isLive() && candidate.kickoff < best.kickoff)) {
                    best = candidate;
                }
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        return best;
    }

    private Candidate fetch(FootballConfig.Team team, String competitionSlug, SharedPreferences preferences) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                    + competitionSlug + "/teams/" + team.espnId + "/schedule?fixture=true");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(5500);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "GozoFootball/2 AndroidTV");
            if (connection.getResponseCode() / 100 != 2) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && body.length() < 1048576) body.append(line);
            }
            JSONArray events = new JSONObject(body.toString()).optJSONArray("events");
            if (events == null) return null;
            Candidate best = null;
            long earliest = System.currentTimeMillis() - 3L * 60L * 60L * 1000L;
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                String league = MatchdayFeed.eventLeague(event, competitionSlug);
                if (league.isEmpty() || !FootballConfig.isCompetitionEnabled(preferences, league)) continue;
                Candidate candidate = parse(event, team, league);
                if (candidate == null || "post".equals(candidate.state) || candidate.kickoff < earliest) continue;
                if (best == null || candidate.isLive() && !best.isLive()
                        || candidate.isLive() == best.isLive() && candidate.kickoff < best.kickoff) {
                    best = candidate;
                }
            }
            return best;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private Candidate parse(JSONObject event, FootballConfig.Team selected, String competitionSlug) {
        if (event == null) return null;
        try {
            long kickoff = FootballConfig.parseKickoffMillis(event.getString("date"));
            JSONObject competition = event.getJSONArray("competitions").getJSONObject(0);
            JSONArray competitors = competition.getJSONArray("competitors");
            JSONObject status = competition.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            String state = type == null ? "pre" : type.optString("state", "pre");
            String statusText = type == null ? "Live" : type.optString("shortDetail", "Live");
            String opponent = null;
            String opponentFull = "";
            int opponentId = 0;
            String ourScore = "";
            String theirScore = "";
            boolean home = false;
            boolean foundTeam = false;
            for (int i = 0; i < competitors.length(); i++) {
                JSONObject competitor = competitors.getJSONObject(i);
                JSONObject team = competitor.getJSONObject("team");
                boolean ours = String.valueOf(selected.espnId).equals(team.optString("id"));
                JSONObject score = competitor.optJSONObject("score");
                String value = score == null ? competitor.optString("score", "")
                        : score.optString("displayValue", "");
                if (ours) {
                    foundTeam = true;
                    ourScore = value;
                    home = "home".equals(competitor.optString("homeAway"));
                } else {
                    opponent = team.optString("shortDisplayName", team.optString("displayName", "Opponent"));
                    opponentFull = team.optString("displayName", opponent);
                    opponentId = team.optInt("id");
                    theirScore = value;
                }
            }
            return !foundTeam || opponent == null ? null : new Candidate(selected.name, selected.shortName, opponent,
                    MatchdayFeed.leagueLabel(event, competitionSlug), state, statusText, kickoff, home,
                    ourScore, theirScore, opponentFull, selected.espnId, opponentId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final android.util.LruCache<Integer, byte[]> CRESTS = new android.util.LruCache<>(16);

    private static byte[] crest(int id) {
        if (id <= 0) return null;
        byte[] cached = CRESTS.get(id);
        if (cached != null) return cached;
        android.graphics.Bitmap bitmap = TeamCrests.load(id);
        if (bitmap == null) return null;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)) return null;
        byte[] image = bytes.toByteArray();
        CRESTS.put(id, image);
        return image;
    }

    private static String when(long millis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime value = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone);
        String time = value.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
        if (value.toLocalDate().equals(LocalDate.now(zone))) return "Today " + time;
        if (value.toLocalDate().equals(LocalDate.now(zone).plusDays(1))) return "Tomorrow " + time;
        return value.format(DateTimeFormatter.ofPattern("EEE d MMM  HH:mm", Locale.getDefault()));
    }

    private static String score(String value) {
        return value == null || value.isEmpty() ? "–" : value;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".fixture";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("The football provider is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("The football provider is read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("The football provider is read-only");
    }

    private static final class Candidate {
        final String team, shortName, opponent, competition, state, status, ourScore, theirScore;
        final long kickoff;
        final boolean home;
        final String opponentFull;
        final int teamId, opponentId;

        Candidate(String team, String shortName, String opponent, String competition, String state,
                  String status, long kickoff, boolean home, String ourScore, String theirScore,
                  String opponentFull, int teamId, int opponentId) {
            this.team = team;
            this.shortName = shortName;
            this.opponent = opponent;
            this.competition = competition;
            this.state = state;
            this.status = status;
            this.kickoff = kickoff;
            this.home = home;
            this.ourScore = ourScore;
            this.theirScore = theirScore;
            this.opponentFull = opponentFull;
            this.teamId = teamId;
            this.opponentId = opponentId;
        }

        String homeScore() { return score(home ? ourScore : theirScore); }
        String awayScore() { return score(home ? theirScore : ourScore); }

        boolean isLive() {
            return "in".equals(state);
        }
    }
}
