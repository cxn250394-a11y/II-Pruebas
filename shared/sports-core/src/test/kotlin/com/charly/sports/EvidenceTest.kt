package com.charly.sports

import java.math.BigDecimal as D
import org.junit.Assert.*
import org.junit.Test

class EvidenceTest {
    private val k=MarketKey("synthetic-event","football","corners","full-time","match","8.5","90-min-only",false)
    private val sides=setOf("over","under")
    private fun q(side:String="over",price:String="2.10",book:String="A",at:Long=100,until:Long=300,suspended:Boolean=false,key:MarketKey=k)=Quote(key,side,book,D(price),at,until,suspended)
    private fun pair()=listOf(q(),q("under",book="B"))
    @Test fun missingStatisticsNotZero() { val r=Statistics.over(listOf(CountObservation(null,"source",1),CountObservation(0,"source",1)),.5); assertEquals(1,r.known); assertEquals(1,r.missing); assertEquals(0,r.hits) }
    @Test fun allUnknownHasNoFraction() = assertNull(Statistics.over(listOf(CountObservation(null,"source",1)),1.5).fraction)
    @Test fun historicalRateIsDescriptive() { val r=Statistics.over(listOf(0,2,3).map { CountObservation(it,"source",1) },1.5); assertEquals(2,r.hits); assertEquals(3,r.known) }
    @Test(expected=IllegalArgumentException::class) fun negativeCountRejected() { CountObservation(-1,"source",1) }
    @Test(expected=IllegalArgumentException::class) fun nanLineRejected() { Statistics.over(emptyList(),Double.NaN) }
    @Test fun completeMarket() { val r=MarketMath.compare(pair(),sides,200,200); assertEquals(ComparisonStatus.COMPLETE,r.status); assertTrue(r.theoreticalArbitrage) }
    @Test fun ordinaryMarginNotArbitrage() { val r=MarketMath.compare(listOf(q(price="1.9"),q("under","1.9","B")),sides,200,200); assertFalse(r.theoreticalArbitrage) }
    @Test fun fairMarketNotArbitrage() = assertFalse(MarketMath.compare(listOf(q(price="2"),q("under","2","B")),sides,200,200).theoreticalArbitrage)
    @Test fun missingOutcomeIsNotArbitrage() { val r=MarketMath.compare(listOf(q()),sides,200,200); assertEquals(ComparisonStatus.INCOMPLETE,r.status); assertNull(r.inverseSum); assertFalse(r.theoreticalArbitrage) }
    @Test fun expiredBoundaryRejected() = assertEquals(ComparisonStatus.INCOMPLETE,MarketMath.compare(pair(),sides,300,300).status)
    @Test fun staleObservationRejected() = assertEquals(ComparisonStatus.INCOMPLETE,MarketMath.compare(pair(),sides,250,100).status)
    @Test fun futureObservationRejected() = assertEquals(ComparisonStatus.INCOMPLETE,MarketMath.compare(pair(),sides,99,200).status)
    @Test fun newestSuspensionDoesNotReviveOldQuote() { val qs=pair()+q(at=150,suspended=true); assertEquals(ComparisonStatus.INCOMPLETE,MarketMath.compare(qs,sides,200,200).status) }
    @Test fun latestPriceReplacesOldHighPrice() { val qs=listOf(q(price="3"),q(price="1.5",at=150),q("under","2","B")); val r=MarketMath.compare(qs,sides,200,200); assertEquals(D("1.5"),r.best["over"]?.odds); assertFalse(r.theoreticalArbitrage) }
    @Test fun bestCurrentBookChosen() { val r=MarketMath.compare(pair()+q(price="2.4",book="C"),sides,200,200); assertEquals("C",r.best["over"]?.bookmaker) }
    @Test fun contradictorySnapshotRejected() = assertEquals(ComparisonStatus.CONFLICT,MarketMath.compare(pair()+q(price="2.2"),sides,200,200).status)
    @Test fun identicalDuplicateNotConflict() = assertEquals(ComparisonStatus.COMPLETE,MarketMath.compare(pair()+q(),sides,200,200).status)
    @Test fun decimalScaleNotConflict() = assertEquals(ComparisonStatus.COMPLETE,MarketMath.compare(pair()+q(price="2.1000"),sides,200,200).status)
    @Test(expected=IllegalArgumentException::class) fun differentPeriodsRejected() { MarketMath.compare(pair()+q(book="C",key=k.copy(period="first-half")),sides,200,200) }
    @Test(expected=IllegalArgumentException::class) fun differentLinesRejected() { MarketMath.compare(pair()+q(book="C",key=k.copy(line="9.5")),sides,200,200) }
    @Test(expected=IllegalArgumentException::class) fun differentSettlementRejected() { MarketMath.compare(pair()+q(book="C",key=k.copy(settlementRules="includes-extra-time")),sides,200,200) }
    @Test(expected=IllegalArgumentException::class) fun liveAndPrematchNotMerged() { MarketMath.compare(pair()+q(book="C",key=k.copy(live=true)),sides,200,200) }
    @Test(expected=IllegalArgumentException::class) fun invalidOddsRejected() { q(price="1") }
    @Test(expected=IllegalArgumentException::class) fun invalidProbabilityRejected() { MarketMath.expectedReturnPerUnit(D("2"),D("1.2")) }
    @Test fun explicitProbabilityOnly() = assertEquals(D("0.10"),MarketMath.expectedReturnPerUnit(D("2"),D("0.55")))
    @Test fun proportionalProbabilitiesSumToOne() { val p=MarketMath.proportionalProbabilities(listOf(D("1.9"),D("1.9"))); assertEquals(0,D.ONE.compareTo(p.reduce(D::add))); assertEquals(0,D("0.5").compareTo(p[0])) }
}
