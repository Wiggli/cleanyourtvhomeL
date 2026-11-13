package com.wiggli.gozofootball;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

final class FootballConfig {
    static final String PREFS = "gozo_football_preferences";

    static final Competition[] COMPETITIONS = new Competition[] {
            new Competition("eng.1", "Premier League", "England"),
            new Competition("ita.1", "Serie A", "Italy"),
            new Competition("esp.1", "La Liga", "Spain"),
            new Competition("ger.1", "Bundesliga", "Germany"),
            new Competition("fra.1", "Ligue 1", "France"),
            new Competition("por.1", "Primeira Liga", "Portugal"),
            new Competition("ned.1", "Eredivisie", "Netherlands"),
            new Competition("mlt.1", "Maltese Premier League", "Malta"),
            new Competition("uefa.champions", "Champions League", "Europe"),
            new Competition("uefa.europa", "Europa League", "Europe"),
            new Competition("eng.fa", "FA Cup", "England"),
            new Competition("eng.league_cup", "League Cup", "England"),
            new Competition("ita.coppa_italia", "Coppa Italia", "Italy"),
            new Competition("fifa.world", "World Cup", "International"),
            new Competition("uefa.nations", "Nations League", "International"),
            new Competition("uefa.euro", "European Championship", "International"),
            new Competition("fifa.friendly", "International Friendlies", "International")
    };

    static final Team[] TEAMS = new Team[] {
            new Team("manchester_united", "Manchester United", "MU", 360,
                    "eng.1", "uefa.champions", "uefa.europa", "eng.fa", "eng.league_cup"),
            new Team("ac_milan", "AC Milan", "ACM", 103,
                    "ita.1", "uefa.champions", "uefa.europa", "ita.coppa_italia"),
            new Team("england", "England", "ENG", 448,
                    "fifa.world", "uefa.nations", "uefa.euro", "fifa.friendly"),
            new Team("italy", "Italy", "ITA", 162,
                    "fifa.world", "uefa.nations", "uefa.euro", "fifa.friendly")
    };

    private FootballConfig() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isTeamEnabled(SharedPreferences preferences, Team team) {
        return preferences.getBoolean("team_" + team.key, true);
    }

    static boolean isCompetitionEnabled(SharedPreferences preferences, Competition competition) {
        return isCompetitionEnabled(preferences, competition.slug);
    }

    static boolean isCompetitionEnabled(SharedPreferences preferences, String slug) {
        return preferences.getBoolean("competition_" + slug, true);
    }

    static List<Team> enabledTeams(SharedPreferences preferences) {
        List<Team> enabled = new ArrayList<>();
        for (Team team : teams(preferences)) {
            if (isTeamEnabled(preferences, team)) enabled.add(team);
        }
        if (enabled.isEmpty()) enabled.add(TEAMS[0]);
        return enabled;
    }

