package mx.charly.stats

import mx.charly.logic.Match
import mx.charly.logic.Csv
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.content.Context
import java.io.File

class StatsWatchTest {
    private fun m(id:String="7",h:Int?=1,a:Int?=0,finished:Boolean=false,
                  home:String="América",away:String="Toluca")=Match(id,home,away,h,a,finished,1_000L)
    private fun diff(old:Match,new:Match,follow:Boolean=true)=StatsWatch.diff(listOf(old),listOf(new)){follow}
    @Test fun initialFixtureNotAlerted(){assertTrue(StatsWatch.diff(emptyList(),listOf(m())){true}.isEmpty())}
    @Test fun unchangedDoesNotAlert(){assertTrue(diff(m(),m()).isEmpty())}
    @Test fun untrackedDoesNotAlert(){assertTrue(diff(m(),m(h=2),false).isEmpty())}
    @Test fun scoreIncreasedIsObservationNotGoal(){val r=diff(m(),m(h=2)).single();assertEquals(UpdateKind.SCORE,r.kind);assertEquals(1,r.beforeHome);assertEquals(2,r.afterHome)}
    @Test fun awayGoalChange(){assertEquals(UpdateKind.SCORE,diff(m(),m(a=1)).single().kind)}
    @Test fun reducedScoreIsCorrection(){assertEquals(UpdateKind.SCORE_CORRECTION,diff(m(h=3),m(h=2)).single().kind)}
    @Test fun missingOldScoreDoesNotInventGoal(){assertEquals(UpdateKind.FIRST_SCORE,diff(m(h=null,a=null),m(h=1,a=0)).single().kind)}
    @Test fun disappearedScoreIsUnknown(){assertEquals(UpdateKind.SCORE_UNKNOWN,diff(m(),m(h=null,a=null)).single().kind)}
    @Test fun finishWithoutScoreChange(){assertEquals(UpdateKind.FINISHED,diff(m(),m(finished=true)).single().kind)}
    @Test fun reopenedWithoutScoreChange(){assertEquals(UpdateKind.REOPENED,diff(m(finished=true),m()).single().kind)}
    @Test fun wrongIdCannotMatch(){assertTrue(diff(m(id="1"),m(id="2")).isEmpty())}
    @Test fun conflictingTeamsCannotMatch(){assertTrue(diff(m(),m(home="Guadalajara")).isEmpty())}
    @Test fun duplicatedUpdateCountedOnce(){assertEquals(1,StatsWatch.diff(listOf(m()),listOf(m(h=2),m(h=2))){true}.size)}
    @Test fun absentScoresRemainUnknown(){assertTrue(diff(m(h=null,a=null),m(h=null,a=null)).isEmpty())}
    @Test fun scoreTextUnknown(){assertEquals("N/D",StatsWatch.score(null,2));assertEquals("1–2",StatsWatch.score(1,2))}
    @Test fun leagueSeasonScope(){assertEquals("bl1:2026",StatsWatch.scope("BL1",2026))}
    @Test fun scopeInvalidLeagueRejected(){try{StatsWatch.scope("../../file",2026);fail("bad league")}catch(_:IllegalArgumentException){}}
    @Test fun teamKeysAreExactNotFuzzy(){assertEquals("amÉrica".lowercase(),StatsWatch.teamKey("  América  "));assertNotEquals(StatsWatch.teamKey("América"),StatsWatch.teamKey("America"))}
    @Test fun exportPreservesCsvEscaping(){
        val csv=MatchExport.csv(listOf(m(home="América, Azul",away="A\"B",h=null,a=1)))
        val row=Csv.parse(csv).single()
        assertEquals("América, Azul",row["home"]);assertEquals("A\"B",row["away"])
        assertEquals("",row["home_goals"]);assertEquals("1",row["away_goals"])
    }
    @Test fun exportEmptyHasHeadersOnly(){assertEquals(0,Csv.parse(MatchExport.csv(emptyList())).size)}
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class WatchStorageTest {
    private val ctx:Context get()=RuntimeEnvironment.getApplication()
    private val scope="bl1:2026"
    private fun m(id:String="7",home:String="América",away:String="Toluca")=Match(id,home,away,1,0,false,1_000)
    private fun observation(id:String="7")=ScoreObservation(id,"América","Toluca",1,0,2,0,UpdateKind.SCORE)
    @Before fun reset(){
        ctx.getSharedPreferences("stats_watch",Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.filesDir,"stats-watch-changes.json").delete()
        File(ctx.filesDir,"stats-watch-changes.json.bak").delete()
        File(ctx.filesDir,"stats-watch-changes.json.new").delete()
    }
    @Test fun noOptInByDefault(){assertFalse(WatchPrefs(ctx).isTracked(scope,m()))}
    @Test fun favoriteMatchPersists(){val p=WatchPrefs(ctx);assertTrue(p.toggleMatch(scope,"7"));assertTrue(WatchPrefs(ctx).isTracked(scope,m()))}
    @Test fun removingFavoriteWorks(){val p=WatchPrefs(ctx);p.toggleMatch(scope,"7");assertFalse(p.toggleMatch(scope,"7"));assertFalse(p.isTracked(scope,m()))}
    @Test fun followedTeamCoversItsMatches(){val p=WatchPrefs(ctx);assertTrue(p.toggleTeam(scope,"América"));assertTrue(p.isTracked(scope,m(id="9",away="Boca")))}
    @Test fun favoritesIsolatedBetweenSeasons(){val p=WatchPrefs(ctx);p.toggleTeam(scope,"América");assertFalse(p.isTracked("bl1:2025",m()))}
    @Test fun favoritesIsolatedBetweenLeagues(){val p=WatchPrefs(ctx);p.toggleMatch(scope,"7");assertFalse(p.isTracked("liga_mx:2026",m()))}
    @Test fun teamNameIsNotFuzzyMatched(){val p=WatchPrefs(ctx);p.toggleTeam(scope,"América");assertFalse(p.isTracked(scope,m(home="America")))}
    @Test fun historyPersistsAndIsScoped(){
        val store=WatchHistory(ctx);store.append(scope,"OpenLigaDB",100_000L,listOf(observation()))
        assertEquals(1,WatchHistory(ctx).all(scope).size)
        assertEquals(0,WatchHistory(ctx).all("bl1:2025").size)
        assertEquals("OpenLigaDB",WatchHistory(ctx).all(scope).single().source)
    }
    @Test fun historyPreservesCorrectionsAndUnknownScores(){
        val store=WatchHistory(ctx)
        store.append(scope,"OpenLigaDB",100_000L,listOf(
            ScoreObservation("7","América","Toluca",2,1,1,1,UpdateKind.SCORE_CORRECTION),
            ScoreObservation("8","Chivas","Pumas",null,null,1,0,UpdateKind.FIRST_SCORE)))
        assertEquals(UpdateKind.SCORE_CORRECTION,store.all(scope).first().change.kind)
        assertNull(store.all(scope).last().change.beforeHome)
    }
    @Test fun corruptedHistoryRemainsUnmodified(){
        val path=File(ctx.filesDir,"stats-watch-changes.json")
        path.writeText("broken-json")
        try{WatchHistory(ctx).append(scope,"OpenLigaDB",100_000L,listOf(observation()));fail("overwrote invalid history")}
        catch(_:Exception){assertEquals("broken-json",path.readText())}
    }
    @Test fun invalidTeamCannotBeStored(){try{WatchPrefs(ctx).toggleTeam(scope,"  ");fail("blank accepted")}catch(_:IllegalArgumentException){}}
    @Test fun historyDoesNotInventEntries(){assertTrue(WatchHistory(ctx).all(scope).isEmpty());WatchHistory(ctx).append(scope,"OpenLigaDB",100_000L,emptyList());assertTrue(WatchHistory(ctx).all(scope).isEmpty())}
}
