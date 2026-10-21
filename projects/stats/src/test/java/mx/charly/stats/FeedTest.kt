package mx.charly.stats
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.json.JSONArray
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class FeedTest{
 private val sample="""[{"matchID":7,"team1":{"teamName":"A"},"team2":{"teamName":"B"},"matchIsFinished":true,"matchDateTimeUTC":"2026-01-01T12:00:00Z","matchResults":[{"resultTypeID":1,"pointsTeam1":0,"pointsTeam2":0},{"resultTypeID":2,"pointsTeam1":2,"pointsTeam2":1}]}]"""
 @Test fun finalScoreNotHalftime(){val m=Feed.parse(JSONArray(sample)).single();assertEquals(2,m.homeGoals);assertEquals(1,m.awayGoals);assertTrue(m.validFinal)}
 @Test fun missingScoreStaysUnknown(){val a=JSONArray(sample);a.getJSONObject(0).remove("matchResults");assertNull(Feed.parse(a).single().homeGoals);assertFalse(Feed.parse(a).single().validFinal)}
 @Test fun malformedIdentityIgnored(){assertTrue(Feed.parse(JSONArray("[{}]")).isEmpty())}
 @Test fun noNetworkOnStart(){val c=Robolectric.buildActivity(StatsActivity::class.java).create();try{assertNotNull(c.get().window.decorView)}finally{c.destroy()}}
}
