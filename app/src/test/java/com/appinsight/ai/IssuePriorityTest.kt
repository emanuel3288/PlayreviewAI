package com.appinsight.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class IssuePriorityTest {
    @Test fun `una incidencia critica debe poder destacar sobre una de severidad baja`() {
        val criticalScore = priority(frequency = 47, severity = 3, growth = 34)
        val lowScore = priority(frequency = 47, severity = 1, growth = 0)
        assertTrue(criticalScore > lowScore)
    }

    private fun priority(frequency: Int, severity: Int, growth: Int) = frequency + severity * 25 + growth
}
