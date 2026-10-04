package com.syed.slate

import com.syed.slate.core.Uid
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fixtures are real rows from the two live Supabase projects. */
class UidTest {
    @Test fun generalQuizUidsMatchDatabase() {
        mapOf(
            "qggoewuwdy6" to "নিচের কোন বানানগুচ্ছটি সঠিক?",
            "qrymmr02sou" to "মুক্তিযুদ্ধ বিষয়ক কাব্যগ্রন্থ কোনটি?",
            "q96c2nozs6o" to "ইন্টারনেট অফ থিংস (IoT)-এর একটি বড় চ্যালেঞ্জ কী?",
            "qwkb6w44a0o" to "Pellucid এর অর্থ কি?",
            "q1hh39xs8a3e" to "A clock is started at noon. By 10 minutes past 5, the hour hand has turned through:",
            "qset5vnrefi" to "What is used as the cathode in a standard dry cell battery?",
            "qlq0iir6uf4" to "Md. Mostaqur Rahman is __ Governor of Bangladesh Bank.",
        ).forEach { (uid, text) -> assertEquals(text, uid, Uid.general(text)) }
    }

    @Test fun ictQuizUidsMatchDatabase() {
        listOf(
            Triple("mcq:28pj3dli166", "mcq", "A device used to display one or more digital signals so that they can be compared with expected timing diagrams for the signals is a:"),
            Triple("mcq:19qmkzu97y3", "mcq", "Black-box testing is also called"),
            Triple("mcq:1x4whjqv6n2", "mcq", "DNS works on which port?"),
            Triple("written:2b56gasctou", "written", "Explain the concepts of Inheritance and Polymorphism in Java. Write a Java program to demonstrate method overriding."),
            Triple("written:dkz1ewwtvk", "written", "KVM vs VMware — what is the difference, and which is better for what?"),
            Triple("written:2b4q2aa3lru", "written", "Explain the working principle of a PN junction diode. Draw its symbol and describe the difference between forward bias and reverse bias."),
            Triple("mcq:4tpeejbnsl", "mcq", "ইন্টারনেট নেটওয়ার্কে Media Access করার জন্য কোন পদ্ধতি ব্যবহৃত হয়?"),
            Triple("mcq:2egwb9p7vkc", "mcq", "Complete Binary tree height n তার মধ্যে node কতটি?"),
        ).forEach { (uid, module, text) -> assertEquals(text, uid, Uid.ict(module, text)) }
    }

    @Test fun emptyTextHasNoUid() {
        assertEquals(null, Uid.general("  ​ "))
        assertEquals(null, Uid.ict("mcq", null))
    }

    @Test fun normalizationIgnoresCaseWhitespaceAndZeroWidth() {
        assertEquals(Uid.general("DNS  works"), Uid.general("dns​ works"))
    }
}
