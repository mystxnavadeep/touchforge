package com.touchforge.toolkit.core.gate

import kotlinx.serialization.Serializable

/**
 * Original local "credits" economy used to meter premium actions on the free tier.
 * Entirely local-first: earned by waiting a cooldown or (optionally, if the user
 * enables ads) by a rewarded ad. No server involved in this implementation.
 */
@Serializable
data class CreditState(val balance: Int = START_CREDITS, val lastClaimMs: Long = 0) {
    companion object {
        const val START_CREDITS = 10
        const val MAX_BALANCE = 60
        const val CLAIM_REWARD = 5
        const val CLAIM_COOLDOWN_MS = 30_000L
    }
}

class CreditLedger(private val clock: () -> Long = System::currentTimeMillis) {

    fun canClaim(state: CreditState, nowMs: Long = clock()): Boolean =
        state.balance < CreditState.MAX_BALANCE && nowMs - state.lastClaimMs >= CreditState.CLAIM_COOLDOWN_MS

    fun claimIn(state: CreditState, nowMs: Long = clock()): Long =
        if (state.balance >= CreditState.MAX_BALANCE) 0
        else (CreditState.CLAIM_COOLDOWN_MS - (nowMs - state.lastClaimMs)).coerceAtLeast(0)

    fun claim(state: CreditState, nowMs: Long = clock()): CreditState? {
        if (!canClaim(state, nowMs)) return null
        return state.copy(
            balance = (state.balance + CreditState.CLAIM_REWARD).coerceAtMost(CreditState.MAX_BALANCE),
            lastClaimMs = nowMs,
        )
    }

    sealed class SpendResult {
        data class Ok(val newState: CreditState) : SpendResult()
        data class Insufficient(val needed: Int, val balance: Int) : SpendResult()
    }

    fun spend(state: CreditState, amount: Int): SpendResult {
        if (amount <= 0) return SpendResult.Insufficient(needed = 0, balance = state.balance)
        return if (state.balance >= amount) SpendResult.Ok(state.copy(balance = state.balance - amount))
        else SpendResult.Insufficient(needed = amount, balance = state.balance)
    }

    fun refund(state: CreditState, amount: Int): CreditState =
        state.copy(balance = (state.balance + amount).coerceAtMost(CreditState.MAX_BALANCE))
}
