package com.wiggli.gozofootball;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;

final class FootballConfig {
    static final String PREFS = "gozo_football_preferences";

    static final Competition[] COMPETITIONS = new Competition[] {
            new Competition("eng.1", "Premier League", "England"),
            new Competition("ita.1", "Serie A", "Italy"),
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
            new Team("manchester_united", "Manchester United", "MU", 360, 0xFFDA291C,
                    "eng.1", "uefa.champions", "uefa.europa", "eng.fa", "eng.league_cup"),
            new Team("ac_milan", "AC Milan", "ACM", 103, 0xFFE4002B,
                    "ita.1", "uefa.champions", "uefa.europa", "ita.coppa_italia"),
            new Team("england", "England", "ENG", 448, 0xFF23477E,
                    "fifa.world", "uefa.nations", "uefa.euro", "fifa.friendly"),
            new Team("italy", "Italy", "ITA", 162, 0xFF0066B3,
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
        return preferences.getBoolean("competition_" + competition.slug, true);
    }

    static List<Team> enabledTeams(SharedPreferences preferences) {
        List<Team> enabled = new ArrayList<>();
        for (Team team : TEAMS) {
            if (isTeamEnabled(preferences, team)) enabled.add(team);
        }
        if (enabled.isEmpty()) enabled.add(TEAMS[0]);
        return enabled;
    }

    static List<String> enabledCompetitions(SharedPreferences preferences, Team team) {
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
        final int color;
        final String[] competitions;

        Team(String key, String name, String shortName, int espnId, int color, String... competitions) {
            this.key = key;
            this.name = name;
            this.shortName = shortName;
            this.espnId = espnId;
            this.color = color;
            this.competitions = competitions;
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
