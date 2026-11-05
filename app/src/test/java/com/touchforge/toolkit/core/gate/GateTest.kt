package com.touchforge.toolkit.core.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TierGateTest {

    @Test
    fun `sensitivity clamps to tier max`() {
        val r = TierGate.sensitivity(Tier.FREE, 4f)
        assertTrue(r is TierGate.SensitivityDecision.Clamped)
        assertEquals(1.5f, (r as TierGate.SensitivityDecision.Clamped).value, 1e-6f)
        val ok = TierGate.sensitivity(Tier.ULTRA, 4f)
        assertEquals(4f, (ok as TierGate.SensitivityDecision.Allowed).value, 1e-6f)
    }

    @Test
    fun `zone counts enforced`() {
        assertTrue(TierGate.canAddZone(Tier.FREE, 1) is TierGate.CountDecision.Allowed)
        val blocked = TierGate.canAddZone(Tier.FREE, 2)
        assertTrue(blocked is TierGate.CountDecision.Blocked)
        assertTrue(TierGate.canAddZone(Tier.PRO, 8) is TierGate.CountDecision.Blocked)
    }

    @Test
    fun `tap interval floor by tier`() {
        val clamped = TierGate.tapInterval(Tier.FREE, 5)
        assertEquals(50L, (clamped as TierGate.IntervalDecision.Clamped).ms)
        val ok = TierGate.tapInterval(Tier.ULTRA, 1)
        assertEquals(1L, (ok as TierGate.IntervalDecision.Allowed).ms)
    }

    @Test
    fun `macros disabled on free`() {
        assertFalse(TierGate.limits(Tier.FREE).macrosEnabled)
        assertTrue(TierGate.limits(Tier.PRO).macrosEnabled)
    }
}

class CreditLedgerTest {
    private var now = 100_000L
    private val ledger = CreditLedger { now }

    @Test
    fun `claim blocked during cooldown then allowed`() {
        val s0 = CreditState()
        assertNull(ledger.claim(s0)) // immediate claim blocked
        now += CreditState.CLAIM_COOLDOWN_MS
        val s1 = ledger.claim(s0)!!
        assertEquals(CreditState.START_CREDITS + CreditState.CLAIM_REWARD, s1.balance)
    }

    @Test
    fun `balance capped`() {
        var s = CreditState(balance = CreditState.MAX_BALANCE - 1, lastClaimMs = 0)
        now += CreditState.CLAIM_COOLDOWN_MS
        s = ledger.claim(s)!!
        assertEquals(CreditState.MAX_BALANCE, s.balance)
        assertFalse(ledger.canClaim(s))
    }

    @Test
    fun `spend and refund`() {
        val s = CreditState(balance = 10)
        val spent = ledger.spend(s, 4)
        assertEquals(6, (spent as CreditLedger.SpendResult.Ok).newState.balance)
        val tooMuch = ledger.spend(s, 11)
        assertTrue(tooMuch is CreditLedger.SpendResult.Insufficient)
        assertEquals(10, ledger.refund(s, 0).balance)
    }
}