    static List<Team> teams(SharedPreferences preferences) {
        List<Team> teams = new ArrayList<>(java.util.Arrays.asList(TEAMS));
        JSONArray saved = savedTeams(preferences);
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id");
            String name = item.optString("name");
            if (id > 0 && !name.isEmpty()) teams.add(new Team(id, name, item.optString("subtitle"),
                    item.optString("league")));
        }
        return teams;
    }

    static Team findTeam(SharedPreferences preferences, int id) {
        for (Team team : teams(preferences)) if (team.espnId == id) return team;
        return null;
    }

    static void follow(SharedPreferences preferences, Team result) {
        Team existing = findTeam(preferences, result.espnId);
        SharedPreferences.Editor edit = preferences.edit();
        if (existing == null) {
            JSONArray saved = savedTeams(preferences);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", result.espnId);
            values.put("name", result.name);
            values.put("subtitle", result.subtitle);
            values.put("league", result.defaultLeague);
            saved.put(new JSONObject(values));
            edit.putString("added_teams", saved.toString());
        }
        edit.putBoolean("team_" + (existing == null ? result.key : existing.key), true).apply();
    }

    static void unfollow(SharedPreferences preferences, Team team) {
        SharedPreferences.Editor edit = preferences.edit();
        if (team.custom) {
            JSONArray previous = savedTeams(preferences);
            JSONArray retained = new JSONArray();
            for (int i = 0; i < previous.length(); i++) {
                JSONObject item = previous.optJSONObject(i);
                if (item != null && item.optInt("id") != team.espnId) retained.put(item);
            }
            edit.putString("added_teams", retained.toString()).remove("team_" + team.key);
        } else {
            edit.putBoolean("team_" + team.key, false);
        }
        edit.apply();
    }

    private static JSONArray savedTeams(SharedPreferences preferences) {
        try {
            return new JSONArray(preferences.getString("added_teams", "[]"));
        } catch (org.json.JSONException ignored) {
            return new JSONArray();
        }
    }

    static List<Competition> competitions(SharedPreferences preferences) {
        Map<String, Competition> catalog = new LinkedHashMap<>();
        for (Competition competition : COMPETITIONS) catalog.put(competition.slug, competition);
        for (Team team : enabledTeams(preferences)) {
            if (team.defaultLeague.isEmpty() || team.subtitle.isEmpty()
                    || team.subtitle.endsWith("soccer team")) continue;
            catalog.putIfAbsent(team.defaultLeague,
                    new Competition(team.defaultLeague, team.subtitle, "Followed teams"));
        }
        return new ArrayList<>(catalog.values());
    }

    static List<String> enabledCompetitions(SharedPreferences preferences, Team team) {
        if (team.custom) {
            if (!team.defaultLeague.isEmpty() && isCompetitionEnabled(preferences, team.defaultLeague)) {
                return java.util.Arrays.asList("all", team.defaultLeague);
            }
            return java.util.Collections.singletonList("all");
        }
        Set<String> applicable = new HashSet<>();
        for (String slug : team.competitions) applicable.add(slug);
        List<String> enabled = new ArrayList<>();
        for (Competition competition : COMPETITIONS) {
            if (applicable.contains(competition.slug) && isCompetitionEnabled(preferences, competition)) {
                enabled.add(competition.slug);
            }
        }
        return enabled;
    }

    static String competitionName(String slug) {
        for (Competition competition : COMPETITIONS) {
            if (competition.slug.equals(slug)) return competition.name;
        }
        return slug;
    }

    static boolean validLeague(String slug) {
        return slug.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*") && slug.length() <= 100;
    }

    /** ESPN sometimes omits seconds, which older Android java.time rejects even though the instant is valid. */
    static long parseKickoffMillis(String value) {
        if (value != null && value.length() == 17 && value.charAt(16) == 'Z') {
            value = value.substring(0, 16) + ":00Z";
        }
        return Instant.parse(value).toEpochMilli();
    }

    static final class Team {
        final String key;
        final String name;
        final String shortName;
        final int espnId;
        final String subtitle;
        final String defaultLeague;
        final boolean custom;
        final String[] competitions;

        Team(String key, String name, String shortName, int espnId, String... competitions) {
            this.key = key;
            this.name = name;
            this.shortName = shortName;
            this.espnId = espnId;
            subtitle = "";
            defaultLeague = "";
            custom = false;
            this.competitions = competitions;
        }

        Team(int id, String name, String subtitle, String defaultLeague) {
            key = "espn_" + id;
            this.name = name;
            shortName = name;
            espnId = id;
            this.subtitle = subtitle;
            this.defaultLeague = validLeague(defaultLeague) && !"all".equals(defaultLeague) ? defaultLeague : "";
            custom = true;
            competitions = new String[0];
        }

        String label() {
            boolean women = subtitle.toLowerCase(java.util.Locale.ROOT).contains("women")
                    || defaultLeague.contains(".w.");
            return women && !name.toLowerCase(java.util.Locale.ROOT).contains("women") ? name + " Women" : name;
        }
    }

    static final class Competition {
        final String slug;
        final String name;
        final String region;

        Competition(String slug, String name, String region) {
            this.slug = slug;
            this.name = name;
            this.region = region;
        }
    }
}
