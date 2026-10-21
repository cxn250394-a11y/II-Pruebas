package com.charly.sports

import java.math.BigDecimal
import java.math.MathContext

/** Unknown statistics stay null. The source and timestamp travel with each observed value. */
data class CountObservation(val value:Int?,val source:String,val observedAt:Long) {
    init { require(value==null || value>=0); require(source.isNotBlank()); require(observedAt>=0) }
}
data class HistoricalRate(val hits:Int,val known:Int,val missing:Int) {
    val fraction:Double? get()=if(known==0) null else hits.toDouble()/known
}
object Statistics {
    /** Descriptive historical rate, not a forecast probability or a betting recommendation. */
    fun over(values:List<CountObservation>,line:Double):HistoricalRate {
        require(line.isFinite() && line>=0)
        val known=values.mapNotNull { it.value }
        return HistoricalRate(known.count { it>line },known.size,values.size-known.size)
    }
}

/** Exact canonical identity supplied by a validated provider adapter; no fuzzy name matching. */
data class MarketKey(val eventId:String,val sport:String,val metric:String,val period:String,
                     val participant:String,val line:String,val settlementRules:String,val live:Boolean) {
    init { require(listOf(eventId,sport,metric,period,participant,line,settlementRules).all { it.isNotBlank() }) }
}
data class Quote(val market:MarketKey,val outcome:String,val bookmaker:String,val odds:BigDecimal,
                 val observedAt:Long,val expiresAt:Long,val suspended:Boolean=false) {
    init {
        require(outcome.isNotBlank() && bookmaker.isNotBlank())
        require(odds>BigDecimal.ONE && odds<=BigDecimal("1000000"))
        require(observedAt>=0 && expiresAt>observedAt)
    }
}
enum class ComparisonStatus { COMPLETE, INCOMPLETE, CONFLICT }
data class Comparison(val status:ComparisonStatus,val best:Map<String,Quote>,val inverseSum:BigDecimal?) {
    /** Mathematical candidate only. Fees, execution, limits and settlement can erase it. */
    val theoreticalArbitrage:Boolean get()=status==ComparisonStatus.COMPLETE &&
        inverseSum!=null && inverseSum<BigDecimal("0.999999999")
}
object MarketMath {
    private val precision=MathContext.DECIMAL128
    fun compare(quotes:List<Quote>,expectedOutcomes:Set<String>,now:Long,maxAgeMs:Long):Comparison {
        require(expectedOutcomes.size>=2 && expectedOutcomes.all { it.isNotBlank() })
        require(now>=0 && maxAgeMs>=0)
        require(quotes.map { it.market }.distinct().size<=1) { "Different event, period, line or settlement rules" }
        require(quotes.all { it.outcome in expectedOutcomes }) { "Unexpected outcome" }
        val latest=mutableListOf<Quote>()
        for(group in quotes.groupBy { it.bookmaker to it.outcome }.values) {
            val time=group.maxOf { it.observedAt }
            val newest=group.filter { it.observedAt==time }
            if(newest.map { Triple(it.odds.stripTrailingZeros(),it.expiresAt,it.suspended) }.distinct().size>1)
                return Comparison(ComparisonStatus.CONFLICT,emptyMap(),null)
            latest+=newest.first()
        }
        // A latest suspension/expired quote never revives an older higher price from that book.
        val valid=latest.filter { !it.suspended && it.observedAt<=now && it.expiresAt>now && now-it.observedAt<=maxAgeMs }
        val best=valid.groupBy { it.outcome }.mapValues { (_,v)->v.maxBy { it.odds } }
        if(best.keys!=expectedOutcomes) return Comparison(ComparisonStatus.INCOMPLETE,best,null)
        val sum=best.values.fold(BigDecimal.ZERO) { acc,q->acc+BigDecimal.ONE.divide(q.odds,precision) }
        return Comparison(ComparisonStatus.COMPLETE,best,sum)
    }
    /** Proportional removal of margin is an assumption, not the true probability. */
    fun proportionalProbabilities(odds:List<BigDecimal>):List<BigDecimal> {
        require(odds.size>=2 && odds.all { it>BigDecimal.ONE })
        val inverse=odds.map { BigDecimal.ONE.divide(it,precision) }
        val sum=inverse.fold(BigDecimal.ZERO,BigDecimal::add)
        return inverse.map { it.divide(sum,precision) }
    }
    fun expectedReturnPerUnit(odds:BigDecimal,estimatedProbability:BigDecimal):BigDecimal {
        require(odds>BigDecimal.ONE)
        require(estimatedProbability>=BigDecimal.ZERO && estimatedProbability<=BigDecimal.ONE)
        return odds*estimatedProbability-BigDecimal.ONE
    }
}
