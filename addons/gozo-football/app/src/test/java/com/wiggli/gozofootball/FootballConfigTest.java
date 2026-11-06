package com.wiggli.gozofootball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class FootballConfigTest {
    @Test
    public void requestedTeamsRemainTheFourDefaults() {
        assertEquals(4, FootballConfig.TEAMS.length);
        assertEquals("Manchester United", FootballConfig.TEAMS[0].name);
        assertEquals("AC Milan", FootballConfig.TEAMS[1].name);
        assertEquals("England", FootballConfig.TEAMS[2].name);
        assertEquals("Italy", FootballConfig.TEAMS[3].name);
    }

    @Test
    public void clubAndInternationalCompetitionsStaySeparated() {
        assertTrue(Arrays.asList(FootballConfig.TEAMS[0].competitions).contains("eng.1"));
        assertTrue(Arrays.asList(FootballConfig.TEAMS[1].competitions).contains("ita.1"));
        assertTrue(Arrays.asList(FootballConfig.TEAMS[2].competitions).contains("fifa.world"));
        assertTrue(Arrays.asList(FootballConfig.TEAMS[3].competitions).contains("uefa.euro"));
    }

    @Test
    public void competitionLabelsAreFriendlyForTheTelevision() {
        assertEquals("Premier League", FootballConfig.competitionName("eng.1"));
        assertEquals("Serie A", FootballConfig.competitionName("ita.1"));
        assertEquals("Champions League", FootballConfig.competitionName("uefa.champions"));
        assertEquals("unknown", FootballConfig.competitionName("unknown"));
    }
}
