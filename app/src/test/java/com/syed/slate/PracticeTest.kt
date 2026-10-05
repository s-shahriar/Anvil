package com.syed.slate

import com.syed.slate.practice.Command
import com.syed.slate.practice.Practice
import com.syed.slate.practice.PracticeData
import com.syed.slate.practice.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PracticeTest {
    // The data lives in Supabase; its authoring source is the sibling web project (tests that need it skip when absent).
    private val src = File(System.getProperty("user.home"), "Projects/Self/Quiz/ict-quiz/src/data/practice")
    private val categories by lazy {
        assumeTrue("ict-quiz checkout not found", src.isDirectory)
        PracticeData.parse(listOf("linux", "sql").map { org.json.JSONObject(File(src, "$it.json").readText()) })
    }

    @Test fun shellCommandsIgnoreSpacingAndTrailingSemicolonButNotCase() {
        assertTrue(Practice.checkAnswer("  cd   /etc ;", listOf("cd /etc")))
        assertFalse(Practice.checkAnswer("CD /etc", listOf("cd /etc")))
    }

    @Test fun sqlIgnoresCaseAndOperatorSpacing() {
        val accept = listOf("SELECT COUNT(*) FROM EmployeeSalary WHERE Project = 'P1';")
        assertTrue(Practice.checkAnswer("select count( * ) from employeesalary where project='P1'", accept, caseInsensitive = true))
        assertTrue(Practice.checkAnswer("SELECT mod(EmpId,2) FROM t", listOf("select MOD(EmpId, 2) from t;"), caseInsensitive = true))
        assertFalse(Practice.checkAnswer("SELECT * FROM t", accept, caseInsensitive = true))
        assertTrue(Practice.checkAnswer("a<=b", listOf("a <= b"), caseInsensitive = true))
    }

    @Test fun flagKeysMatchTheWebFormat() {
        assertEquals("practice__linux__files__ls -la", Practice.cmdId("linux", "files", "ls   -la;"))
        assertEquals("practice__sql__sel__select * from t where a = 1", Practice.cmdId("sql", "sel", "SELECT * FROM t WHERE a=1;"))
    }

    @Test fun commandListDropsCommandsAlreadyCoveredByADrillAndDuplicates() {
        val list = Practice.buildCommandList(
            listOf(Command("pwd", "where am i"), Command("pwd", "dup"), Command("ls", "list")),
            listOf(Problem("list files", listOf("ls"), emptyList(), "ls lists"), Problem("again", listOf("ls;"), emptyList(), null)),
        )
        assertEquals(listOf("pwd", "ls"), list.map { it.key })
        assertEquals("list files", list[1].prompt)
    }

    @Test fun realDataLoads() {
        assertEquals(listOf("linux", "sql"), categories.map { it.id })
        assertEquals(13, categories[0].topics.size); assertEquals(16, categories[1].topics.size)
        assertTrue(categories[1].sampleFor(categories[1].topics.first { it.set == "A" })!!.isNotEmpty())
    }

    @Test fun everyDrillAcceptsItsOwnAnswers() {
        for (c in categories) for (t in c.topics) for (p in t.practice) {
            assertTrue("${c.id}/${t.id}: ${p.prompt}", p.accept.isNotEmpty())
            p.accept.forEach { assertTrue("${c.id}/${t.id}: '$it' rejected", Practice.checkAnswer(it, p.accept, c.caseInsensitive)) }
            p.answers.forEach { assertTrue("${c.id}/${t.id}: shown answer '$it' would be marked wrong", Practice.checkAnswer(it, p.accept, c.caseInsensitive)) }
        }
    }
}
